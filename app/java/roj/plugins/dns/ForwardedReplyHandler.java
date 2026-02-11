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
final class ForwardedReplyHandler implements ChannelHandler, Consumer<MyChannel> {
	DnsServer server;
	DnsSession dnsSession = new DnsSession();

	public ForwardedReplyHandler(DnsServer server) {this.server = server;}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) {
		var p = (DatagramPkt) msg;

		var req = server.forwardQueue.get(dnsSession.init(p));
		if (req == null) {
			System.out.println("过期或已完成: " + dnsSession);
		} else {
			req.handle(server, p.data, dnsSession);
		}
	}

	@Override
	public void accept(MyChannel ch) {
		ch.addLast("ForwardQueryHandler", this);
	}
}