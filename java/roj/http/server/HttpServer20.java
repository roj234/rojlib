package roj.http.server;

import roj.crypt.CRC32s;
import roj.http.Headers;
import roj.http.HttpHead;
import roj.http.HttpUtil;
import roj.http.h2.H2Connection;
import roj.http.h2.H2Exception;
import roj.http.h2.H2Stream;
import roj.http.hDeflate;
import roj.io.FastFailException;
import roj.io.IOUtil;
import roj.io.buf.BufferPool;
import roj.net.*;
import roj.net.util.SpeedLimiter;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.Deflater;

import static roj.http.server.HSConfig.*;
import static roj.http.server.HttpServer11.*;

/**
 * @author Roj234
 * @since 2024/7/14 0014 8:38
 */
final class HttpServer20 extends H2Stream implements PostSetting, ResponseHeader, ContentWriter, ChannelHandler {
	private final Router router;

	public HttpServer20(Router router, int id) {
		super(id);
		this.router = router;
		this.time = System.currentTimeMillis() + router.readTimeout(null);
	}

	private long time;
	private H2Connection man;
	private Request req;
	private Content body;
	private RequestFinishHandler fh;

	@Override
	protected void onHeaderDone(H2Connection man, HttpHead head, boolean hasData) throws IOException {
		String path = head.getPath(), query;
		int i = path.indexOf('?');
		if (i < 0) query = "";
		else {
			query = path.substring(i+1);
			path = path.substring(0, i);
		}
		var req = HSConfig.getInstance().request().init(HttpUtil.getMethodId(head.getMethod()), path, query, head.versionStr());
		req._moveFrom(head);

		req.server = this;
		this.man = man;
		this.req = req;
		this.flag = 0;

		if (!hasData) {
			router.checkHeader(req, null);
			return;
		}

		postSize = -1;
		exceptPostSize = head.getContentLength();
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
	private BodyParser ph;
	private ByteList postBuffer;

	@Override
	public void postHandler(BodyParser ph) {this.ph = ph;}

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
			req.bodyData = ph == null ? postBuffer : ph;

			try {
				if (ph != null) ph.onSuccess(postBuffer);
				if ((flag&FLAG_HEADER_SENT) != 0) return;

				var resp = router.response(req, this);
				if (body == null) body = resp;
				else if (resp != null) throw new FastFailException("已调用body()设置请求体,response必须返回null");
			} catch (Throwable e) {
				onError(man, e);
			}

			if ((flag & FLAG_ASYNC) != 0 && body == null) return;
			time = System.currentTimeMillis() + router.writeTimeout(req, body);
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
			if (h.header("content-length").equals("0")) {
				noBody = true;
				h.remove("content-length");
				body.release(man.channel());
			} else if ((flag & FLAG_COMPRESS) != 0 && req.containsKey("accept-encoding") && !h.containsKey("content-encoding")) {
				enc = HSConfig.getInstance().parseAcceptEncoding(req.get("accept-encoding"));
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
		if ((flag&FLAG_HEADER_SENT) != 0) {
			if (!pending.isReadable() || !man.sendData(this, pending, dataEnd)) {
				pending.clear();
				if (!dataEnd && !body.send(this)) sendLastData();
			}
		}

		if (System.currentTimeMillis() > time) {
			switch (getState()) {
				case HEAD_V, HEAD_R, DATA, HEAD_T -> {
					if (req != null) throw new IllegalRequestException(HttpUtil.TIMEOUT);
					man.streamError(id, H2Exception.ERROR_CANCEL/*TIMEOUT*/);
				}
				case DATA_FIN -> {
					if (getOutState() == OPEN) {
						code(504).body(Content.internalError("处理超时\n在规定的时间内未收到响应头，请求代理失败"));
					} else if (fh == null || !fh.onResponseTimeout(this)) {
						H2Connection.LOGGER.warn("发送超时[在规定的时间内未能将响应体全部发送]: {}", body);
						man.streamError(id, H2Exception.ERROR_INTERNAL/*TIMEOUT*/);
					}
				}
			}
		}
	}

	@Override
	protected void onError(H2Connection man, Throwable e) {
		if ((flag & FLAG_ERRORED) == 0 && getOutState() == OPEN && req != null) {
			flag |= FLAG_ERRORED;
			req.responseHeader.clear();

			if (e instanceof IllegalRequestException ire) {
				if (ire.code != 0) code(ire.code);
				body = ire.createResponse();
			} else {
				code(500);
				body = HttpServer11.onUncaughtError(req, e);
				H2Connection.LOGGER.error("路由发生了异常", e);
			}
			return;
		}

		super.onError(man, e);
	}

	@Override
	public MyChannel connection() {return man.channel().channel();}

	@Override
	protected void onFinish(H2Connection man) {
		var t = HSConfig.getInstance();

		onFinish(false);

		if (body != null) {
			try {
				body.release(man.channel());
			} catch (Exception e) {
				H2Connection.LOGGER.warn("{}完成时出现了异常", e, body.getClass());
			}
			body = null;
		}
		if (ph != null) {
			try {
				ph.onComplete();
			} catch (Exception e) {
				H2Connection.LOGGER.warn("{}完成时出现了异常", e, ph.getClass());
			}
			ph = null;
		}

		if (req != null) {
			t.reserve(req);
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

	private void onFinish(boolean success) {
		if (fh != null) {
			try {
				fh.onRequestFinish(this, success);
			} catch (Exception e) {
				H2Connection.LOGGER.warn("{}完成时出现了异常", e, fh.getClass());
			}
			fh = null;
		}
	}

	private long sendBytes;
	@Override public long getBytesSent() {return sendBytes;}

	private boolean sendData(DynByteBuf buf) throws IOException {
		sendBytes += buf.readableBytes();
		if (embed == null) return man.sendData(this, buf, false);

		if ((flag & FLAG_GZIP) != 0) crc = CRC32s.update(crc, buf);
		embed.fireChannelWrite(buf);
		return pending.wIndex() > 0;
	}
	private void sendLastData() throws IOException {
		if (embed != null) embed.postEvent(new Event(hDeflate.OUT_FINISH));
		if ((flag & FLAG_GZIP) != 0) pending.putIntLE(CRC32s.retVal(crc)).putIntLE(def.getTotalIn());
		man.sendData(this, pending, true);
		dataEnd = true;
		onFinish((flag&ERRORED) == 0);
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
				def = HSConfig.getInstance().deflater(true);
				flag |= FLAG_GZIP;
				crc = CRC32s.INIT_CRC;
				header("content-encoding", "gzip");
			}
			case ENC_DEFLATE -> {
				def = HSConfig.getInstance().deflater(false);
				flag &= ~FLAG_GZIP;
				header("content-encoding", "gzip");
			}
		}

		int level = Deflater.DEFAULT_COMPRESSION;
		String levelStr = req.responseHeader.remove(":compression-level");
		if (levelStr != null) level = Integer.parseInt(levelStr);
		def.setLevel(level);

		var compress = new hDeflate(1024);
		compress.setDef(def);

		embed = EmbeddedChannel.createReadonly().addLast("write_hook", this).addLast(hDeflate.ANCHOR, compress);
	}

	//region ResponseWriter
	private SpeedLimiter limiter;

	@Override public void limitSpeed(SpeedLimiter limiter) {this.limiter = limiter;}
	@Override public SpeedLimiter getSpeedLimiter() {return limiter;}

	public void write(DynByteBuf buf) throws IOException {
		assert ((ReentrantLock) man.channel().channel().lock()).isHeldByCurrentThread();

		int limit = buf.readableBytes();
		int window = man.getImmediateWindow(this);
		if (limit > window) limit = window;
		if (limiter != null && (limit = limiter.limit(limit)) <= 0) return;

		int prevWpos = buf.wIndex();
		buf.wIndex(buf.rIndex + limit);
		try {
			sendData(buf);
		} finally {
			buf.wIndex(prevWpos);
		}
	}

	public int write(InputStream in, int limit) throws IOException {
		assert ((ReentrantLock) man.channel().channel().lock()).isHeldByCurrentThread();

		int window = man.getImmediateWindow(this);
		if (window <= 0) return 0;
		window = Math.min(window, man.getRemoteSetting().max_frame_size);

		if (limit < 0) limit = NOOB_LIMIT;
		if (limit > window) limit = window;
		if (limiter != null) limit = limiter.limit(limit);
		if (limit <= 0) return 0;

		int writeOnce = Math.min(NOOB_LIMIT, limit);
		var buf = (ByteList) man.channel().allocate(false, writeOnce);
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

			return totalRead;
		} finally {
			BufferPool.reserve(buf);
		}
	}

