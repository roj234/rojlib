package roj.net;

import roj.util.DynByteBuf;

import java.net.InetAddress;
import java.net.InetSocketAddress;

public final class DatagramPkt {
	public InetSocketAddress address;
	public DynByteBuf data;

	public DatagramPkt() {}
	public DatagramPkt(InetSocketAddress address, DynByteBuf data) {
		this.address = address;
		this.data = data;
	}
	public DatagramPkt(DatagramPkt p, DynByteBuf data) {
		this.address = p.address;
		this.data = data;
	}

	@Deprecated
	public void setAddress(InetAddress ip, char port) {address = new InetSocketAddress(ip, port);}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		DatagramPkt that = (DatagramPkt) o;

		return address.equals(that.address);
	}

	@Override
	public int hashCode() {return address.hashCode();}

	@Override
	public String toString() {return Net.toString(address) + "{" + data.readableBytes() + '}';}
}
