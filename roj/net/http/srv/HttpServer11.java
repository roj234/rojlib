package roj.net.http.srv;

import roj.NativeLibrary;
import roj.collect.MyHashMap;
import roj.collect.ObjectPool;
import roj.collect.RingBuffer;
import roj.io.IOUtil;
import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.net.ch.Event;
import roj.net.ch.handler.PacketMerger;
import roj.net.ch.handler.StreamCompress;
import roj.net.ch.osi.ServerLaunch;
import roj.net.http.*;
import roj.net.http.srv.error.GreatErrorPage;
import roj.text.ACalendar;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;
import roj.util.NamespaceKey;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.util.List;
import java.util.TimeZone;
import java.util.function.BiConsumer;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

import static roj.net.http.HttpClient11.setChunk;

public final class HttpServer11 extends PacketMerger implements
								ChannelHandler,
								PostSetting, BiConsumer<String, String>,
								ResponseHeader, ResponseWriter {
	public static final String SERVER_NAME = "nginx/2.3.3";

	private static final int KEEPALIVE_MAX = 32;
	private static final int MAX_REQEUST_CACHE = 10;
	private static final int COMPRESS_LEVEL = Deflater.DEFAULT_COMPRESSION;

	public static final class Local {
		private static final ACalendar RFC_DATE = new ACalendar(TimeZone.getTimeZone("GMT"));

		public final ACalendar date = RFC_DATE.copy();
		public final MyHashMap<String, Object> ctx = new MyHashMap<>();

		final RingBuffer<HttpServer11> hanging = new RingBuffer<>(KEEPALIVE_MAX);

		final ObjectPool<Request> requests = new ObjectPool<>(() -> {
			Request req = new Request();
			req.threadCtx = ctx;
			return req;
		}, 10);

		Local() {}

		public String toRFC(long time) { return date.toRFCString(time); }
	}
	public static final ThreadLocal<Local> TSO = ThreadLocal.withInitial(Local::new);

	public static ServerLaunch simple(InetSocketAddress addr, int backlog, Router router) throws IOException {
		return ServerLaunch.tcp().threadPrefix("HTTP服务器").threadMax(4)
						   .listen_(addr, backlog)
						   .option(StandardSocketOptions.SO_REUSEADDR, true)
						   .initializator((ctx) -> ctx.addLast("h11@server", create(router)));
	}

	// state
	private static final byte UNOPENED = 0, RECV_HEAD = 1, RECV_BODY = 2, PROCESSING = 3, SEND_HEAD = 4, SEND_BODY = 5, SEND_DONE = 6, HANG_PRE = 7, HANG = 8, CLOSED = 9;
	// flag
	private static final byte KEPT_ALIVE = 1, CHUNK = 2, GZIP_MODE = 4, UNCAUGHT_ERROR = 8, WANT_COMPRESS = 16, EXT_POST_BUFFER = 32;

	private byte state, flag;
	private long time;

	private final Router router;
	public ChannelCtx ch;

	private Request req;

	private HFinishHandler fh;
	private HPostHandler ph;

	private ByteList postBuffer;

	private int code;
	private Response body;

	private HttpServer11(Router router) { this.router = router; }
	public static HttpServer11 create(Router r) { return new HttpServer11(r); }

	@Override
	public void channelOpened(ChannelCtx ctx) throws IOException {
		state = HANG;
		time = System.currentTimeMillis() + 1000;
	}

	@Override
	@SuppressWarnings("fallthrough")
	public void channelTick(ChannelCtx ctx) throws IOException {
		seg:
		switch (state) {
			case UNOPENED: return;
			case SEND_BODY:
				if (ctx.isPendingSend()) break;

				long t = System.currentTimeMillis();
				int spin = 5;
				while (body.send(this)) {
					if (--spin == 0 || System.currentTimeMillis() != t) break seg;
					if (ctx.isPendingSend()) {
						ctx.pauseAndFlush();
						break seg;
					}
				}
			case SEND_DONE:
				outEof();

				if (fh != null && fh.onRequestFinish(this)) {
					state = CLOSED;
					ch.readActive();
					ch.removeSelf();
					return;
				}

				// 500ms for close-notify or hang
				time = System.currentTimeMillis() + 500;

				if ("close".equalsIgnoreCase(req.responseHeader.get("connection"))) {
					ch.channel().closeGracefully();
					state = CLOSED;
				} else {
					state = HANG_PRE;
					flag |= KEPT_ALIVE;

					ch.readActive();

					finish(false);
				}
				break;
		}

		if (System.currentTimeMillis() > time || !ctx.isInputOpen()) {
			switch (state) {
				case RECV_HEAD:
				case RECV_BODY:
				case PROCESSING:
					if (!ctx.isOutputOpen() || req == null) {
						ctx.channel().closeGracefully();
						time = System.currentTimeMillis() + 500;
						state = CLOSED;
						return;
					}

					time = System.currentTimeMillis() + 500;
					code(state == PROCESSING ? 504 : 408).die();
					body = StringResponse.httpErr(state == PROCESSING ? 504 : 408);
					sendHead();
					break;
				case HANG_PRE:
					if (ctx.isInputOpen()) {
						time = System.currentTimeMillis() + router.keepaliveTimeout();

						RingBuffer<HttpServer11> rb = TSO.get().hanging;
						HttpServer11 before = rb.ringAddLast(this);
						if (before != null) before.ch.close();
						state = HANG;
						break;
					}
				case HANG:
				case SEND_HEAD: case SEND_BODY:
				case CLOSED: default:
					ctx.close();
					break;
			}
		}
	}

	// region Receive head / body

	private int headerLen;
	private long exceptPostSize, postSize;

	@Override
	@SuppressWarnings("fallthrough")
	public void channelRead(ChannelCtx ctx, Object o) throws IOException {
		DynByteBuf data = (DynByteBuf) o;
		switch (state) {
			case HANG: TSO.get().hanging.remove(this);
			case HANG_PRE:
				time = System.currentTimeMillis() + router.readTimeout();
				state = RECV_HEAD;
			case RECV_HEAD:
				// first line
				if (req == null) {
					headerLen = router.maxHeaderSize();

					String method, path, version, query;

					success: {
						String line = HttpHead.readCRLF(data);
						if (line == null) return;
						if (line.length() > 8192) throw new IllegalRequestException(Code.URI_TOO_LONG);

						failed: {
							int i = line.indexOf(' ');
							if (i < 0) break failed;
							method = line.substring(0, i);
							int j = line.indexOf(' ', i+1);
							if (j < 0) break failed;
							int k = line.indexOf('?', i+1);
							if (k < 0) {
								path = line.substring(i+1, j);
								query = "";
							} else {
								path = line.substring(i+1, k);
								query = line.substring(k+1, j);
							}
							version = line.substring(j+1);
							if (version.startsWith("HTTP/")) break success;
						}
						throw new IllegalRequestException(Code.BAD_REQUEST, "无效请求头 " + line);
					}

					int act = Action.valueOf(method);
					if (act < 0) throw new IllegalRequestException(Code.METHOD_NOT_ALLOWED, "无效请求类型 " + method);

					Local local = TSO.get();
					req = local.requests.get().init(act, path, query);
					req.handler = this;
				}

				// headers
				Headers h = req;
				try {
					int avail = data.readableBytes();
					boolean finish = h.parseHead(data, IOUtil.getSharedByteBuf());

					if((headerLen -= avail-data.readableBytes()) < 0) throw new IllegalArgumentException("header too large");

					if (!finish) return;
				} catch (IllegalArgumentException e) {
					throw new IllegalRequestException(Code.BAD_REQUEST, e.getMessage());
				}

				validateHeader(h);

				String lenStr = req.get("content-length");
				String encoding = req.get("transfer-encoding");
				if (lenStr == null && encoding == null) {
					exceptPostSize = -2;

					if (checkHeader(ctx, null)) return;
					process();
					return;
				}

				exceptPostSize = lenStr != null ? Long.parseLong(lenStr) : -1;
				postSize = Router.DEFAULT_POST_SIZE;

				if (checkHeader(ctx, this)) return;

				boolean chunk = "chunked".equalsIgnoreCase(encoding);

				if (lenStr != null) {
					if (exceptPostSize > postSize) throw new IllegalRequestException(Code.ENTITY_TOO_LARGE);
					preparePostBuffer(ctx, postSize = exceptPostSize);
				} else if (chunk) {
					preparePostBuffer(ctx, postSize);
				} else {
					throw new IllegalRequestException(Code.BAD_REQUEST, "不支持的传输编码"+encoding);
				}

				state = RECV_BODY;

				if (chunk) {
					ctx = setChunk(ctx, -1);
					ctx.handler().channelRead(ctx, o);
					return;
				}
			case RECV_BODY:
				long remain = postSize-data.readableBytes();
				_if:
				if (remain <= 0) {
					if (!req.containsKey("content-length")) {
						if (remain == 0) break _if;
						die().code(Code.ENTITY_TOO_LARGE);
					} else {
						// 两个请求连在一起？
						ctx.readInactive();

						int w = data.wIndex();
						data.wIndex(data.rIndex+(int)postSize);

						if (ph != null) mergedRead(ctx, data);
						else if (postBuffer != null) postBuffer.put(data);

						data.rIndex = data.wIndex();
						data.wIndex(w);

						req.postFields = ph == null ? postBuffer : ph;
						process();
					}
					return;
				}

				postSize -= data.readableBytes();
				if (ph != null) {
					mergedRead(ctx, data);
				} else if (postBuffer != null) {
					postBuffer.put(data);
				}
				data.clear();
				break;
			case PROCESSING: throw new IOException("Unexpected PROCESSING");
			default: ch.readInactive();
		}
	}
	private void preparePostBuffer(ChannelCtx ctx, long len) {
		// post accept
		if (ph != null || state != RECV_BODY) return;

		if (len > 8388608) throw new IllegalArgumentException("必须使用PostHandler");

		if (len <= 65535) {
			postBuffer = (ByteList) ctx.alloc().buffer(false, (int) len);
			flag |= EXT_POST_BUFFER;
		} else {
			// if "USE_CACHE" is on
			if (postBuffer == null) postBuffer = new ByteList();
			postBuffer.clear();
		}
	}

	public long postExceptLength() { return exceptPostSize; }
	public void postAccept(long len, int t) {
		if (state == RECV_HEAD) state = RECV_BODY;
		else throw new IllegalStateException();
		postSize = len;
		time += t;
	}
	public boolean postAccepted() { return state == RECV_BODY; }

	private static void validateHeader(Headers h) throws IllegalRequestException {
		int c = h.getCount("content-length");
		if (c > 1) {
			List<String> list = h.getAll("content-length");
			// noinspection all
			for (int i = 0; i < list.size(); i++) {
				if (!list.get(i).equals(list.get(0))) throw new IllegalRequestException(400);
			}
		}
		if (c > 0 && h.containsKey("transfer-encoding")) throw new IllegalRequestException(400);
	}

	@Override
	public void onEvent(ChannelCtx ctx, Event event) throws IOException {
		NamespaceKey id = event.id;
		if (id.equals(ChunkSplitter.CHUNK_IN_EOF)) {
			if (state != RECV_BODY) return;

			req.postFields = ph == null ? postBuffer : ph;
			process();
			event.setResult(Event.RESULT_ACCEPT);
		}
	}

	// endregion

	@Override
	public void exceptionCaught(ChannelCtx ctx, Throwable ex) throws Exception {
		if (ex instanceof SSLException) {
			System.out.println("SSLException: " + ex.getMessage());
			ctx.close();
			return;
		}

		boolean alreadyError = (flag&UNCAUGHT_ERROR) != 0;
		flag |= UNCAUGHT_ERROR;
		if (req != null && state <= SEND_HEAD) {
			try {
				req.responseHeader.clear();

				if (body != null) {
					try {
						body.release(ch);
					} catch (Exception ignored) {}
					body = null;
				}

				onError(ex);
				sendHead();
				return;
			} catch (Throwable ignored) {}
		}

		ctx.exceptionCaught(ex);
	}

	private boolean checkHeader(ChannelCtx ctx, PostSetting cfg) throws IOException {
		try {
			router.checkHeader(req, cfg);
			ctx.channelOpened();
		} catch (Throwable e) {
			onError(e);
			sendHead();
			return true;
		}
		return false;
	}
	private void process() throws IOException {
		exceptPostSize = -2;
		state = PROCESSING;
		try {
			if (ph != null) {
				mergedRead(ch, ByteList.EMPTY);
				ph.onSuccess();
			}
			Response resp = router.response(req, this);
			if (body == null) body = resp;
			else assert resp == null;
		} catch (Throwable e) {
			onError(e);
		}
		sendHead();
	}
	private void onError(Throwable e) {
		if (exceptPostSize != -2) {
			req.responseHeader.put("connection", "close");
		}

		if (e instanceof IllegalRequestException) {
			IllegalRequestException ire = (IllegalRequestException) e;

			code = ire.code;
			body = ire.response == null ? StringResponse.httpErr(ire.code) : ire.response;
		} else {
			code = 500;
			body = makeErrorResponse(e);

			e.printStackTrace();
		}
	}

	private Response makeErrorResponse(Throwable e) {
		if (NativeLibrary.IN_DEV) return GreatErrorPage.display(req, e);
		return StringResponse.httpErr(500);
	}

	// region Write head

	@Override
	public void channelWrite(ChannelCtx ctx, Object data) throws IOException {
		if (data instanceof Response) {
			body = (Response) data;
			sendHead();
		} else {
			ctx.channelWrite(data);
		}
	}

	private void sendHead() throws IOException {
		if (code == 0) code = 200;
		if (body == Response.EMPTY) body = null; // fast path

		ByteList hdr = IOUtil.ddLayeredByteBuf();
		hdr.putAscii("HTTP/1.1 ").putAscii(Integer.toString(code)).put((byte) ' ').putAscii(Code.getDescription(code)).putAscii("\r\n");

		req.packToHeader();
		Headers h = req.responseHeader;

		if ("close".equalsIgnoreCase(req.getField("connection"))) h.put("connection", "close");
		else if ((flag & KEPT_ALIVE) == 0) {
			if (SERVER_NAME != null) h.putIfAbsent("server", SERVER_NAME);
			h.putIfAbsent("connection", "keep-alive");
		}

		if (body == null) h.put("content-length", "0");
		else body.prepare(this, h);

		state = SEND_HEAD;
		_enc = ENC_PLAIN;

		if (req != null && req.containsKey("accept-encoding") &&
			body != null &&
			(flag&WANT_COMPRESS) != 0 && !h.containsKey("content-encoding")) {

			_maxQ = 0;
			Headers.complexValue(req.get("accept-encoding"), this, false);
		}

		int enc = _enc;
		boolean chunk = enc != ENC_PLAIN || (flag & CHUNK) != 0;
		ChannelCtx out = chunk ? setChunk(ch, 1) : ch;
		setCompr(ch, enc, hdr);

		if (chunk) {
			h.remove("content-length");
			h.put("transfer-encoding", "chunked");
		}
		h.encode(hdr);

		out.channelWrite(hdr.putShort(0x0D0A));

		if (enc == ENC_GZIP) {
			hdr.clear();
			hdr.putShort(0x1f8b).putLong((long) Deflater.DEFLATED << 56);
			out.handler().channelWrite(out, hdr);
		}
		hdr.close();

		time = System.currentTimeMillis() + router.writeTimeout(req, body);
		state = body == null ? SEND_DONE : SEND_BODY;
		ch.readInactive();
	}

	// endregion

	@Override
	public void channelClosed(ChannelCtx ctx) throws IOException {
		finish(true);
	}

	private void finish(boolean close) {
		Local t = TSO.get();

		if (state == HANG) t.hanging.remove(this);

		if (req != null) {
			req.free();
			t.requests.reserve(req);
			req = null;
		}

		ByteList pb = postBuffer;
		postBuffer = null;
		if (pb != null) {
			if ((flag & EXT_POST_BUFFER) != 0) {
				try {
					pb.close();
				} catch (IOException ignored) {}
			} else if (close) {
				pb._free();
			}
		}

		code = 0;
		flag &= (KEPT_ALIVE | GZIP_MODE);

		if (def != null && close) def.end();

		setChunk(ch, 0);
		setCompr(ch, ENC_PLAIN, null);

		Exception e = null;
		if (body != null) {
			try {
				body.release(ch);
			} catch (Exception e1) {
				e = e1;
			}
			body = null;
		}
		if (ph != null) {
			ch.channel().remove("h11@body_handler");
			try {
				ph.onComplete();
			} catch (Exception e1) {
				if (e == null) e = e1;
				else e.addSuppressed(e1);
			}
			ph = null;
		}
		try {
			super.channelClosed(ch);
		} catch (Exception e1) {
			if (e == null) e = e1;
			else e.addSuppressed(e1);
		}
		if (e != null) Helpers.athrow(e);
	}

	public void finishHandler(HFinishHandler o) { fh = o; }
	public void postHandler(HPostHandler o) { ph = o; ch.channel().addAfter(ch, "h11@body_handler", o); }

	public boolean hasError() {
		return (flag & UNCAUGHT_ERROR) != 0;
	}

	// region ResponseHeader

	@Override
	public ResponseHeader code(int code) {
		this.code = code;
		return this;
	}

	@Override
	public ResponseHeader die() { req.responseHeader.put("connection", "close"); return this; }

	public ResponseHeader chunked() { flag |= CHUNK; return this; }
	public ResponseHeader compressed() { flag |= WANT_COMPRESS; return this; }
	public ResponseHeader uncompressed() { flag &= ~WANT_COMPRESS; return this; }

	public ResponseHeader body(Response resp) { this.body = resp; return this; }

	@Override
	public ResponseHeader date() {
		req.responseHeader.put("date", TSO.get().toRFC(System.currentTimeMillis()));
		return this;
	}

	@Override
	public ResponseHeader header(String k, String v) {
		req.responseHeader.put(k, v);
		return this;
	}

	@Override
	public ResponseHeader headers(String hdr) {
		req.responseHeader.putAllS(hdr);
		return this;
	}

	@Override
	public Headers headers() { return req.responseHeader; }

	@Override
	public ChannelCtx ch() {
		return ch;
	}

	// endregion
	// region ResponseWriter

	public int write(DynByteBuf buf) throws IOException {
		if (state != SEND_BODY) throw new IllegalStateException();

		int len = buf.readableBytes();
		if ((flag & GZIP_MODE) != 0) {
			if (buf.hasArray()) {
				crc.update(buf.array(), buf.arrayOffset() + buf.rIndex, len);
			} else {
				crc.update(buf.nioBuffer());
			}
		}
		if (len > 0) ch.channelWrite(buf);
		return len - buf.readableBytes();
	}
	public int write(InputStream in, int limit) throws IOException {
		if (state != SEND_BODY) throw new IllegalStateException();

		ByteList buf = (ByteList) ch.allocate(false, 4000);
		try {
			int v = buf.readStream(in, limit <= 0 ? buf.capacity() : Math.min(buf.capacity(), limit));
			if (v <= 0) return v;
			ch.channelWrite(buf);
			if ((flag & GZIP_MODE) != 0) {
				crc.update(buf.array(), buf.arrayOffset(), buf.wIndex());
			}
			return v;
		} finally {
			ch.reserve(buf);
		}
	}

	private void outEof() throws IOException {
		if (!ch.isOutputOpen()) return;

		if (def != null) ch.postEvent(StreamCompress.OUT_EOF);
		if ((flag & GZIP_MODE) != 0) {
			DynByteBuf buf = ch.allocate(false, 8);
			buf.putIntLE((int) crc.getValue()).putIntLE(def.getTotalIn());
			try {
				ChannelCtx ctx = ch.channel().handler("h11@chunk");
				// noinspection all
				ctx.handler().channelWrite(ctx, buf);
			} finally {
				ch.reserve(buf);
			}
		}
		ch.postEvent(ChunkSplitter.CHUNK_OUT_EOF);
	}

	// endregion
	// region Compress / Chunk

	@Override
	public void handlerAdded(ChannelCtx ctx) {
		ch = ctx;
	}

	@Override
	public void handlerRemoved(ChannelCtx ctx) {
		ch = null;
		ctx.channel().remove("h11@compr");
		ctx.channel().remove("h11@chunk");
	}

	private static final int ENC_PLAIN = 0, ENC_DEFLATE = 1, ENC_GZIP = 2;

	private static int isTypeSupported(String type) {
		switch (type) {
			case "*":
			case "deflate":
				return ENC_DEFLATE;
			case "gzip":
				return ENC_GZIP;
			//case "br":
			default:
				return -1;
		}
	}

	private CRC32 crc;
	private Deflater def;

	private void setCompr(ChannelCtx ctx, int enc, ByteList hdr) {
		StreamCompress sc;

		ChannelCtx hwarp = ctx.channel().handler("h11@compr");
		if (hwarp == null) {
			if (enc == ENC_PLAIN) return;
			ctx.channel().addBefore(ctx, "h11@compr", sc = new StreamCompress(1024));
		} else {
			sc = (StreamCompress) hwarp.handler();
		}

		if (enc != ENC_PLAIN) {
			switch (enc) {
				case ENC_GZIP:
					if ((flag & GZIP_MODE) == 0) {
						if (def != null) def.end();
						def = new Deflater(COMPRESS_LEVEL, true);
					} else {
						def.reset();
					}
					flag |= GZIP_MODE;

					if (crc == null) crc = new CRC32();
					else crc.reset();

					hdr.putAscii("content-encoding: gzip\r\n");
					break;
				case ENC_DEFLATE:
					if ((flag & GZIP_MODE) != 0 || def == null) {
						if (def != null) def.end();
						def = new Deflater(COMPRESS_LEVEL, false);
					} else {
						def.reset();
					}
					flag &= ~GZIP_MODE;

					hdr.putAscii("content-encoding: deflate\r\n");
					break;
			}
			sc.setDef(def);
		} else sc.setDef(null);
	}

	private float _maxQ;
	private byte _enc;
	@Override
	public void accept(String k, String v) {
		int sup = isTypeSupported(k);
		if (sup >= 0) {
			float Q = 1;
			if (!v.isEmpty()) {
				try {
					Q = Float.parseFloat(v);
				} catch (NumberFormatException e) {
					Q = 0;
				}
			}

			if (Q > _maxQ) {
				_enc = (byte) sup;
				_maxQ = Q;
			}
		}
	}

	// endregion
}
