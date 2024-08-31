package roj.net;

import org.jetbrains.annotations.Nullable;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.net.handler.Socks5Client;
import roj.reflect.Bypass;
import roj.text.CharList;
import roj.text.Escape;
import roj.text.TextUtil;

import java.io.IOException;
import java.net.*;
import java.util.Enumeration;

/**
 * @author Roj234
 * @since 2020/10/30 23:05
 */
public final class NetUtil {
	private static SimpleList<NetworkInterface> networkInterfaces;
	private static MyHashSet<InetAddress> endpoints;
	private static boolean hasAnyIpV6Address;
	static { refreshEndpoints(); }

	public static void refreshEndpoints() {
		SimpleList<NetworkInterface> interfaces = new SimpleList<>();
		MyHashSet<InetAddress> addresses = new MyHashSet<>();
		boolean val = false;

		try {
			Enumeration<NetworkInterface> itr = NetworkInterface.getNetworkInterfaces();
			while (itr.hasMoreElements()) {
				NetworkInterface itf = itr.nextElement();
				if (/*itf.getParent() == null && */itf.isUp() && !itf.isLoopback()) {
					interfaces.add(itf);
					Enumeration<InetAddress> itr1 = itf.getInetAddresses();
					while (itr1.hasMoreElements()) {
						InetAddress endpoint = itr1.nextElement();
						if (endpoint instanceof Inet6Address) {
							val = true;
						}
						addresses.add(endpoint);
					}
				}
			}
		} catch (IOException ignored) {}

		networkInterfaces = interfaces;
		endpoints = addresses;
		hasAnyIpV6Address = val;
	}

	public static boolean hasAnyIpV6Address() { return hasAnyIpV6Address; }
	public static MyHashSet<InetAddress> getNetworkEndpoints() { return endpoints; }
	public static SimpleList<NetworkInterface> getNetworkInterfaces() { return networkInterfaces; }

	private static InetAddress any;
	public static InetAddress anyLocalAddress() {
		if (any != null) return any;
		return any = new InetSocketAddress(0).getAddress();
	}

	// region IP String-Bytes convert
	public static byte[] ip2bytes(CharSequence ip) { return TextUtil.gLastIndexOf(ip, '.') != -1 ? v4ipBytes(ip) : v6ipBytes(ip); }
	public static byte[] v4ipBytes(CharSequence ip) {
		byte[] addr = new byte[4];
		int pos = 0;

		CharList num = new CharList(4);
		for (int i = 0; i < ip.length(); i++) {
			char c = ip.charAt(i);
			if (c == '.') {
				addr[pos++] = (byte) TextUtil.parseInt(num);
				if (pos == 4) throw new IllegalArgumentException("invalid ipv4: "+ip);
				num.clear();
			} else {
				num.append(c);
			}
		}

		if (num.length() == 0 || pos != 3) throw new IllegalArgumentException("invalid ipv4: "+ip);
		addr[3] = (byte) TextUtil.parseInt(num);
		return addr;
	}
	public static byte[] v6ipBytes(CharSequence ip) {
		// subnet mask
		int len = TextUtil.gIndexOf(ip, '%');
		if (len < 0) len = ip.length();

		byte[] addr = new byte[16];

		int j = 0, colon = -1;
		CharList num = new CharList(5);
		for (int i = 0; i < len; i++) {
			char c = ip.charAt(i);
			if (c == ':') {
				if (num.length() == 0) {
					if (i == 0) throw new IllegalArgumentException("Not support :: at first");
					if (ip.charAt(i - 1) != ':') throw new IllegalArgumentException("Single ':': "+ip);
					if (colon >= 0) throw new IllegalArgumentException("More than one ::");
					colon = j;
					continue;
				}
				int st = TextUtil.parseInt(num, 1);
				addr[j++] = (byte) (st >> 8);
				addr[j++] = (byte) st;

				if (j == 16) throw new IllegalArgumentException("Address overflow: "+ip);
				num.clear();
			} else if (TextUtil.HEX.contains(c)) {
				num.append(c);
			} else {
				throw new IllegalArgumentException("Invalid character at "+i+": "+ip);
			}
		}

		if ((colon == -1 && (num.length() == 0 || j != 14)) || j > 14) throw new IllegalArgumentException("Address overflow: " + ip);
		int st = TextUtil.parseInt(num, 1);
		addr[j++] = (byte) (st >> 8);
		addr[j] = (byte) st;

		if (colon >= 0) {
			len = j - colon + 1;
			for (int i = 1; i <= len; i++) {
				addr[16 - i] = addr[j - i + 1];
				addr[j - i + 1] = 0;
			}
		}

		return addr;
	}