	//endregion
	//region ResponseHeader
	@Override public void onFinish(RequestFinishHandler o) {fh = o;}
	@Override public Request request() {return req;}

	@Override public ResponseHeader code(int code) {req.responseHeader.put(":status", String.valueOf(code));return this;}
	@Override public ResponseHeader die() {flag |= FLAG_GOAWAY;return this;}
	@Override public ResponseHeader enableAsyncResponse(int extraTimeMs) {flag |= FLAG_ASYNC;time = System.currentTimeMillis()+extraTimeMs;return this;}
	@Override public void body(Content resp) throws IOException {
		if ((flag & FLAG_ASYNC) == 0) {
			if (getState() >= DATA_FIN) {
				this.body = resp;
				return;
			}

			flag |= FLAG_HEADER_SENT;
		} else if (getOutState() != OPEN) {
			throw new IllegalStateException("Expect PROCESSING: "+_getState());
		}

		var lock = man.channel().channel().lock();
		lock.lock();
		try {
			body = resp;
			time = System.currentTimeMillis() + router.writeTimeout(req, body);
			sendHead(man);
		} catch (Throwable e) {
			onError(man, e);
		} finally {
			lock.unlock();
		}
	}
	@Override public ResponseHeader enableCompression() {flag |= FLAG_COMPRESS;return this;}
	@Override public ResponseHeader disableCompression() {flag &= ~FLAG_COMPRESS;return this;}
	@Override public Headers headers() {return req.responseHeader;}
	//endregion
}