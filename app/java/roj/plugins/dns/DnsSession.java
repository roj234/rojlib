package roj.plugins.dns;

import roj.net.DatagramPkt;

import java.net.InetSocketAddress;

/**
 * @author Roj234
 * @since 2023/3/4 18:00
 */
public final class DnsSession implements Cloneable {
	InetSocketAddress address;
	public char sessionId;

	public DnsSession() {}
	public DnsSession(InetSocketAddress socketAddress, char sessionId) {
		this.address = socketAddress;
		this.sessionId = sessionId;
	}
	public DnsSession init(DatagramPkt packet) {
		this.address = packet.address;
		this.sessionId = (char) packet.data.getUnsignedShort(packet.data.rIndex);
		return this;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		DnsSession session = (DnsSession) o;
		return sessionId == session.sessionId && address.equals(session.address);
	}

	@Override
	public int hashCode() {
		int result = address.hashCode();
		result = 31 * result + sessionId;
		return result;
	}
}