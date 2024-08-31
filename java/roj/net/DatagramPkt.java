package roj.net;

import roj.util.DynByteBuf;

import java.net.InetAddress;
import java.net.InetSocketAddress;

public final class DatagramPkt {
	public DynByteBuf buf;
	public InetAddress addr;
	public int port;

	public DatagramPkt(InetSocketAddress address, DynByteBuf wb) {
		addr = address.getAddress();
		port = address.getPort();
		buf = wb;
	}

	public DatagramPkt(InetAddress address, int port, DynByteBuf wb) {
		addr = address;
		this.port = port;
		buf = wb;
	}

	public DatagramPkt(DatagramPkt p, DynByteBuf buf) {
		addr = p.addr;
		port = p.port;
		this.buf = buf;
	}

	public DatagramPkt() {}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		DatagramPkt pkt = (DatagramPkt) o;

		if (port != pkt.port) return false;
		return addr.equals(pkt.addr);
	}

	@Override
	public int hashCode() {
		return 31 * addr.hashCode() + port;
	}

	@Override
	public String toString() {
		return addr + ":" + port + "{" + buf.readableBytes() + '}';
	}
}
