package roj.plugins.p2p;

import roj.collect.SimpleList;
import roj.net.NetUtil;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * @author Roj234
 * @since 2024/1/12 0012 2:59
 */
public final class Servers {
	public static Servers getDefault() { return INSTANCE; }
	public static void setDefault(Servers r) {
		if (r == null) throw new NullPointerException("servers");
		INSTANCE = r;
	}
	private static Servers INSTANCE = new Servers("www.baidu.com", "dns.alidns.com",
		// UDP only
		"stun.miwifi.com", "stun.qq.com", "stun.chat.bilibili.com",
		null,
		// TCP and UDP
		"fwa.lifesizecloud.com",
		"stun.isp.net.au",
		"stun.nextcloud.com",
		"stun.freeswitch.org",
		"stun.voip.blackberry.com",
		"stunserver.stunprotocol.org",
		"stun.sipnet.com",
		"stun.radiojar.com",
		"stun.sonetel.com",
		"stun.voipgate.com"
	);

	public Servers(String tcpKeepAliveHost, String udpKeepAliveHost, String... objects) {
		this.tcpKeepaliveServer = new InetSocketAddress[] {NetUtil.parseUnresolvedAddress(tcpKeepAliveHost, 80)};
		this.udpKeepaliveServer = new InetSocketAddress[] {NetUtil.parseUnresolvedAddress(udpKeepAliveHost, 53)};

		int udpPos = 0;
		List<String> list = new SimpleList<>();
		for (int j = 0; j < objects.length; j++) {
			String s = objects[j];
			if (s == null) {
				udpPos = j;
			} else {
				list.add(s);
			}
		}

		servers = list.toArray();
		lastUdpOnly = udpPos;
		stunServerCount = servers.length;
	}

	private InetSocketAddress[] tcpKeepaliveServer, udpKeepaliveServer;
	public InetSocketAddress getKeepaliveServer(boolean tcp, boolean ipv6) {
		InetSocketAddress[] arr = tcp ? tcpKeepaliveServer : udpKeepaliveServer;

		if (arr.length == 1) {
			InetSocketAddress oldHost = arr[0];
			int port = oldHost.getPort();

			arr = new InetSocketAddress[2];
			try {
				for (InetAddress address : InetAddress.getAllByName(oldHost.getHostString())) {
					if (address instanceof Inet4Address) {
						arr[0] = new InetSocketAddress(address, port);
					} else {
						arr[1] = new InetSocketAddress(address, port);
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}

			if (tcp) tcpKeepaliveServer = arr;
			else udpKeepaliveServer = arr;
		}

		return arr[ipv6?1:0];
	}

	public final int lastUdpOnly, stunServerCount;
	private final Object[] servers;
	public InetSocketAddress getStunServer(int i, boolean ipv6) {
		Object addr = servers[i];
		if (addr == null) return null;

		if (addr.getClass() == String.class) {
			String url = addr.toString();
			int j = url.indexOf(':');
			int port;
			if (j < 0) { j = url.length(); port = STUN.DEFAULT_STUN_PORT; }
			else port = Integer.parseInt(url.substring(j + 1));

			try {
				InetAddress[] addrs = InetAddress.getAllByName(url.substring(0, j));
				InetAddress v4 = null, v6 = null;
				for (InetAddress address : addrs) {
					if (address instanceof Inet4Address) v4 = address;
					else v6 = address;
				}

				if (v4 == null) {
					assert v6 != null;
					addr = new InetSocketAddress(v6, port);
				} else if (v6 == null) {
					addr = new InetSocketAddress(v4, port);
				} else {
					addr = new Object[] { new InetSocketAddress(v4, port), new InetSocketAddress(v6, port) };
				}

				servers[i] = addr;
			} catch (UnknownHostException e) {
				servers[i] = null;
				return null;
			}
		}

		if (addr.getClass() == Object[].class) {
			Object[] arr = (Object[]) addr;
			return (InetSocketAddress) (ipv6 ? arr[1] : arr[0]);
		}
		InetSocketAddress addr1 = (InetSocketAddress) addr;
		if (addr1.getAddress() instanceof Inet4Address == ipv6) return null;
		return addr1;
	}
}