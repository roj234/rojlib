package roj.net.http.server;

import roj.io.IOUtil;
import roj.net.ChannelCtx;
import roj.net.ChannelHandler;
import roj.net.http.h2.H2Connection;
import roj.net.http.h2.H2FlowControlSimple;
import roj.util.DynByteBuf;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2024/7/12 0012 20:40
 */
public class H2C implements ChannelHandler {
	public static final String MAGIC = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n";
	private int pos = 0;

	private final Router router;
	public H2C(Router router) {this.router = router;}

	@Override
	public void channelOpened(ChannelCtx ctx) throws IOException {
		if (router == null) ctx.channelWrite(IOUtil.getSharedByteBuf().putAscii(H2C.MAGIC));
		ctx.channelOpened();
	}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		DynByteBuf in = (DynByteBuf) msg;
		int len = in.readableBytes();

		int i = pos;
		for (; i < Math.min(len, MAGIC.length()); i++) {
			if (in.get(in.rIndex + i) != MAGIC.charAt(i)) {
				ctx.removeSelf();
				ctx.channelRead(in);
				return;
			}
		}

		pos = i;
		if (i < MAGIC.length()) return;

		var connection = new H2Connection(id -> new HttpServer20(router, id), true, new H2FlowControlSimple(1048576, 0.75f));

		ctx.removeSelf();
		ctx.channel().replace("h11@server", connection);
		ctx.channelOpened();

		in.rIndex += MAGIC.length();
		ctx.channelRead(in);
	}
}