package roj.net.ch.handler;

import roj.net.ch.ChannelCtx;
import roj.net.ch.DatagramPkt;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * @author Roj234
 * @since 2024/1/9 0009 2:04
 */
public final class UDPAddressBinder extends PacketMerger {
	InetAddress addr;
	int port;

	public UDPAddressBinder(InetSocketAddress address) {
		addr = address.getAddress();
		port = address.getPort();
	}

	@Override
	public void channelWrite(ChannelCtx ctx, Object msg) throws IOException {
		System.out.println("bind address "+addr+":"+port);
		ctx.channelWrite(new DatagramPkt(addr, port, (DynByteBuf) msg));
	}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		DatagramPkt pkt = (DatagramPkt) msg;
		if (pkt.addr.equals(addr) || pkt.port != port) return;
		mergedRead(ctx, pkt.buf);
	}
}