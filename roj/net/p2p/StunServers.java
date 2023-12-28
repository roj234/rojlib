package roj.net.p2p;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

/**
 * @author Roj234
 * @since 2024/1/12 0012 2:59
 */
public final class StunServers {
	private static final Object[] STUN_SERVERS = new Object[]{
		// UDP only
		"stun.miwifi.com", "stun.qq.com", "stun.chat.bilibili.com",

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
		"stun.voipgate.com",

		// TCP only
	};
	// ths last UDP only stun server index+1
	public static final int LAST_UDP_ONLY = 3;
	// ths first TCP only stun server index
	public static final int FIRST_TCP_ONLY_INDEX = 13;
	public static final int STUN_SERVER_COUNT = STUN_SERVERS.length;
	public static InetSocketAddress getAddress(int i, boolean ipv6) {
		Object addr = STUN_SERVERS[i];
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

				STUN_SERVERS[i] = addr;
			} catch (UnknownHostException e) {
				STUN_SERVERS[i] = null;
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