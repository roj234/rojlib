package roj.plugins.dns;

import roj.net.ChannelCtx;
import roj.net.ChannelHandler;
import roj.net.DatagramPkt;
import roj.net.MyChannel;

import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2023/3/4 18:00
 */
final class ForwardQueryHandler implements ChannelHandler, Consumer<MyChannel> {
	DnsServer server;
	XAddr xAddr = new XAddr();

	public ForwardQueryHandler(DnsServer server) {
		this.server = server;
	}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) {
		DatagramPkt p = ((DatagramPkt) msg);

		ForwardQuery req = server.waiting.get(xAddr.init(p));
		if (req == null) {
			System.out.println("过期或已完成: " + xAddr);
			if (server.forwardDns.contains(p.address)) {
				server.handleUnknown(p.data, xAddr);
			} else {
				System.out.println("Unknown数据包 " + xAddr);
			}
		} else {
			req.handle(server, p.data, xAddr);
		}
	}

	@Override
	public void accept(MyChannel ch) {
		ch.addLast("ForwardQueryHandler", this);
	}
}