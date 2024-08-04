package roj.net.http.server;

import roj.crypt.CRC32s;
import roj.io.FastFailException;
import roj.io.IOUtil;
import roj.io.buf.BufferPool;
import roj.net.ch.*;
import roj.net.http.*;
import roj.net.http.h2.H2Connection;
import roj.net.http.h2.H2Exception;
import roj.net.http.h2.H2Stream;
import roj.text.logging.Logger;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.Deflater;

import static roj.net.http.server.HttpCache.*;
import static roj.net.http.server.HttpServer11.*;

/**
 * @author Roj234
 * @since 2024/7/14 0014 8:38
 */
public final class HttpServer20 extends H2Stream implements PostSetting, ResponseHeader, ResponseWriter, ChannelHandler {
	private static final Logger LOGGER = Logger.getLogger("HtpSvr/2");

	private final Router router;

	public HttpServer20(Router router, int id) {
		super(id);
		this.router = router;
		this.time = System.currentTimeMillis() + router.readTimeout(null);
	}

	private static final byte FLAG_GOAWAY = 1;
	private byte flag;

	private long time;
	private H2Connection man;
	private Request req;
	private Response body;

	@Override
	protected void onHeaderDone(H2Connection man, HttpHead head, boolean hasData) throws IOException {
		String path = head.getPath(), query;
		int i = path.indexOf('?');
		if (i < 0) query = "";
		else {
			query = path.substring(i+1);
			path = path.substring(0, i);
		}
		var req = HttpCache.getInstance().request().init(HttpUtil.parseMethod(head.getMethod()), path, query, head.versionStr());
		req._moveFrom(head);

		req.handler = this;
		this.man = man;
		this.req = req;
		this.flag = 0;

		if (!hasData) {
			router.checkHeader(req, null);
			return;
		}

		postSize = -1;
		exceptPostSize = head.getContentLengthLong();
		router.checkHeader(req, this);
		time = System.currentTimeMillis() + router.readTimeout(req);

		long len = postSize;
		if (len < 0) {
			man.streamError(id, H2Exception.ERROR_CANCEL);
			throw new FastFailException("CancelPost");
		}

		if (exceptPostSize != -1) {
			if (exceptPostSize > len) throw new IllegalRequestException(req.isExpecting() ? 417 : HttpUtil.ENTITY_TOO_LARGE);
			// TODO if (req.isExpecting())
			len = exceptPostSize;
		}

		if (ph != null) len = 512;
		else if (len > 8388608) throw new IllegalArgumentException("请求体过大("+len+")必须使用PostHandler");

		postBuffer = len <= 65535 ? (ByteList) man.channel().allocate(false, (int) len) : new ByteList();
	}

	//region PostSetting
	private HPostHandler ph;
	private ByteList postBuffer;

	@Override
	public void postHandler(HPostHandler ph) {this.ph = ph;}

	private long postSize, exceptPostSize;

	public long postExceptLength() {return exceptPostSize;}

	public void postAccept(long maxLen, int time) {
		if (postSize >= 0) throw new IllegalStateException();
		postSize = maxLen;
		this.time += time;
	}

	public boolean postAccepted() {return postSize >= 0;}

	//endregion
	@Override
	protected String onData(H2Connection man, DynByteBuf buf) throws IOException {
		if ((flag & FLAG_ERRORED) != 0) return null;

		var pb = postBuffer;
		if (ph != null) {
			if (pb.isReadable()) {
				ph.channelRead(man.channel(), pb.put(buf));
				pb.compact();
				return null;
			} else {
				ph.channelRead(man.channel(), buf);
				if (!buf.isReadable()) return null;
			}
		} else if (pb.readableBytes() + buf.readableBytes() > postSize)
			throw new IllegalRequestException(HttpUtil.ENTITY_TOO_LARGE);

		if (pb.writableBytes() < buf.readableBytes()) {
			pb = postBuffer = (ByteList) man.channel().alloc().expand(pb, buf.readableBytes(), true, true);
		}

		pb.put(buf);
		return null;
	}

	@Override
	protected void onDone(H2Connection man) throws IOException {
		if ((flag & FLAG_ERRORED) == 0) {
			req.postFields = ph == null ? postBuffer : ph;

			try {
				if (ph != null) ph.onSuccess();

				var resp = router.response(req, this);
				if (body == null) body = resp;
				else if (resp != null) throw new FastFailException("已调用body()设置请求体,response必须返回null");
			} catch (Exception e) {
				onError(man, e);
			}

			time = System.currentTimeMillis() + router.writeTimeout(req, body);
			if ((flag & FLAG_ASYNC) != 0 && body == null) return;
		}
		sendHead(man);
	}

