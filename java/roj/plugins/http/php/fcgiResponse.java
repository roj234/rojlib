package roj.plugins.http.php;

import roj.io.FastFailException;
import roj.io.IOUtil;
import roj.net.ch.ChannelCtx;
import roj.net.http.Headers;
import roj.net.http.server.*;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2024/7/1 0001 19:12
 */
public final class fcgiResponse extends AsyncResponse implements HPostHandler {
	static final int INITIAL = 0, OPENED = 1, LOCAL_SIGNAL = 2, LOCAL_FINISH = 3, REMOTE_FINISH = 4, ERROR = 5;

	fcgiConnection conn;
	int requestId;
	int state;

	private ByteList tmpData;
	private Headers headers = new Headers();
	private boolean headerFinished;
	private final HttpServer11 server;

	public fcgiResponse(ResponseHeader server) {this.server = (HttpServer11) server;}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		var b = (DynByteBuf) msg;
		if (b.isReadable()) fcgi_STDIN(b);
	}
	@Override
	public void onSuccess() throws IOException {
		if (conn != null) fcgi_STDIN(ByteList.EMPTY);
		else if (state < LOCAL_SIGNAL) state = LOCAL_SIGNAL;
	}
	final void opened() throws IOException {
		boolean signalled = state == LOCAL_SIGNAL;
		state = OPENED;
		if (tmpData != null) {
			fcgi_STDIN(tmpData);
			tmpData._free();
		}
		if (signalled) onSuccess();
	}

	private static final int FCGI_MAX = 65528;
	private void fcgi_STDIN(DynByteBuf b) throws IOException {
		if (state != OPENED) {
			if (state < OPENED) {
				if (tmpData == null) tmpData = new ByteList();
				tmpData.put(b);
				b.rIndex = b.wIndex();
				return;
			}
			fcgiManager.LOGGER.warn("fcgi_STDIN的状态错误({}), 期待OPENED", state);
		}

		var ctx = conn.ensureOpen();

		var tmp = IOUtil.getSharedByteBuf();

		ctx.channel().lock().lock();
		try {
			// well this is TCP, right ?
			while (b.readableBytes() > FCGI_MAX) {
				tmp.clear();
				ctx.channelWrite(tmp.putShort(0x0105).putShort(requestId).putInt(FCGI_MAX << 16));
				ctx.channelWrite(b.slice(FCGI_MAX));
			}

			int len = b.readableBytes();
			if (len == 0) state = LOCAL_FINISH;

			tmp.clear();
			ctx.channelWrite(tmp.putShort(0x0105).putShort(requestId).putShort(len).putShort(0));
			ctx.channelWrite(b);
		} finally {
			ctx.channel().lock().unlock();
		}
	}

	public boolean isHeaderFinished() {return headerFinished;}

	@Override
	public synchronized void prepare(ResponseHeader rh, Headers h) throws IOException {
		super.prepare(rh, h);
		var httpCode = headers.remove("status");
		h.putAll(headers);
		headers = null;
		if (httpCode != null) rh.code(Integer.parseInt(httpCode.substring(0, httpCode.indexOf(' '))));
		if (h.getField("content-type").startsWith("text/")) rh.enableCompression();
	}

	@Override
	public boolean offer(DynByteBuf buf) {
		if (!headerFinished && headers.parseHead(buf)) {
			headerFinished = true;
			sendHeaders();
			if (headers != null) {
				var ch = server.ch();
				throw new FastFailException("Http客户端("+(ch==null?"<地址不可知>":ch.remoteAddress())+")的请求已完成("+server.getState()+"), 异步FastCGI响应被忽略");
			}
		}
		return !buf.isReadable() || super.offer(buf);
	}

	@Override
	public void setEof() {
		if (state < REMOTE_FINISH)
			state = REMOTE_FINISH;
		super.setEof();
	}

	@Override
	public void release(ChannelCtx ctx) throws IOException {
		try {
			super.release(ctx);
		} finally {
			if (state < REMOTE_FINISH)
				state = ERROR;
			if (hasSpaceCallback != null) hasSpaceCallback.run();
		}
	}

	public void fail(Throwable ex) throws IOException {
		if (conn != null) conn.abort = 0;
		fcgiManager.LOGGER.warn("FastCGI Error", ex);
		if (!isEof()) {
			headers = new Headers();
			headers.put("status", "502 Bad Gateway");
			sendHeaders();
			headerFinished = true;
			CharSequence str = StringResponse.detailedErrorPage(502, ex).getStr();
			offer(IOUtil.getSharedByteBuf().putUTFData(str));
			setEof();
		}
	}

	private void sendHeaders() {
		try {
			if (server.getState() == 3/*HARDCODED PROCESSING*/)
				server.ch().fireChannelWrite(this);
		} catch (IOException e) {
			Helpers.athrow(e);
		}
	}

	public void onEmpty(Runnable o) {hasSpaceCallback = o;}
}