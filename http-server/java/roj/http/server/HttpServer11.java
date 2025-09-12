package roj.http.server;

import roj.ci.annotation.ReferenceByGeneratedClass;
import roj.http.*;
import roj.io.BufferPool;
import roj.io.IOUtil;
import roj.net.ChannelCtx;
import roj.net.Event;
import roj.net.MyChannel;
import roj.net.handler.PacketMerger;
import roj.net.util.SpeedLimiter;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.text.Tokenizer;
import roj.text.logging.Level;
import roj.text.logging.Logger;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.FastFailException;
import roj.util.Helpers;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.List;
import java.util.zip.Deflater;

final class HttpServer11 extends PacketMerger implements PostSetting, ResponseHeader, ContentWriter {
	public static final Logger LOGGER = Logger.getLogger("IIS");

	//region 使用Nixim注入的函数
	@ReferenceByGeneratedClass
	private void accessLog() {
		var sb = IOUtil.getSharedCharBuf();
		req.firstLine(sb.append(((InetSocketAddress) ch.remoteAddress()).getHostString()).append(" \""))
		  .append("\" ")
		  .append(code).append(' ').append(receivedBytes).append(' ').append(sendBytes)
		  .append(" \"")
		  .append(Tokenizer.escape(req.header("user-agent")))
		  .append("\"[");
		if (ch.channel().handler(hDeflate.ANCHOR) != null) sb.append('Z');
		if (state == HANG_PRE) sb.append('A');
		LOGGER.trace(sb.append(']').toString());

		receivedBytes = sendBytes = 0;
	}
	//endregion

	// state
	private static final byte UNOPENED = 0, RECV_HEAD = 1, RECV_BODY = 2, PROCESSING = 3, SEND_BODY = 4, HANG_PRE = 5, HANG = 6, CLOSED = 7;
	// flag
	static final byte KEPT_ALIVE = 1, FLAG_ERRORED = 2, FLAG_COMPRESS = 8, FLAG_GZIP = 16, FLAG_ASYNC = 32;

	private byte state, flag;
	private long time;

	private final Router router;
	ChannelCtx ch;

	private Request req;

	private RequestFinishHandler fh;
	private BodyParser ph;

	private ByteList postBuffer;

	private short code;
	private Content body;

	//仅用于生成访问日志
	private long receivedBytes, sendBytes;
	@Override public long getBytesSent() {return sendBytes;}

	HttpServer11(Router router) {this.router = router;}

	@Override
	public void channelOpened(ChannelCtx ctx) {
		state = RECV_HEAD;
		time = System.currentTimeMillis() + router.readTimeout(null);
	}

	@Override
	@SuppressWarnings("fallthrough")
	public void channelTick(ChannelCtx ctx) throws IOException {
		if (state == UNOPENED) return;

		if ((flag&SEND_BODY) != 0 && !ctx.isFlushing()) {
			boolean hasMore = body.send(this);
			if (ctx.isFlushing()) ctx.pauseAndFlush();
			else if (!hasMore) outEof();
		}

		if (System.currentTimeMillis() > time || !ctx.isInputOpen()) {
			switch (state) {
				case RECV_HEAD, RECV_BODY, PROCESSING:
					if (!ctx.isOutputOpen() || req == null) {
						ctx.channel().closeGracefully();
						time = System.currentTimeMillis() + 500;
						state = CLOSED;
						return;
					}

					time += 100;
					if (state != PROCESSING) body = Content.httpError(code = HttpUtil.TIMEOUT);
					else if (fh == null || !fh.onResponseTimeout(this)) {
						code = 504;
						body = Content.internalError("异步处理超时\n在规定的时间内未收到响应头，请求代理失败");
					}
					die();
					sendHead();
					flag |= FLAG_ERRORED;
				break;
				case SEND_BODY: LOGGER.warn("发送超时[在规定的时间内未能将响应体全部发送]: {}", body); break;
				case HANG_PRE:
					if (ctx.isInputOpen()) {
						time = System.currentTimeMillis() + router.keepaliveTimeout();

						var prev = HttpServer.getInstance().keepalive.ringAddLast(this);
						if (prev != null) prev.ch.close();
						state = HANG;
						break;
					}
				default:
				case HANG, CLOSED: ctx.close(); break;
			}
		}
	}

	//region Receive head / body
	private int headerLen;
	private long exceptPostSize, postSize;

