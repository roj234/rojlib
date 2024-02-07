package roj.net.ch.handler;

import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.net.ch.DatagramPkt;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;

public class TCPasUDP implements ChannelHandler {
	InetSocketAddress address;

	@Override
	public void channelOpened(ChannelCtx ctx) throws IOException {
		address = (InetSocketAddress) ctx.remoteAddress();
		ctx.channelOpened();
	}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		ctx.channelRead(new DatagramPkt(address, (DynByteBuf) msg));
	}

	@Override
	public void channelWrite(ChannelCtx ctx, Object msg) throws IOException {
		DatagramPkt pkt = (DatagramPkt) msg;
		if (!pkt.addr.equals(address.getAddress()) || pkt.port != address.getPort()) throw new ClosedChannelException();
		ctx.channelWrite(pkt.buf);
	}
}