	private void sendHead(H2Connection man) throws IOException {
		var h = req.responseHeader;
		h.putIfAbsent(":status", "200");
		h.put("server", HttpServer11.SERVER_NAME);

		int enc = ENC_PLAIN;
		boolean noBody = false;
		if (body != null) {
			body.prepare(this, h);
			if (h.getField("content-length").equals("0")) {
				noBody = true;
				h.remove("content-length");
				body.release(man.channel());
			} else if ((flag & FLAG_COMPRESS) != 0 && req.containsKey("accept-encoding") && !h.containsKey("content-encoding")) {
				enc = HttpCache.getInstance().parseAcceptEncoding(req.get("accept-encoding"));
				setCompr(enc);
				h.remove("content-length");
			}
		} else {
			noBody = true;
		}
		man.sendHeader(this, h, noBody);

		if (enc == ENC_GZIP) {
			var buf = IOUtil.getSharedByteBuf().putShort(0x1f8b).putLong((long) Deflater.DEFLATED << 56);
			if (man.sendData(this, buf, false)) pending.put(buf);
		}

		if ((flag & FLAG_GOAWAY) != 0) man.goaway(H2Exception.ERROR_OK, "from connection: close");
	}

	public void tick(H2Connection man) throws IOException {
		if (getState() == SEND_BODY) {
			if (!pending.isReadable() || !man.sendData(this, pending, dataEnd)) {
				pending.clear();
				if (!dataEnd) {
					streamLimit = streamLimitDefault;
					if (!body.send(this)) sendLastData();
				}
			}
		}

		if (System.currentTimeMillis() > time) {
			switch (getState()) {
				case HEAD_V, HEAD_R, DATA, HEAD_T -> {
					if (req != null) throw new IllegalRequestException(HttpUtil.TIMEOUT);
					man.streamError(id, H2Exception.ERROR_CANCEL/*TIMEOUT*/);
				}
				case PROCESSING -> code(504).body(Response.internalError("处理超时\n在规定的时间内未收到响应头，请求代理失败"));
				case SEND_BODY -> {
					LOGGER.warn("发送超时[在规定的时间内未能将响应体全部发送]: {}", body);
					man.streamError(id, H2Exception.ERROR_INTERNAL/*TIMEOUT*/);
				}
			}
		}
	}

	@Override
	protected void onError(H2Connection man, Exception e) {
		if ((flag & FLAG_ERRORED) == 0 && getState() < SEND_BODY && req != null) {
			flag |= FLAG_ERRORED;
			req.responseHeader.clear();

			if (e instanceof IllegalRequestException ire) {
				code(ire.code);
				body = ire.createResponse();
			} else {
				code(500);
				body = HttpServer11.onUncaughtError(req, e);
				LOGGER.error("路由发生了异常", e);
			}
			return;
		}

		super.onError(man, e);
	}

	@Override
	public MyChannel ch() {return man.channel().channel();}

	@Override
	protected void onFinish(H2Connection man) {
		var t = HttpCache.getInstance();

		if (body != null) {
			try {
				body.release(man.channel());
			} catch (Exception e) {
				LOGGER.warn("{}完成时出现了异常", e, ph.getClass());
			}
			body = null;
		}
		if (ph != null) {
			try {
				ph.onComplete();
			} catch (Exception e) {
				LOGGER.warn("{}完成时出现了异常", e, ph.getClass());
			}
			ph = null;
		}

		if (req != null) {
			if ((flag&FLAG_UNSHARED) == 0) t.reserve(req);
			req = null;
		}

		if (def != null) t.reserve(def, (flag&FLAG_GZIP) != 0);

		var pb = postBuffer;
		if (pb != null) {
			BufferPool.reserve(pb);
			postBuffer = null;
		}

		embed = null;
		pending._free();
	}

	private boolean sendData(DynByteBuf buf) throws IOException {
		if (embed == null) return man.sendData(this, buf, false);

		int len = buf.readableBytes();
		if ((flag & FLAG_GZIP) != 0) {
			crc = buf.hasArray() ? CRC32s.update(crc, buf.array(), buf.arrayOffset() + buf.rIndex, len) : CRC32s.update(crc, buf.address(), len);
		}
		embed.fireChannelWrite(buf);
		return pending.wIndex() > 0;
	}
	private void sendLastData() throws IOException {
		if (embed != null) embed.postEvent(new Event(HCompress.EVENT_CLOSE_OUT));
		if ((flag & FLAG_GZIP) != 0) pending.putIntLE(CRC32s.retVal(crc)).putIntLE(def.getTotalIn());
		man.sendData(this, pending, true);
		dataEnd = true;
	}