	@Override
	@SuppressWarnings("fallthrough")
	public void channelRead(ChannelCtx ctx, Object o) throws IOException {
		DynByteBuf buf = (DynByteBuf) o;
		switch (state) {
			case HANG: HttpServer.getInstance().keepalive.remove(this);
			case HANG_PRE:
				time = System.currentTimeMillis() + router.readTimeout(null);
				state = RECV_HEAD;
			case RECV_HEAD: {
				// first line
				if (req == null) {
					headerLen = router.maxHeaderSize();

					String method, path, version, query;

					success: {
						String line = HttpHead.readCRLF(buf);
						if (line == null) return;
						if (line.length() > 8192) throw new IllegalRequestException(HttpUtil.URI_TOO_LONG);

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
						throw IllegalRequestException.badRequest("无效请求头 "+line);
					}

					byte act = HttpUtil.getMethodId(method);
					if (act < 0) throw IllegalRequestException.badRequest("无效请求类型 "+method);

					req = HttpServer.getInstance().request().init(act, path, query, version);
					req.server = this;
				}

				// headers
				Headers h = req;
				try {
					int avail = buf.readableBytes();
					boolean finish = Headers.parseHeader(h, buf, IOUtil.getSharedByteBuf());

					if((headerLen -= avail-buf.readableBytes()) < 0) throw new IllegalRequestException(431);

					if (!finish) return;
				} catch (IllegalArgumentException e) {
					throw IllegalRequestException.badRequest(e.getMessage());
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

				if (lenStr != null) {
					if (TextUtil.isNumber(lenStr) != 0) throw IllegalRequestException.badRequest("content-length非法");
					exceptPostSize = Long.parseLong(lenStr);
				}
				else exceptPostSize = -1;
				postSize = -1;

				if (checkHeader(ctx, this)) return;
				time = System.currentTimeMillis() + router.readTimeout(req);

				boolean chunk = "chunked".equalsIgnoreCase(encoding);

				if (lenStr != null) {
					if (exceptPostSize > postSize) throw new IllegalRequestException(req.isExpecting() ? 417 : HttpUtil.ENTITY_TOO_LARGE);
					preparePostBuffer(ctx, postSize = exceptPostSize);
				} else if (chunk) {
					// 不知道请求体长度，准备一个最大长度的缓冲区
					preparePostBuffer(ctx, postSize);
				} else {
					throw req.isExpecting() ? new IllegalRequestException(417) : IllegalRequestException.badRequest("不支持的传输编码 "+encoding);
				}

				if (req.isExpecting()) {
					try {
						ByteList hdr = IOUtil.getSharedByteBuf();
						ch.channelWrite(hdr.putAscii("HTTP/1.1 100 Continue\r\n\r\n"));
					} catch (IOException e) {
						Helpers.athrow(e);
					}
				}

				state = RECV_BODY;

				// 只有提供BodyParser时才自动解压缩
				ctx = hCE.apply(ctx, req, ph == null, postSize);
				ctx = hTE.apply(ctx, req, -1);

				ctx.handler().channelRead(ctx, o);
				return;
			}
			case RECV_BODY:
				receivedBytes += buf.readableBytes();
				if (ph != null) {
					mergedRead(ctx, buf);
				} else if (postBuffer != null) {
					postBuffer.put(buf);
				}
				buf.rIndex = buf.wIndex();
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
			assert postBuffer == null;
			postBuffer = (ByteList) ctx.allocate(false, (int) len);
		} else {
			// if "USE_CACHE" is on
			if (postBuffer == null) postBuffer = new ByteList();
			postBuffer.clear();
		}
	}

	public long postExceptLength() {return exceptPostSize;}
	public void postAccept(long len, int t) {
		if (state == RECV_HEAD) state = RECV_BODY;
		else throw new IllegalStateException();
		postSize = len;
		time += t;
	}
	public boolean postAccepted() {return state == RECV_BODY;}
	public void postHandler(BodyParser o) {ph = o; ch.channel().addAfter(ch, "http:body_parser", o);}

	private static void validateHeader(Headers h) throws IllegalRequestException {
		int c = h.getCount("content-length");
		if (c > 1) {
			List<String> list = h.getAll("content-length");
			// noinspection all
			for (int i = 0; i < list.size(); i++) {
				if (!list.get(i).equals(list.get(0))) throw IllegalRequestException.badRequest("content-length头部长度不统一");
			}
		}
		//if (h.getCount("host") != 1) throw IllegalRequestException.badRequest("host数量不为一");
		if (c > 0 && h.containsKey("transfer-encoding")) throw IllegalRequestException.badRequest("已知长度时使用transfer-encoding头部");
	}

	@Override
	public void onEvent(ChannelCtx ctx, Event event) throws IOException {
		String id = event.id;
		switch (id) {
			case MyChannel.IN_EOF, hTE.IN_END -> {
				if (state == RECV_BODY) {
					processWithBody();
				} else if (state < RECV_BODY || state > SEND_BODY) {
					assert id.equals(MyChannel.IN_EOF); // close
					return;
				}
				event.setResult(Event.RESULT_ACCEPT);
			}
			case hCE.IN_END -> processWithBody();
			case hCE.TOO_MANY_DATA -> die().code(HttpUtil.ENTITY_TOO_LARGE);
		}
	}
	//endregion
	//region Handle request & error
	@Override
	public void exceptionCaught(ChannelCtx ctx, Throwable ex) throws Exception {
		boolean hasError = (flag&FLAG_ERRORED) != 0;
		flag |= FLAG_ERRORED;
		if (!hasError && req != null && state <= PROCESSING) {
			try {
				req.responseHeader.clear();

				if (body != null) {
					try {
						body.release(ch);
					} catch (Exception ignored) {}
					body = null;
				}

				onError(ex);
				return;
			} catch (Throwable ignored) {}
		}

		if (ex.getClass() == IOException.class || ex instanceof SocketException) {
			LOGGER.debug(ex.getClass().getSimpleName()+": "+ex.getMessage());
			ctx.close();
			return;
		}

		LOGGER.warn("未捕获的异常", ex);
		ctx.close();
	}

	private boolean checkHeader(ChannelCtx ctx, PostSetting cfg) throws IOException {
		try {
			router.checkHeader(req, cfg);
			ctx.channelOpened();
		} catch (Throwable e) {
			onError(e);
			return true;
		}
		return false;
	}
	private void processWithBody() throws IOException {
		req.bodyData = ph == null ? postBuffer : ph;
		process();
	}
	private void process() throws IOException {
		// 两个请求可能连在一起
		ch.readInactive();
		exceptPostSize = -2;
		state = PROCESSING;
		try {
			if (ph != null) {
				DynByteBuf buf = merged;
				ph.onSuccess(buf == null ? ByteList.EMPTY : buf);
				if (buf != null) buf.release();
				merged = null;
			}
			if ((flag&SEND_BODY) != 0) return;

			var resp = router.response(req, this);
			if (body == null) body = resp;
			else if (resp != null) throw new FastFailException("已调用body()设置请求体,response必须返回null");
		} catch (Exception e) {
			onError(e);
			return;
		}

		// handlerRemoved() in response()
		if (ch != null && ((flag & FLAG_ASYNC) == 0 || body != null)) {
			time += System.currentTimeMillis() + router.writeTimeout(req, body);
			sendHead();
		}
	}
	private void onError(Throwable e) throws IOException {
		if (exceptPostSize != -2) {
			req.responseHeader.put("connection", "close");
		}

		if (e instanceof IllegalRequestException ire) {
			if (ire.code != 0) code = (short) ire.code;
			body = ire.createResponse();
		} else {
			code = 500;
			body = HttpServer.onUncaughtError(req, e);

			if (LOGGER.canLog(Level.WARN))
				LOGGER.warn(req.firstLine(new CharList("处理请求")).append("时发生异常").toStringAndFree(), e);
		}

		sendHead();
	}
	//endregion
	//region Write head
	private void sendHead() throws IOException {
		if (code == 0) code = 200;
		if (body == Content.EMPTY) body = null; // fast path

		req.packToHeader();
		Headers h = req.responseHeader;

		if ("close".equalsIgnoreCase(req.header("connection"))) h.put("connection", "close");
		else if ((flag & KEPT_ALIVE) == 0) {
			h.put("server", HttpServer.SERVER_NAME);
			h.putIfAbsent("connection", "keep-alive");
			//h.putIfAbsent("keep-alive", "timeout="+router.keepaliveTimeout()/1000);
		}

		int enc = HttpServer.ENC_PLAIN;
		if (body == null) h.put("content-length", "0");
		else {
			body.prepare(this, h);
			if ((flag & FLAG_COMPRESS) != 0 && req.containsKey("accept-encoding") && !h.containsKey("content-encoding") && !h.header("content-length").equals("0")) {
				enc = HttpServer.getInstance().parseAcceptEncoding(req.get("accept-encoding"));
			}
		}

		var hdr = new ByteList().putAscii("HTTP/1.1 ").putAscii(Integer.toString(code)).put(' ').putAscii(HttpUtil.getCodeDescription(code)).putAscii("\r\n");

		boolean chunk = enc != HttpServer.ENC_PLAIN || (!"close".equals(h.get("connection")) && !h.containsKey("content-length"));
		ChannelCtx out = chunk ? hTE.apply(ch, null, 1) : ch;
		deflate(ch, enc, hdr);

		if (chunk) {
			h.remove("content-length");
			h.put("transfer-encoding", "chunked");
		}
		h.encode(hdr);

		out.channelWrite(hdr.putShort(0x0D0A));

		//time added
		if (body == null) outEof();
		else {
			state = SEND_BODY;
			flag |= SEND_BODY;
		}

		if (enc == HttpServer.ENC_GZIP) {
			hdr.clear();
			hdr.putShort(0x1f8b).putLong((long) Deflater.DEFLATED << 56);
			out.handler().channelWrite(out, hdr);
		}
		hdr.release();
	}
	//endregion
	//region ResponseHeader
	@Override public MyChannel connection() {return ch.channel();}
	@Override public String _getState() {
		return switch (state) {
			case UNOPENED -> "UNOPENED";
			case RECV_HEAD -> "RECV_HEAD";
			case RECV_BODY -> "RECV_BODY";
			case PROCESSING -> "PROCESSING";
			case SEND_BODY -> "SEND_BODY";
			case HANG_PRE -> "HANG_PRE";
			case HANG -> "HANG";
			case CLOSED -> "CLOSED";
			default -> "Unknown state#"+state;
		};
	}
	@Override public Request request() {return req;}

	@Override public ResponseHeader code(int code) {this.code = (short) code;return this;}
	@Override public ResponseHeader die() {req.responseHeader.put("connection", "close");return this;}
	@Override public ResponseHeader enableAsyncResponse(int extraTimeMs) {flag |= FLAG_ASYNC;time = System.currentTimeMillis()+extraTimeMs;return this;}
	@Override public void body(Content resp) {
		if ((flag & FLAG_ASYNC) == 0) {
			if (state != RECV_HEAD) {
				this.body = resp;
				return;
			}

			flag |= SEND_BODY;
			LOGGER.warn("全双工模式正开发中");
		} else if (state != PROCESSING) {
			throw new IllegalStateException("Expect PROCESSING: "+_getState());
		}

		var lock = ch.channel().lock();
		lock.lock();
		try {
			body = resp;
			time = System.currentTimeMillis() + router.writeTimeout(req, body);
			sendHead();
		} catch (Exception e) {
			try {
				exceptionCaught(ch, e);
			} catch (Exception ex) {
				Helpers.athrow(ex);
			}
			e.printStackTrace();
		} finally {
			lock.unlock();
		}
	}

	@Override public ResponseHeader enableCompression() {flag |= FLAG_COMPRESS; return this;}
	@Override public ResponseHeader disableCompression() {flag &= ~FLAG_COMPRESS; return this;}

	@Override public Headers headers() {return req.responseHeader;}
	//endregion
	//region ResponseWriter
	static final int NOOB_LIMIT = 4080;
	private SpeedLimiter limiter;

	@Override public void limitSpeed(SpeedLimiter limiter) {this.limiter = limiter;}
	@Override public SpeedLimiter getSpeedLimiter() {return limiter;}

	public void write(DynByteBuf buf) throws IOException {
		if ((flag&SEND_BODY) == 0) throw new IllegalStateException();

		int len = buf.readableBytes();
		if (ch.isFlushing() || limiter != null && (len = limiter.limit(len)) <= 0) return;

		ch.channelWrite(len < buf.readableBytes() ? buf.slice(len) : buf);
		sendBytes += len;
	}
	public int write(InputStream in, int limit) throws IOException {
		if ((flag&SEND_BODY) == 0) throw new IllegalStateException();

		if (limit < 0) limit = NOOB_LIMIT;
		if (ch.isFlushing() || limiter != null && (limit = limiter.limit(limit)) <= 0) return 0;

		int writeOnce = Math.min(NOOB_LIMIT, limit);
		var buf = (ByteList) ch.allocate(false, writeOnce);
		try {
			int totalRead = 0;
			while (true) {
				int r = Math.min(limit - totalRead, writeOnce);
				if (r == 0 || (r = buf.readStream(in, r)) <= 0) {
					if (totalRead == 0) return r;
					break;
				}

				totalRead += r;

				ch.channelWrite(buf);
				if (ch.isFlushing()) break;
				buf.clear();
			}

			sendBytes += totalRead;
			return totalRead;
		} finally {
			buf.release();
		}
	}

	private boolean outEof() throws IOException {
		// for close-notify or hang
		time = System.currentTimeMillis() + 100;

		if (ch.isOutputOpen()) {
			ch.postEvent(hDeflate.OUT_FINISH);
			if ((flag & FLAG_GZIP) != 0) {
				var hd = (hDeflate) ch.channel().handler(hDeflate.ANCHOR).handler();
				var buf = ch.allocate(false, 8).putIntLE(hd.getOutCrc()).putIntLE(hd.getDef().getTotalIn());
				try {
					var ctx = ch.channel().handler(hTE.ANCHOR);
					// noinspection all
					ctx.handler().channelWrite(ctx, buf);
				} finally {
					buf.release();
				}
			}
			ch.postEvent(hTE.OUT_FINISH);
		}

		try {
			if (fh != null && fh.onRequestFinish(this, !hasError())) {
				state = CLOSED;
				finish(true);
				MyChannel channel = ch.channel();
				ch.removeSelf();
				channel.fireOpen();
				channel.readActive();
				return true;
			}
			fh = null;
		} catch (Exception e) {
			LOGGER.warn("onRequestFinish时发生了异常", e);
		}

		if (req == null || "close".equalsIgnoreCase(req.responseHeader.get("connection"))) {
			ch.channel().closeGracefully();
			state = CLOSED;
		} else {
			state = HANG_PRE;
			ch.readActive();
			finish(false);
		}
		return false;
	}

	//endregion
	//region Compress / Chunk
	@Override
	public void handlerAdded(ChannelCtx ctx) {ch = ctx;}

	@Override
	public void handlerRemoved(ChannelCtx ctx) {
		finish(true);

		ch = null;
		ctx.channel().remove(hDeflate.ANCHOR);
		ctx.channel().remove(hCE.ANCHOR);
		ctx.channel().remove(hTE.ANCHOR);
	}

	private void deflate(ChannelCtx ctx, int enc, ByteList hdr) {
		hDeflate hd;

		var exist = ctx.channel().handler(hDeflate.ANCHOR);
		if (exist == null) {
			if (enc == HttpServer.ENC_PLAIN) return;
			ctx.channel().addBefore(ctx, hDeflate.ANCHOR, hd = new hDeflate(1024));
		} else {
			hd = (hDeflate) exist.handler();

			if (enc == HttpServer.ENC_PLAIN) {
				if (hd.getDef() != null) {
					HttpServer.getInstance().reserve(hd.getDef(), (flag&FLAG_GZIP) != 0);
					hd.setDef(null);
				}
				return;
			}
		}

		int level = Deflater.DEFAULT_COMPRESSION;
		if (req != null) {
			String levelStr = req.responseHeader.remove(":compression-level");
			if (levelStr != null) level = Integer.parseInt(levelStr);
		}

		Deflater def;
		if (enc == HttpServer.ENC_GZIP) {
			def = HttpServer.getInstance().deflater(true);
			hd.setOutCrc(true);
			flag |= FLAG_GZIP;
			hdr.putAscii("content-encoding: gzip\r\n");
		} else {
			def = HttpServer.getInstance().deflater(false);
			hd.setOutCrc(false);
			flag &= ~FLAG_GZIP;
			hdr.putAscii("content-encoding: deflate\r\n");
		}

		def.setLevel(level);
		hd.setDef(def);
	}
	//endregion
	//region Finish & Close
	@Override
	public void channelClosed(ChannelCtx ctx) {
		finish(true);
		state = CLOSED;
	}

	private void finish(boolean close) {
		var t = HttpServer.getInstance();

		if (state == HANG) t.keepalive.remove(this);

		Exception e = null;
		if (ph != null) {
			ch.channel().remove("http:body_parser");
			try {
				ph.onComplete();
			} catch (Exception e1) {
				e = e1;
			}
			ph = null;
		}
		if (body != null) {
			try {
				body.release(ch);
			} catch (Exception e1) {
				if (e == null) e = e1;
				else e.addSuppressed(e1);
			}
			body = null;
		}

		if (req != null) {
			if (LOGGER.canLog(Level.TRACE)) accessLog();
			t.reserve(req);
			req = null;
		}

		var pb = postBuffer;
		if (pb != null && (BufferPool.isPooled(pb) || close)) {
			pb.release();
			postBuffer = null;
		}

		code = 0;
		flag = KEPT_ALIVE;
		limiter = null;

		hCE.reset(ch);
		hTE.reset(ch);
		deflate(ch, HttpServer.ENC_PLAIN, null);

		if (fh != null) fh.onRequestFinish(this, false);

		try {
			super.channelClosed(ch);
		} catch (Exception e1) {
			if (e == null) e = e1;
			else e.addSuppressed(e1);
		}
		if (e != null) Helpers.athrow(e);
	}

	public void onFinish(RequestFinishHandler o) {fh = o;}
	public boolean hasError() {return (flag & FLAG_ERRORED) != 0;}
	//endregion
}