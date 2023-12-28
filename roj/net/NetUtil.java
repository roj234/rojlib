package roj.net;

import roj.asm.type.Type;
import roj.reflect.DirectAccessor;
import roj.text.CharList;
import roj.text.TextUtil;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;

/**
 * @author Roj234
 * @since 2020/10/30 23:05
 */
public final class NetUtil {
	public static final ByteBuffer EMPTY = ByteBuffer.allocate(0);

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
				int st = TextUtil.parseInt(num, 16);
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
		int st = TextUtil.parseInt(num, 16);
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
		if (addr.length == 4) { // IPv4
			return v4bytesIp(addr, 0);
		} else { // IPv6
			assert addr.length == 16 : "address length neither v4 nor v6";
			return v6bytesIp(addr, 0);
		}
	}
	public static String v4bytesIp(byte[] addr, int off) { return new CharList().append(addr[off++]&0xFF).append('.').append(addr[off++]&0xFF).append('.').append(addr[off++]&0xFF).append('.').append(addr[off]&0xFF).toStringAndFree(); }
	public static String v6bytesIp(byte[] addr, int off) {
		// xxxx:xxxx:xxxx:xxxx:xxxx:xxxx:xxxx:xxxx
		CharList sb = new CharList(40);
		int i = 0;
		while (true) {
			sb.append(Integer.toHexString((0xFF & addr[off++]) << 8 | (addr[off++] & 0xFF)));
			if (++i == 8) break;
			sb.append(':');
		}

		return sb.toStringAndFree();
	}
	// endregion
	// region utils for reading string a:b port
	public static InetSocketAddress parseListeningAddress(String s) { return parseAddress(s, null); }
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
	// endregion

	public static void putHostCache(boolean negative, String host, long expire, InetAddress... addresses) {
		initUtil();
		Object cache = negative ? Util.getNegativeHostCache() : Util.getHostCache();
		synchronized (cache) {
			Object entry = Util.newCacheEntry(addresses, expire);
			Util.getInternalMap(cache).put(host, entry);
		}
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
					DirectAccessor<H> b = DirectAccessor.builder(H.class);
					try {
						b.i_construct("java.net.InetAddress$CacheEntry", "([Ljava/net/InetAddress;J)V", "newCacheEntry")
						 .access(InetAddress.class, new String[] {"addressCache", "negativeCache"}, new String[] {"getHostCache", "getNegativeHostCache"}, null)
						 .i_access("java.net.InetAddress$Cache", "cache", new Type("java/util/LinkedHashMap"), "getInternalMap", null, false);
					} catch (Throwable e) {
						e.printStackTrace();
					}
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
					Util = b.build();
				}
			}
		}
	}

	private static volatile H Util;
	private interface H {
		Object newCacheEntry(InetAddress[] addresses, long expire);

		default Object getHostCache() {
			throw new UnsupportedOperationException();
		}
		default Object getNegativeHostCache() {
			throw new UnsupportedOperationException();
		}

		LinkedHashMap<String, Object> getInternalMap(Object cache);

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