	public static String bytes2ip(byte[] addr) {
		try {
			return InetAddress.getByAddress(addr).getHostAddress();
		} catch (UnknownHostException e) {
			throw new IllegalArgumentException("address length neither v4 nor v6");
		}
	}
	// endregion
	// region utils for reading string a:b port
	public static InetSocketAddress parseListeningAddress(String s) { return parseAddress(s, null); }
	@Deprecated
	public static InetSocketAddress parseConnectAddress(String s) { return parseAddress(s, InetAddress.getLoopbackAddress()); }
	public static InetSocketAddress parseAddress(String s, InetAddress defaultAddr) {
		if (s == null || s.isEmpty()) throw new IllegalArgumentException("null port");

		int pos = s.lastIndexOf(':');

		InetAddress host;
		try {
			host = pos < 0 ? defaultAddr : InetAddress.getByName(s.substring(0, pos));
		} catch (UnknownHostException e) {
			throw new IllegalArgumentException("unknown host");
		}

		InetSocketAddress addr;
		try {
			addr = new InetSocketAddress(host, Integer.parseInt(s.substring(pos+1)));
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("illegal port");
		}
		return addr;
	}
	public static InetSocketAddress parseAddress(String hostStr, int defaultPort) {
		if (hostStr == null || hostStr.isEmpty()) throw new IllegalArgumentException("null port");
		int pos = hostStr.lastIndexOf(':');
		return new InetSocketAddress(pos < 0 ? hostStr : hostStr.substring(0, pos), pos < 0 ? defaultPort : Integer.parseInt(hostStr.substring(pos+1)));
	}
	public static InetSocketAddress parseUnresolvedAddress(String hostStr, int defaultPort) {
		if (hostStr == null || hostStr.isEmpty()) throw new IllegalArgumentException("null port");
		int pos = hostStr.lastIndexOf(':');
		return InetSocketAddress.createUnresolved(pos < 0 ? hostStr : hostStr.substring(0, pos), pos < 0 ? defaultPort : Integer.parseInt(hostStr.substring(pos+1)));
	}
	// endregion

	public static URI socks5(InetSocketAddress server) {return URI.create("socks5://"+server);}
	public static URI socks5(InetSocketAddress server, String username, String password) {return URI.create("socks5://"+ Escape.encodeURIComponent(username)+":"+Escape.encodeURIComponent(password)+"@"+server);}

	public static InetSocketAddress applyProxy(@Nullable URI proxy, InetSocketAddress originalAddr, MyChannel ch) throws IOException {
		if (proxy == null) return originalAddr;
		switch (proxy.getScheme()) {
			default -> throw new IllegalArgumentException("Not know "+proxy+" proxy");
			case "socks5" -> {
				String info = proxy.getRawUserInfo();
				String user = null, pass = null;
				if (info != null) {
					int i = info.indexOf(':');
					if (i < 0) {
						user = Escape.decodeURI(info);
					} else {
						user = Escape.decodeURI(info.substring(0, i));
						pass = Escape.decodeURI(info.substring(i+1));
					}
				}
				ch.addFirst("@proxy", new Socks5Client(originalAddr, user, pass));
				return new InetSocketAddress(InetAddress.getByName(proxy.getHost()), proxy.getPort());
			}
		}
	}

	@Nullable
	public static String getOriginalHostName(InetAddress address) {
		initUtil();
		return Util.getOriginalHostName(Util.getHolder(address));
	}

	public static void setHostCachePolicy(boolean negative, int seconds) {
		if (seconds < 0) seconds = -1;
		initUtil();
		if (negative) {
			Util.setNegCachePolicy(seconds);
			Util.setNegCacheSet(true);
		} else {
			Util.setPosCachePolicy(seconds);
			Util.setPosCacheSet(true);
		}
	}

	private static void initUtil() {
		if (Util == null) {
			synchronized (NetUtil.class) {
				if (Util == null) {
					Bypass<H> b = Bypass.builder(H.class);
					try {
						Class<?> pl = Class.forName("sun.net.InetAddressCachePolicy");
						String[] fieldName = new String[] {"cachePolicy", "negativeCachePolicy", "propertySet", "propertyNegativeSet"};
						try {
							pl.getDeclaredField("propertySet");
						} catch (NoSuchFieldException e) {
							fieldName[2] = "set";
							fieldName[3] = "negativeSet";
						}
						b.access(pl, fieldName, null, new String[] {"setPosCachePolicy", "setNegCachePolicy", "setPosCacheSet", "setNegCacheSet"});
					} catch (Throwable e) {
						e.printStackTrace();
					}
					try {
						Class<?> pl = Class.forName("java.net.InetAddress$InetAddressHolder");
						b.access(pl, "originalHostName", "getOriginalHostName", null);
						b.access(InetAddress.class, "holder", "getHolder", null);
					} catch (Throwable e) {
						e.printStackTrace();
					}
					Util = b.build();
				}
			}
		}
	}

	private static volatile H Util;
	private interface H {
		Object getHolder(InetAddress address);
		String getOriginalHostName(Object o);

		default void setPosCachePolicy(int seconds) {
			throw new UnsupportedOperationException();
		}
		default void setNegCachePolicy(int seconds) {
			throw new UnsupportedOperationException();
		}

		void setPosCacheSet(boolean set);
		void setNegCacheSet(boolean set);
	}
}