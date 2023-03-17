package roj.net;

import roj.asm.type.Type;
import roj.math.MathUtils;
import roj.reflect.DirectAccessor;
import roj.text.CharList;
import roj.text.TextUtil;
import sun.net.InetAddressCachePolicy;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.LinkedHashMap;

/**
 * @author Roj234
 * @since 2020/10/30 23:05
 */
public final class NetworkUtil {
	public static final ByteBuffer EMPTY = ByteBuffer.allocate(0);

	public static void trustAllCertificates() {
		try {
			SSLContext.getDefault().init(null, new TrustManager[] {new TrustAllManager()}, null);
		} catch (NoSuchAlgorithmException | KeyManagementException ignored) {}
		// should not happen
	}

	static InetAddress any;
	public static InetAddress anyLocalAddress() {
		if (any != null) return any;
		return any = new InetSocketAddress(0).getAddress();
	}

	// IP Conservation

	public static byte[] IPv42int(CharSequence ip) {
		byte[] arr = new byte[4];

		int found = 0;
		CharList fl = new CharList(5);
		for (int i = 0; i < ip.length(); i++) {
			char c = ip.charAt(i);
			if (c == '.') {
				arr[found++] = (byte) MathUtils.parseInt(fl);
				if (found == 4) throw new RuntimeException("IP format error " + ip);
				fl.clear();
			} else {
				fl.append(c);
			}
		}

		if (fl.length() == 0 || found != 3) throw new RuntimeException("IP format error " + ip);
		arr[3] = (byte) MathUtils.parseInt(fl);
		return arr;
	}

	public static byte[] IPv62int(CharSequence ip) {
		// subnet mask
		int len = TextUtil.gIndexOf(ip, '%');
		if (len < 0) len = ip.length();

		byte[] arr = new byte[16];

		int j = 0, colon = -1;
		CharList fl = new CharList(5);
		for (int i = 0; i < len; i++) {
			char c = ip.charAt(i);
			if (c == ':') {
				if (fl.length() == 0) {
					if (i == 0) throw new IllegalArgumentException("Not support :: at first");
					if (ip.charAt(i - 1) != ':') throw new IllegalArgumentException("Single ':': " + ip);
					if (colon >= 0) throw new IllegalArgumentException("More than one ::");
					colon = j;
					continue;
				}
				int st = MathUtils.parseInt(fl, 16);
				arr[j++] = (byte) (st >> 8);
				arr[j++] = (byte) st;

				if (j == 16) throw new IllegalArgumentException("Address overflow: " + ip);
				fl.clear();
			} else if (TextUtil.HEX.contains(c)) {
				fl.append(c);
			} else {
				throw new IllegalArgumentException("Invalid character at " + i + ": " + ip);
			}
		}

		if ((colon == -1 && (fl.length() == 0 || j != 14)) || j > 14) throw new IllegalArgumentException("Address overflow: " + ip);
		int st = MathUtils.parseInt(fl, 16);
		arr[j++] = (byte) (st >> 8);
		arr[j] = (byte) st;

		if (colon >= 0) {
			len = j - colon + 1;
			for (int i = 1; i <= len; i++) {
				arr[16 - i] = arr[j - i + 1];
				arr[j - i + 1] = 0;
			}
		}

		return arr;
	}

	public static byte[] ip2bytes(CharSequence ip) {
		return TextUtil.gLastIndexOf(ip, '.') != -1 ? IPv42int(ip) : IPv62int(ip);
	}

	public static String bytes2ip(byte[] bytes) {
		if (bytes.length == 4) {
			// IPv4
			return bytes2ipv4(bytes, 0);
		} else {
			// IPv6
			assert bytes.length == 16;

			return bytes2ipv6(bytes, 0);
		}
	}

	public static String bytes2ipv6(byte[] bytes, int off) {
		// xxxx:xxxx:xxxx:xxxx:xxxx:xxxx:xxxx:xxxx
		CharList sb = new CharList(24);
		for (int i = 0; i < 8; i++) {
			sb.append(Integer.toHexString((0xFF & bytes[off++]) << 8 | (bytes[off++] & 0xFF))).append(':');
		}
		sb.setLength(sb.length() - 1);

		return sb.toString();
	}

	public static String bytes2ipv4(byte[] bytes, int off) {
		return String.valueOf(bytes[off++] & 0xFF) + '.' + (bytes[off++] & 0xFF) + '.' + (bytes[off++] & 0xFF) + '.' + (bytes[off] & 0xFF);
	}

	public static final class TrustAllManager implements X509TrustManager {
		@Override
		public void checkClientTrusted(X509Certificate[] chain, String authType) {}

		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType) {}

		@Override
		public X509Certificate[] getAcceptedIssuers() {
			return new X509Certificate[0];
		}
	}

	public static void putHostCache(boolean negative, String host, long expire, InetAddress... addresses) {
		initUtil();
		synchronized (Util.getHostCache()) {
			Object cache = Util.newCacheEntry(addresses, expire);
			Util.getInternalMap(negative ? Util.getNegativeHostCache() : Util.getHostCache()).put(host, cache);
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
			synchronized (NetworkUtil.class) {
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
						Class<?> pl = InetAddressCachePolicy.class;
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

	static volatile H Util;

	private interface H {
		Object newCacheEntry(InetAddress[] addresses, long expire);

		default Object getHostCache() {
			throw new UnsupportedOperationException("Failed to get the field");
		}
		default Object getNegativeHostCache() {
			throw new UnsupportedOperationException("Failed to get the field");
		}

		default LinkedHashMap<String, Object> getInternalMap(Object cache) {
			throw new UnsupportedOperationException("Failed to get the field");
		}

		default void setPosCachePolicy(int seconds) {
			throw new UnsupportedOperationException("Failed to get the field");
		}
		default void setNegCachePolicy(int seconds) {
			throw new UnsupportedOperationException("Failed to get the field");
		}

		default void setPosCacheSet(boolean set) {
			throw new UnsupportedOperationException("Failed to get the field");
		}
		default void setNegCacheSet(boolean set) {
			throw new UnsupportedOperationException("Failed to get the field");
		}
	}

	public static InetSocketAddress getListenAddress(String s) {
		return getAddressByString(s, null);
	}
	public static InetSocketAddress getConnectAddress(String s) {
		return getAddressByString(s, InetAddress.getLoopbackAddress());
	}
	public static InetSocketAddress getAddressByString(String s, InetAddress defaultAddr) {
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
}