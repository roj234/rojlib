package roj.net.p2p;

import roj.collect.MyHashSet;
import roj.io.NIOUtil;
import roj.net.http.HttpRequest;
import roj.util.ByteList;

import java.io.IOException;
import java.net.*;

/**
 * @author Roj234
 * @since 2024/1/9 0009 3:27
 */
public class NAT {
	public static final String TCP_KEEPALIVE_SERVER = "www.baidu.com";

	public static void main(String[] args) throws Exception {
		System.out.println("Start checking NAT type, this may cost upto 30 seconds");
		System.out.println("UDP IPv4 NAT TYPE: "+checkNatType_UDP(false, true));
		System.out.println("TCP IPv4 NAT TYPE: "+checkNatType_TCP(false, true));
		System.setProperty("java.net.preferIPv6Addresses", "true");
		System.out.println("UDP IPv6 NAT TYPE: "+checkNatType_UDP(true, false));
		System.out.println("TCP IPv6 NAT TYPE: "+checkNatType_TCP(true, false));
	}

	/**
	 * <a href="https://www.rfc-editor.org/rfc/rfc5780">NAT Behavior Discovery Using Session Traversal Utilities for NAT (STUN)</a><br>
	 * <a href="https://i2.wp.com/img-blog.csdnimg.cn/20200408145057307.png">RFC3478 Binding Lifetime Discovery</a>
	 */
	public static String checkNatType_UDP(boolean checkIpV6, boolean tryUPnP) throws IOException {
		MyHashSet<InetAddress> endpoints = new MyHashSet<>();
		endpoints.addAll(InetAddress.getAllByName(InetAddress.getLocalHost().getHostName()));

		STUN.Response server2 = null;

		InetSocketAddress myAddr;
		try (DatagramSocket so = new DatagramSocket()) {
			myAddr = (InetSocketAddress) so.getLocalSocketAddress();
		}

		char port = (char) myAddr.getPort();
		boolean opened = tryUPnP&&UPnPGateway.available()&&UPnPGateway.openPort("checkNat", port, port, false, 60000);

		try {
			for (int i = 0; i < StunServers.FIRST_TCP_ONLY_INDEX; i++) {
				InetSocketAddress server = StunServers.getAddress(i, checkIpV6);
				if (server == null) continue;

				STUN.Response r = STUN.request(server, 1500, STUN.UDP, myAddr);
				if (r.errCode != 0) continue;

				if (server2 == null) {
					server2 = r;
					continue;
				}

				if (!server2.internetAddress.getAddress().equals(r.internetAddress.getAddress())) return "NAT_SYMMETRIC";

				r = STUN.request(server, 1000, STUN.UDP|STUN.CHANGE_PORT|STUN.CHANGE_IP, myAddr);
				if (r.serverAddress != null && (r.serverAddress.getAddress().equals(server.getAddress()) || r.serverAddress.getPort() == server.getPort())) continue;

				if (endpoints.contains(server2.internetAddress.getAddress()) && server2.internetAddress.getPort() == myAddr.getPort()) {
					if (r.errCode == 0) return "NAT_OPEN_INTERNET";
					return "NAT_SYMMETRIC_FIREWALL";
				}

				if (r.errCode == 0) return "NAT_FULL_CONE";

				r = STUN.request(server, 1000, STUN.UDP|STUN.CHANGE_PORT, myAddr);
				if (r.errCode == 0) return "NAT_RESTRICTED";
				return "NAT_PORT_RESTRICTED";
			}

			return "NAT_UDP_BLOCKED";
		} finally {
			if (opened) UPnPGateway.closePort(port, false);
		}
	}

	public static String checkNatType_TCP(boolean checkIpV6, boolean tryUPnP) throws IOException {
		MyHashSet<InetAddress> endpoints = new MyHashSet<>();
		endpoints.addAll(InetAddress.getAllByName(InetAddress.getLocalHost().getHostName()));

		try (ServerSocket listener = new ServerSocket()) {
			listener.setReuseAddress(true);
			NIOUtil.windowsSetReusePort(NIOUtil.socketFD(listener));

			listener.bind(new InetSocketAddress(0));
			InetSocketAddress myAddr = (InetSocketAddress) listener.getLocalSocketAddress();

			int port = myAddr.getPort();
			boolean opened = tryUPnP&&UPnPGateway.available()&&UPnPGateway.openPort("checkNat", port, port, true, 60000);

			try (Socket query = new Socket(Proxy.NO_PROXY)) {
				query.setReuseAddress(true);
				NIOUtil.windowsSetReusePort(NIOUtil.socketFD(query));

				query.bind(myAddr);

				query.connect(new InetSocketAddress(TCP_KEEPALIVE_SERVER, 80), 1000);
				query.getOutputStream().write(("GET /~ HTTP/1.1\r\nHost: "+TCP_KEEPALIVE_SERVER+"\r\nConnection: keep-alive\r\n\r\n").getBytes());

				int successCount = 0;
				STUN.Response first = null;
				for (int i = StunServers.LAST_UDP_ONLY; i < StunServers.STUN_SERVER_COUNT; i++) {
					InetSocketAddress server = StunServers.getAddress(i, checkIpV6);
					if (server == null) continue;

					STUN.Response r = STUN.request(server, 1500, 0, myAddr);
					if (r.errCode != 0) continue;

					if (first == null) {
						// localAddress返回是绑定用的地址0:0:0:0...
						if (endpoints.contains(r.internetAddress.getAddress()) && r.internetAddress.getPort() == myAddr.getPort()) return "NAT_OPEN_INTERNET";

						// this is very slow
						Boolean result = checkTCPPort(r.internetAddress.getPort());
						if (result == null) break; // UNKNOWN
						if (result) return "NAT_FULL_CONE";

						first = r;
					} else {
						if (!first.internetAddress.getAddress().equals(r.internetAddress.getAddress())) return "NAT_SYMMETRIC";

						if (++successCount == 2) return "NAT_PORT_RESTRICTED";

						// RESTRICTED => 同一IP不同端口, 不部署服务器没有办法检测
					}
				}
			} finally {
				if (opened) UPnPGateway.closePort(port, true);
			}
		}
		return "NAT_UNKNOWN";
	}

	public static Boolean checkTCPPort(int port) {
		try {
			ByteList data = HttpRequest.nts().url(new URL("http://portcheck.transmissionbt.com/"+port)).execute(10000).bytes();
			char isOk = data.charAt(0);
			if (isOk == '1') return true;
			else if (isOk == '0') return false;
		} catch (Exception ignored) {}
		return null;
	}
}