package roj.plugins.dns;

import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.net.ch.DatagramPkt;
import roj.net.ch.MyChannel;

import java.net.InetSocketAddress;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2023/3/4 0004 18:00
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
			if (server.forwardDns.contains(new InetSocketAddress(p.addr, p.port))) {
				server.handleUnknown(p.buf, xAddr);
			} else {
				System.out.println("Unknown数据包 " + xAddr);
			}
		} else {
			req.handle(server, p.buf, xAddr);
		}
	}

	@Override
	public void accept(MyChannel ch) {
		ch.addLast("ForwardQueryHandler", this);
	}
}