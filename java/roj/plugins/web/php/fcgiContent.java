package roj.plugins.web.php;

import roj.http.Headers;
import roj.http.server.*;
import roj.io.FastFailException;
import roj.io.IOUtil;
import roj.net.ChannelCtx;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;

/**
 * @author Roj234
 * @since 2024/7/1 19:12
 */
public final class fcgiContent extends AsyncContent implements BodyParser {
	fcgiConnection conn;

	private static final int INITIAL = 0, OPENED = 1, LOCAL_SIGNAL = 2, LOCAL_FINISH = 3, REMOTE_FINISH = 4, ERROR = 5;
	private int state;

	private ByteList tmpData;
	private Headers headers = new Headers();
	private boolean headerFinished;
	private final Request req;

	public fcgiContent(Request req) {this.req = req;}

	private ChannelCtx lazyRead;
	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		var b = (DynByteBuf) msg;
		if (b.isReadable()) {
			fcgi_STDIN(b);
			if (conn != null) conn.checkFlushing(ctx);
			else {
				ctx.readInactive();
				lazyRead = ctx;
			}
		}
	}
	@Override
	public void onSuccess(DynByteBuf rest) throws IOException {
		if (conn != null) fcgi_STDIN(ByteList.EMPTY);
		else if (state < LOCAL_SIGNAL) state = LOCAL_SIGNAL;
	}
	final void opened() throws IOException {
		boolean signalled = state == LOCAL_SIGNAL;
		state = OPENED;
		if (tmpData != null) {
			fcgi_STDIN(tmpData);
			tmpData._free();
			if (lazyRead != null)
				lazyRead.readActive();
		}
		if (signalled) onSuccess(null);
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
			if (state == LOCAL_FINISH) return;
		}

		var ctx = conn.ensureOpen();

		var tmp = IOUtil.getSharedByteBuf();

		ctx.channel().lock().lock();
		try {
			// well this is TCP, right ?
			while (b.readableBytes() > FCGI_MAX) {
				tmp.clear();
				ctx.channelWrite(tmp.putLong(0x0105_0001_0000_0000L | ((long) FCGI_MAX << 16)));
				ctx.channelWrite(b.slice(FCGI_MAX));
			}

			int len = b.readableBytes();
			if (len == 0) state = LOCAL_FINISH;

			tmp.clear();
			ctx.channelWrite(tmp.putInt(0x0105_0001).putShort(len).putShort(0));
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
		if (h.header("content-type").startsWith("text/")) rh.enableCompression();
	}

	@Override
	public boolean offer(DynByteBuf buf) {
		if (!headerFinished && Headers.parseHeader(headers, buf)) {
			headerFinished = true;
			sendBody();
		}
		return !buf.isReadable() || super.offer(buf);
	}
	private void sendBody() {
		var msg = isInvalid();
		if (msg != null) throw new FastFailException(req.firstLine(new CharList().append("请求(")).append(")被客户端取消: ").append(msg).toStringAndFree());

		String sendFile = headers.remove("x-sendfile");
		Content body;
		if (sendFile != null) {
			req.responseHeader().putAll(headers);
			File file = new File(sendFile);
			if (!file.isFile()) body = Content.internalError("x-sendfile返回的路径无效:"+sendFile);
			else body = Content.file(req, new DiskFileInfo(file));
		} else {
			body = this;
		}

		try {
			req.server().body(body);
		} catch (IOException e) {
			Helpers.athrow(e);
		}
		headers = null;
	}
	@Override
	public void setEof() {
		if (state < REMOTE_FINISH)
			state = REMOTE_FINISH;
		super.setEof();
	}

	@Override
	public void release(ChannelCtx ctx) throws IOException {
		super.release(ctx);

		if (state != REMOTE_FINISH) state = ERROR;
		else if (isInvalid() == null) req.shared();

		if (conn != null) conn.requestFinished(this);
		hasSpaceCallback();
	}
	@Override protected void hasSpaceCallback() {if (conn != null) conn.ensureOpen().readActive();}

	public void fail(Throwable ex) {
		if (conn != null) conn.requestFinished(this);

		var msg = isInvalid();
		fcgiManager.LOGGER.warn("FastCGI Error ({})", ex, msg);
		state = ERROR;
		if (msg == null) {
			if (!headerFinished) {
				headers.clear();
				headers.put("status", "502 Bad Gateway");
				sendBody();
				headerFinished = true;
			}
			offer(IOUtil.getSharedByteBuf().putUTFData("<div class='position:absolute;background:black;color:red;font-size:24px'>Bad Gateway (FastCGI Error)</div>"));
			setEof();
		}
	}

	public String isInvalid() {
		if (state >= REMOTE_FINISH) return "LState: "+state;
		try {
			var state = req.server()._getState();
			if (!state.equals("PROCESSING") && !state.equals("RECV_BODY")) return "HState: "+state;
			return null;
		} catch (Exception e) {
			return "Unexpected: "+e;
		}
	}
}