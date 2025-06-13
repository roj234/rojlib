package roj.http;

import roj.io.IOUtil;
import roj.net.ChannelCtx;
import roj.net.ChannelHandler;
import roj.net.Event;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;

final class HttpClient11 extends HttpRequest implements ChannelHandler {
	static final int SEND_HEAD=0,RECV_HEAD=1,PROCESSING=2,RECV_BODY=3,IDLE=4;

	private HttpHead response;

	HttpClient11() {}

	// TODO channelWrite加一个布尔返回值，表示写不出去了已经开始缓冲了，然后DynByteBuf加一个引用计数，就不需要复制了
	@Override
	public void channelOpened(ChannelCtx ctx) throws IOException {
		state = SEND_HEAD;
		response = null;

		hCE.reset(ctx);
		hTE.reset(ctx);

		ByteList text = IOUtil.getSharedByteBuf().putAscii(method()).putAscii(" ");
		encodeQuery(text).putAscii(" HTTP/1.1\r\n");
		Headers hdr = getHeaders();
		hdr.encode(text);

		ctx.channelWrite(text.putShort(0x0D0A));

		if (initBody() instanceof DynByteBuf b) {
			ctx.channelWrite(b.slice());
		} else {
			/*if ("deflate".equalsIgnoreCase(hdr.get("content-encoding")))
				setCompr(ctx, 1);*/
			if ("chunked".equalsIgnoreCase(hdr.get("transfer-encoding")))
				hTE.apply(ctx, null, 1);
		}

		state = RECV_HEAD;
	}

	@Override
	public void channelTick(ChannelCtx ctx) throws IOException {
		if (_body != null && !writeBody(ctx, _body)) {
			ctx.postEvent(hDeflate.OUT_FINISH);
			ctx.postEvent(hTE.OUT_FINISH);

			if (_body instanceof AutoCloseable c)
				IOUtil.closeSilently(c);
			_body = null;
		}
	}

	@Override
	@SuppressWarnings("fallthrough")
	public void onEvent(ChannelCtx ctx, Event event) throws IOException {
		if (event.id.equals(hCE.IN_END)) end(ctx);
		if (event.id.equals(hCE.TOO_MANY_DATA)) throw new IOException("多余"+event.getData()+"字节");
	}

	@Override
	@SuppressWarnings("fallthrough")
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		DynByteBuf buf = (DynByteBuf) msg;

		switch (state) {
			case RECV_HEAD:
				if (response == null) {
					if ((response = HttpHead.parseFirst(buf)) == null) return;
				}
				// maybe a limit?
				if (!Headers.parseHeader(response, buf, IOUtil.getSharedByteBuf())) return;

				boolean is1xx = response.getCode() >= 100 && response.getCode() < 200;

				state = PROCESSING;
				ctx.channelOpened();
				if (state != PROCESSING) return;

				if (is1xx) {
					response = null;
					state = RECV_HEAD;
					return;
				}

				if (method().equals("HEAD")) {end(ctx);return;}

				state = RECV_BODY;
				long contentLength = response.getContentLength();
				if (contentLength > responseBodyLimit) throw new IOException("多余"+(contentLength-responseBodyLimit)+"字节");

				String contentEncoding = response.getContentEncoding();
				if (!contentEncoding.equals("identity") && !getHeaders().get("accept-encoding", "identity").contains(contentEncoding))
					throw new IllegalArgumentException("不支持的压缩算法:"+contentEncoding);

				ctx = hCE.apply(ctx, response, (flag&SKIP_CE) != 0, responseBodyLimit);
				ctx = hTE.apply(ctx, response, -1);
				ctx.handler().channelRead(ctx, buf);
				return;
			case RECV_BODY:
				ctx.channelRead(buf);
				buf.rIndex = buf.wIndex();
				return;
			case IDLE: if (buf.isReadable()) throwTrailerData(buf);
		}
	}

	private static void throwTrailerData(DynByteBuf buf) throws IOException {throw new IOException("多余的数据:"+buf.slice(Math.min(buf.readableBytes(), 128)));}

	private void end(ChannelCtx ctx) throws IOException {
		if (state != IDLE) {
			state = IDLE;

			Event event = new Event(DOWNLOAD_EOF).capture();
			event.setData(true);
			ctx.postEvent(event);

			synchronized (this) { notifyAll(); }

			if (response != null && response.header("connection").equalsIgnoreCase("close")) {
				try {
					ctx.channel().closeGracefully();
				} catch (IOException ignored) {}
			}
		}
	}

	@Override
	public void channelClosed(ChannelCtx ctx) throws IOException {
		closeBody();
		if (state != IDLE) {
			state = IDLE;

			Event event = new Event(DOWNLOAD_EOF).capture();
			event.setData(false);
			ctx.postEvent(event);

			synchronized (this) { notifyAll(); }
		}
	}

	@Override
	public void handlerRemoved(ChannelCtx ctx) {
		ctx.channel().remove(hCE.ANCHOR);
		ctx.channel().remove(hTE.ANCHOR);
	}

	@Override
	public HttpHead response() { return response; }

	@Override
	public void waitFor() throws InterruptedException {
		synchronized (this) {
			while (state != IDLE) { wait(); }
		}
	}

	@Override
	public HttpRequest clone() {
		return copyTo(new HttpClient11());
	}
}