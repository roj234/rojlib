package roj.http.server;

import roj.http.h2.H2Connection;
import roj.http.h2.H2FlowControlSimple;
import roj.io.IOUtil;
import roj.net.ChannelCtx;
import roj.net.ChannelHandler;
import roj.net.Event;
import roj.net.handler.TLSClient;
import roj.util.DynByteBuf;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2024/7/12 20:40
 */
final class H2Upgrade implements ChannelHandler {
	static final String MAGIC = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n";
	private int pos;

	private final Router router;
	H2Upgrade(Router router) {this.router = router;}

	@Override
	public void channelOpened(ChannelCtx ctx) throws IOException {
		if (router == null) ctx.channelWrite(IOUtil.getSharedByteBuf().putAscii(MAGIC));
		//ctx.channelOpened();
	}

	@Override
	public void onEvent(ChannelCtx ctx, Event event) throws IOException {
		if (event.id.equals(TLSClient.ALPN_EVENT)) {
			if (event.getData().toString().equals("h2")) {
				upgrade(ctx);
			}
		}
	}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		DynByteBuf in = (DynByteBuf) msg;
		int len = in.readableBytes();

		int i = pos;
		for (; i < Math.min(len, MAGIC.length()); i++) {
			if (in.get(in.rIndex + i) != MAGIC.charAt(i)) {
				ctx.replaceSelf(HttpServer.http(router));
				ctx.handler().channelOpened(ctx);
				ctx.handler().channelRead(ctx, in);
				return;
			}
		}

		pos = i;
		if (i < MAGIC.length()) return;

		upgrade(ctx);

		in.rIndex += MAGIC.length();
		ctx.handler().channelRead(ctx, in);
	}

	private void upgrade(ChannelCtx ctx) throws IOException {
		var connection = new H2Connection(id -> new HttpServer20(router, id), true, new H2FlowControlSimple(1048576, 0.75f));
		ctx.replaceSelf(connection);
		ctx.handler().channelOpened(ctx);
	}
}