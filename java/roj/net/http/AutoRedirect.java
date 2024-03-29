package roj.net.http;

import roj.io.FastFailException;
import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.net.ch.Event;
import roj.net.ch.handler.Timeout;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.IOException;
import java.net.URL;

/**
 * @author Roj234
 * @since 2023/5/11 0011 14:29
 */
public class AutoRedirect extends Timeout implements ChannelHandler {
	private final HttpRequest req;
	private int maxRedirect, maxRetry;
	private URL redirectPending;

	public AutoRedirect(HttpRequest req, int timeout, int redirect) {
		this(req,timeout,redirect,0);
	}
	public AutoRedirect(HttpRequest req, int timeout, int redirect, int retry) {
		super(timeout, 1000);
		this.req = req;
		maxRedirect = redirect;
		maxRetry = retry;
	}

	@Override
	public void handlerRemoved(ChannelCtx ctx) { redirectPending = null; }

	@Override
	public void channelOpened(ChannelCtx ctx) throws IOException {
		HttpHead header = req.response();

		int code = header.getCode();
		if (maxRedirect >= 0 && code >= 200 && code < 400) {
			String location = header.get("location");
			if (location != null) {
				if (maxRedirect-- == 0) {
					maxRetry = 0;
					throw new FastFailException("AutoRedirect:重定向过多");
				}

				redirectPending = new URL(req.url(), location);
				return;
			}
		}

		ctx.channelOpened();
	}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		if (redirectPending == null) super.channelRead(ctx, msg);
		else {
			DynByteBuf b = (DynByteBuf) msg;
			b.rIndex = b.wIndex();
		}
	}

	@Override
	public void onEvent(ChannelCtx ctx, Event event) throws IOException {
		if (event.id.equals(HttpRequest.DOWNLOAD_EOF)) {
			if (event.getData() == Boolean.TRUE) {
				if (redirectPending != null) {
					event.stopPropagate();
					ctx.invokeLater(() -> {
						try {
							req._redirect(ctx.channel(), redirectPending, readTimeout);
							redirectPending = null;
						} catch (IOException e) {
							Helpers.athrow(e);
						}
					});
					return;
				}

				ctx.removeSelf();
				maxRetry = 0; // do not retry
			}
		}
	}

	@Override
	protected void closeChannel(ChannelCtx ctx) {
		throw new FastFailException("连接"+ctx.remoteAddress()+"超时");
	}

	@Override
	public void exceptionCaught(ChannelCtx ctx, Throwable ex) throws Exception {
		if (maxRetry-- > 0) {
			req._address = null;
			req._redirect(ctx.channel(), req.url(), readTimeout);

			lastRead = System.currentTimeMillis();
		} else {
			ctx.exceptionCaught(ex);
		}
	}
}
