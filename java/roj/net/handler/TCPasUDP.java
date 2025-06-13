package roj.net.handler;

import roj.net.ChannelCtx;
import roj.net.ChannelHandler;
import roj.net.DatagramPkt;
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
		if (!pkt.address.equals(address)) throw new ClosedChannelException();
		ctx.channelWrite(pkt.data);
	}
}