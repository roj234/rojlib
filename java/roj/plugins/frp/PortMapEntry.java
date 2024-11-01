package roj.plugins.frp;

import java.net.InetAddress;

/**
 * @author Roj234
 * @since 2024/4/6 0006 21:27
 */
public final class PortMapEntry {
	public String name;
	public char port;
	public boolean udp;

	public InetAddress address;
	public char initialPort;

	private PortMapEntry _next;

	public PortMapEntry(char port, String desc, boolean udp) {
		name = desc;
		this.port = initialPort = port;
		this.udp = udp;
	}

	@Override public String toString() {return "("+name+") "+(udp?"udp":"tcp")+"://"+(address == null ? "[remote]" : address.getHostAddress())+":"+(int)port;}
}