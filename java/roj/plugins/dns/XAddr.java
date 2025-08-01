package roj.plugins.dns;

import roj.net.DatagramPkt;

import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * @author Roj234
 * @since 2023/3/4 18:00
 */
final class XAddr implements Cloneable {
	InetAddress addr;
	char port, id;

	XAddr() {}

	XAddr(InetSocketAddress socketAddress, char sessionId) {
		this.addr = socketAddress.getAddress();
		this.port = (char) socketAddress.getPort();
		this.id = sessionId;
	}

	XAddr init(DatagramPkt packet) {
		this.addr = packet.address.getAddress();
		this.port = (char) packet.address.getPort();
		this.id = (char) packet.data.readUnsignedShort(packet.data.rIndex);
		return this;
	}

	protected XAddr clone() {
		XAddr n = null;
		try {
			n = (XAddr) super.clone();
		} catch (CloneNotSupportedException e) {
			e.printStackTrace();
		}
		return n;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		XAddr xAddr = (XAddr) o;

		if (port != xAddr.port) return false;
		if (id != xAddr.id) return false;
		return addr.equals(xAddr.addr);
	}

	@Override
	public int hashCode() {
		int result = addr.hashCode();
		result = 31 * result + port;
		result = 31 * result + id;
		return result;
	}

	@Override
	public String toString() {
		return String.valueOf(addr) + ':' + (int) port + '#' + (int) id;
	}
}