	private boolean dataEnd;
	private ByteList pending = new ByteList();
	private MyChannel embed;
	private int crc;
	private Deflater def;

	@Override
	public void channelWrite(ChannelCtx ctx, Object msg) throws IOException {
		DynByteBuf buf = (DynByteBuf) msg;
		if (man.sendData(this, buf, false)) pending.put(buf);
	}

	private void setCompr(int enc) {
		switch (enc) {
			default -> {return;}
			case ENC_GZIP -> {
				def = HttpCache.getInstance().deflater(true);
				flag |= FLAG_GZIP;
				crc = CRC32s.INIT_CRC;
				header("content-encoding", "gzip");
			}
			case ENC_DEFLATE -> {
				def = HttpCache.getInstance().deflater(false);
				flag &= ~FLAG_GZIP;
				header("content-encoding", "gzip");
			}
		}

		int level = Deflater.DEFAULT_COMPRESSION;
		String levelStr = req.responseHeader.remove(":compression-level");
		if (levelStr != null) level = Integer.parseInt(levelStr);
		def.setLevel(level);

		var compress = new HCompress(1024);
		compress.setDef(def);

		embed = EmbeddedChannel.createReadonly().addLast("write_hook", this).addLast("hcompr", compress);
	}

	//region ResponseWriter
	private static final int WRITE_ONCE = 4080;
	private int streamLimit, streamLimitDefault = WRITE_ONCE;

	public int getStreamLimit() {return streamLimitDefault;}

	public void setStreamLimit(int kbps) {this.streamLimitDefault = kbps;}

	public void write(DynByteBuf buf) throws IOException {
		assert ((ReentrantLock) man.channel().channel().lock()).isHeldByCurrentThread();
		sendData(buf);
	}

	public int write(InputStream in, int limit) throws IOException {
		assert ((ReentrantLock) man.channel().channel().lock()).isHeldByCurrentThread();

		int window = man.getImmediateWindow(this);
		if (window <= 0) return 0;

		limit = limit <= 0 ? streamLimit : Math.min(streamLimit, limit);
		if (limit > window) limit = window;
		if (limit <= 0) return 0;

		int writeOnce = Math.min(WRITE_ONCE, limit);
		ByteList buf = (ByteList) man.channel().allocate(false, writeOnce);
		try {
			int totalRead = 0;
			while (true) {
				int r = Math.min(limit - totalRead, writeOnce);
				if (r == 0) break;

				r = buf.readStream(in, r);
				if (r <= 0) {
					if (totalRead == 0) return r;
					break;
				}

				totalRead += r;

				if (sendData(buf)) break;
				buf.clear();
			}

			streamLimit -= totalRead;
			return totalRead;
		} finally {
			BufferPool.reserve(buf);
		}
	}

	//endregion
	//region ResponseHeader
	@Override public void onFinish(HFinishHandler o) {throw new UnsupportedOperationException("HTTP/2不支持FinishHandler");}
	@Override public Request request() {return req;}

	@Override public ResponseHeader code(int code) {req.responseHeader.put(":status", String.valueOf(code));return this;}
	@Override public ResponseHeader die() {flag |= FLAG_GOAWAY;return this;}
	@Override public void unsharedRequest() {flag |= FLAG_UNSHARED;}
	@Override public void sharedRequest() {flag &= ~FLAG_UNSHARED;}
	@Override public ResponseHeader enableAsyncResponse() {flag |= FLAG_ASYNC;return this;}
	@Override public void body(Response resp) throws IOException {
		if ((flag & FLAG_ASYNC) != 0) {
			if (getState() != PROCESSING) throw new IllegalStateException("Expect PROCESSING: "+_getState());

			var lock = man.channel().channel().lock();
			lock.lock();
			try {
				body = resp;
				time = System.currentTimeMillis() + router.writeTimeout(req, body);
				sendHead(man);
			} catch (Exception e) {
				onError(man, e);
			} finally {
				lock.unlock();
			}
		} else {
			this.body = resp;
		}
	}
	@Override public ResponseHeader enableCompression() {flag |= FLAG_COMPRESS;return this;}
	@Override public ResponseHeader disableCompression() {flag &= ~FLAG_COMPRESS;return this;}
	@Override public Headers headers() {return req.responseHeader;}
	//endregion
}