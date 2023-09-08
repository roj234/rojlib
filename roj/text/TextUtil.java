package roj.text;

import roj.collect.*;
import roj.io.IOUtil;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;

import static java.lang.Character.*;

/**
 * @author Roj234
 * @since 2021/6/19 0:14
 */
public class TextUtil {
	public static Charset DefaultOutputCharset = Charset.defaultCharset();

	public static final MyBitSet HEX = MyBitSet.from("0123456789ABCDEFabcdef");

	public static Map<String, String> parseLang(CharSequence content) {
		MyHashMap<String, String> map = new MyHashMap<>();
		if (content == null) return map;
		try {
			boolean block = false;
			CharList sb = new CharList();
			List<String> k_v = new ArrayList<>();
			for (String entry : new LineReader(content)) {
				if (entry.startsWith("#") || entry.isEmpty()) continue;
				if (!block) {
					k_v.clear();
					split(k_v, entry, '=', 2);
					if (k_v.get(1).startsWith("#strl")) {
						block = true;
						sb.clear();
						sb.append(k_v.get(1).substring(5));
					} else {
						map.put(k_v.get(0), k_v.get(1));
					}
				} else {
					if (entry.equals("#endl")) {
						block = false;
						map.put(k_v.get(0), sb.toString());
						sb.clear();
					} else {
						sb.append(entry).append('\n');
					}
				}
			}
		} catch (NullPointerException e) {
			e.printStackTrace();
		}
		return map;
	}

	public static String shuffle(String in) {
		char[] arr = IOUtil.getSharedCharBuf().append(in).list;
		Random r = new Random();
		for (int i = 0; i < in.length(); i++) {
			int j = r.nextInt(in.length());
			char t = arr[i];
			arr[i] = arr[j];
			arr[j] = t;
		}
		return new String(arr, 0, in.length());
	}

	public static String shiftLeft(String name) {
		char[] arr = IOUtil.getSharedCharBuf().append(name).list;
		System.arraycopy(arr, 1, arr, 0, name.length() - 1);
		arr[name.length() - 1] = name.charAt(0);
		return new String(arr, 0, name.length());
	}

	public static CharList repeat(int num, char ch) {
		if (num <= 0) return new CharList();
		CharList sb = new CharList(num);
		Arrays.fill(sb.list, ch);
		sb.setLength(num);
		return sb;
	}

	// 8bits: ⣿ 每个点代表一位
	public static final int BRAILLN_CODE = 10240;

	public final static byte[] digits = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
										 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j',
										 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't',
										 'u', 'v', 'w', 'x', 'y', 'z', 'A', 'B', 'C', 'D',
										 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N',
										 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'};

	/**
	 * 这个字是中文吗
	 *
	 * @return True if is
	 */
	public static boolean isChinese(int c) {
		return (c >= 0x4E00 && c <= 0x9FFF) // 4E00..9FFF
			|| (c >= 0xF900 && c <= 0xFAFF) // F900..FAFF
			|| (c >= 0x3400 && c <= 0x4DBF) // 3400..4DBF
			|| (c >= 0x2000 && c <= 0x206F) // 2000..206F
			|| (c >= 0x3000 && c <= 0x303F) // 3000..303F
			|| (c >= 0xFF00 && c <= 0xFFEF); // FF00..FFEF
	}

	public static String scaledNumber(long number) {
		CharList sb = new CharList();

		if (number < 0) return "-" + scaledNumber(-number);
		if (number >= 1000000) {
			if (number >= 1000000000) {
				sb.append(number / 1000000000).append('.');
				int v = (int) (number % 1000000000 / 10000000);
				if (v < 10) sb.append('0');
				return sb.append(v).append('G').toString();
			}

			sb.append(number / 1000000).append('.');
			int v = (int) (number % 1000000 / 10000);
			if (v < 10) sb.append('0');
			return sb.append(v).append('M').toString();
		} else {
			if (number >= 1000) {
				sb.append(number / 1000).append('.');
				int v = (int) (number % 1000 / 10);
				if (v < 10) sb.append('0');
				return sb.append(v).append('K').toString();
			}
			return String.valueOf(number);
		}
	}

	/**
	 * 找到首个大写字符
	 */
	public static int firstCap(CharSequence str) {
		for (int i = 0; i < str.length(); i++) {
			if (Character.isUpperCase(str.charAt(i))) {
				return i;
			}
		}
		return -1;
	}

	public static boolean isPrintableAscii(int j) {
		return j > 31 && j < 127;
	}

	/**
	 * char to (ascii) number is represents
	 */
	public static int c2i(char c) {
		if (c < 0x30 || c > 0x39) {
			return -1;
		}
		return c-0x30;
	}

	/**
	 * byte to hex
	 */
	public static char b2h(int a) {
		return (char) (a < 10 ? 48 + a : (a < 16 ? 87 + a : '!'));
	}

	/**
	 * hex to byte
	 */
	public static int h2b(char c) {
		if (c < 0x30 || c > 0x39) {
			if ((c > 64 && c < 71) || ((c = Character.toUpperCase(c)) > 64 && c < 71)) {
				return c - 55;
			}
			throw new IllegalArgumentException("Not a hex character '" + c + "'");
		}
		return c - 0x30;
	}

	public static DynByteBuf hex2bytes(CharSequence hex, DynByteBuf bl) {
		bl.ensureCapacity(bl.wIndex() + (hex.length() >> 1));

		for (int i = 0; i < hex.length(); ) {
			char c = hex.charAt(i++);
			if (c == ' ' || c == '\r' || c == '\n') continue;
			bl.put((byte) ((h2b(c) << 4) | h2b(hex.charAt(i++))));
		}
		return bl;
	}

	public static String bytes2hex(byte[] b) {
		return bytes2hex(b, 0, b.length, new CharList()).toStringAndFree();
	}

	public static CharList bytes2hex(byte[] b, int off, int len, CharList sb) {
		sb.ensureCapacity(sb.len + len << 1);
		len += off;
		char[] tmp = sb.list;
		int j = sb.len;
		while (off < len) {
			int bb = b[off++] & 0xFF;
			tmp[j++] = b2h(bb >>> 4);
			tmp[j++] = b2h(bb & 0xf);
		}
		sb.setLength(j);
		return sb;
	}

	// region 数字相关

	/**
	 * 是否为数字(simple check for unmarked(LFD/xE+y) int/long/double)
	 *
	 * @return -1 不是, 0 整数, 1 小数
	 */
	public static int isNumber(CharSequence s) { return isNumber(s, LONG_MAXS); }
	public static int isNumber(CharSequence s, byte[] max) {
		if (s == null || s.length() == 0) return -1;
		int dot = 0;
		int i = 0;
		int off = 0;

		if (s.charAt(0) == '+' || s.charAt(0) == '-') {
			if (s.length() == 1) return -1;
			else off = i = 1;
		}

		for (int len = s.length(); i < len; i++) {
			char c = s.charAt(i);
			// .n and n. are valid doubles
			if (c == 0x2E) {
				if (++dot > 1) return -1;
			} else if (c < 0x30 || c > 0x39) {
				return -1;
			}
		}

		return dot>0 || (max != null && !checkMax(max, s, off, s.charAt(0) == '-')) ? 1 : 0;
	}

	/**
	 * Implementation detail: The runtime complexity is in O(log2(n)). <br>
	 * Negative numbers has the same number of digits as the corresponding positive ones.
	 *
	 * @param n The given integer
	 *
	 * @return The number of digits of the given integer n in O(log2(n))
	 */
	public static int digitCount(int n) {
		if (n == Integer.MIN_VALUE) return 10;
		if (n < 0) return digitCount(-n)+1;

		if (n < 100000) /* 1 <= digit count <= 5 */ {
			if (n < 100) /* 1 <= digit count <= 2 */
				return (n < 10) ? 1 : 2;
			else /* 3 <= digit count <= 5 */
				if (n < 1000) return 3;
				else /* 4 <= digit count <= 5 */
					return (n < 10000) ? 4 : 5;
		} else /* 6 <= digit count <= 10 */
			if (n < 10000000) /* 6 <= digit count <= 7 */
				return (n < 1000000) ? 6 : 7;
			else /* 8 <= digit count <= 10 */
				if (n < 100000000) return 8;
				else /* 9 <= digit count <= 10 */
					return (n < 1000000000) ? 9 : 10;
	}

	// fallback
	public static int digitCount(long x) {
		if (x == Long.MIN_VALUE) return 20;
		if (x < 0) return digitCount(-x)+1;

		long p = 10;
		for (int i=1; i<19; i++) {
			if (x < p) return i;
			p = 10*p;
		}
		return 19;
	}

	public static int parseInt(CharSequence s) throws NumberFormatException {
		return parseInt(s, 10);
	}
	public static int parseInt(CharSequence s, int radix) throws NumberFormatException {
		boolean n = s.charAt(0) == '-';
		int i = parseInt(s, n ? 1 : 0, s.length(), radix);
		return n ? -i : i;
	}
	public static int parseInt(CharSequence s, int i, int end, int radix) throws NumberFormatException {
		long result = 0;

		if (end - i > 0) {
			int digit;

			while (i < end) {
				if ((digit = Character.digit(s.charAt(i++), radix)) < 0)
					throw new NumberFormatException("Not a number at offset " + (i - 1) + "(" + s.charAt(i - 1) + "): " + s.subSequence(i-1,end));

				result *= radix;
				result += digit;
			}
		} else {
			throw new NumberFormatException("Len=0: " + s);
		}

		if (result > 4294967295L || result < Integer.MIN_VALUE) throw new NumberFormatException("Value overflow " + result + " : " + s.subSequence(i,end));

		return (int) result;
	}

	public static boolean parseIntOptional(CharSequence s, int[] radixAndReturn) {
		if (s == null) return false;

		long result = 0;
		int radix = radixAndReturn[0];

		if (s.length() > 0) {
			int i = 0, len = s.length();

			int digit;

			while (i < len) {
				if ((digit = Character.digit(s.charAt(i++), radix)) < 0) return false;

				result *= radix;
				result += digit;
			}
		} else {
			return false;
		}

		if (result > 4294967295L || result < Integer.MIN_VALUE) return false;

		radixAndReturn[0] = (int) result;

		return true;
	}

	public static final byte[] INT_MAXS = new byte[] {'2', '1', '4', '7', '4', '8', '3', '6', '4', '8'};
	public static final byte[] LONG_MAXS = new byte[] {'9', '2', '2', '3', '3', '7', '2', '0', '3', '6', '8', '5', '4', '7', '7', '5', '8', '0', '8'};

	public static boolean checkMax(byte[] maxs, CharSequence s, int off, boolean negative) {
		int k = maxs.length + off;
		if (s.length() > k) return false;
		if (s.length() < k) return true;

		for (int i = off; i < k; i++) {
			if (s.charAt(i) <= maxs[i-off]) return true;
		}

		return maxs[maxs.length - 1] - s.charAt(k-1) >= (negative?0:1);
	}

	public static void pad(CharList sb, int number, int min) {
		for (int i = min - digitCount(number) - 1; i >= 0; i--) sb.append('0');
		sb.append(number);
	}
	public static void pad(CharList sb, long number, int min) {
		for (int i = min - digitCount(number) - 1; i >= 0; i--) sb.append('0');
		sb.append(number);
	}

	public static String toFixed(double d) {
		return toFixed(d, 5);
	}

	// 保留n位小数
	public static String toFixed(double d, int fract) {
		StringBuilder sb = new StringBuilder().append(d);
		int dot = sb.lastIndexOf(".");

		int ex = dot+fract+1;
		if (sb.length() < ex) {
			while (sb.length() < ex) sb.append('0');
		} else {
			sb.setLength(ex);
		}
		return sb.toString();
	}

	public static String toFixedLength(double d, int valid) {
		String v = Double.toString(d);
		if (v.length() == valid+1) return v;
		if (v.length() < valid+1) {
			StringBuilder sb = new StringBuilder(valid+1).append(v);
			while (sb.length() < valid+1) sb.append('0');
			return sb.toString();
		} else {
			return v.substring(0, valid+1);
		}
	}

	// endregion
	// region Pretty print

	public static String dumpBytes(byte[] b) {
		return dumpBytes(new StringBuilder(), b, 0, b.length).toString();
	}
	public static String dumpBytes(byte[] b, int off, int len) {
		return dumpBytes(new StringBuilder(), b, off, len).toString();
	}
	public static StringBuilder dumpBytes(StringBuilder sb, byte[] b, int off, int len) {
		if (b.length - off < len) {
			len = b.length - off;
			if (len <= 0) {
				off = 0;
				len = b.length;
			}
		}
		if (len <= 0) return sb;
		int delta = off & 15;
		if (delta != 0) {
			_off(sb, off & ~15);
			int x = delta;
			while (x-- > 0) {
				sb.append("  ");
				if ((x & 1) != 0) sb.append(' ');
			}
		} else {
			_off(sb, off);
		}
		int d = 0;
		while (true) {
			sb.append(b2h((b[off] & 0xFF) >>> 4)).append(b2h(b[off++] & 0xf));
			d++;
			if (--len == 0) {
				sb.append(" ");

				int rem = 16 - d;
				rem = (rem << 1) + (rem >> 1) + 1;
				for (int i = 0; i < rem; i++) sb.append(" ");

				off -= d;
				while (d-- > 0) {
					int j = b[off++] & 0xFF;
					sb.append(isPrintableAscii(j) ? (char) j : '.');
				}
				break;
			}
			if ((off & 1) == 0) sb.append(' ');
			if ((off & 15) == 0) {
				sb.append(" ");
				off -= 16;
				d = 0;
				for (int i = 0; i < 16; i++) {
					int j = b[off++] & 0xFF;
					sb.append(delta-- > 0 ? ' ' : isPrintableAscii(j) ? (char) j : '.');
				}
				_off(sb, off);
			}
		}

		return sb;
	}

	private static void _off(StringBuilder sb, int v) {
		sb.append("\n0x");
		String s = Integer.toHexString(v);
		for (int k = 7 - s.length(); k >= 0; k--) {
			sb.append('0');
		}
		sb.append(s).append("  ");
	}

	public static String deepToString(Object o) {
		StringBuilder sb = new StringBuilder();
		deepToString(sb, o, "");
		return sb.toString();
	}
	private static void deepToString(StringBuilder sb, Object o, CharSequence off) {
		sb.append(off);

		String off2 = off + "  ";
		try {
			if (o == null) sb.append("null");
			else if (o instanceof Iterable) {
				Iterable<?> itr = (Iterable<?>) o;
				sb.append("[").append('\n');
				for (Object o1 : itr) {
					deepToString(sb, o1, off2);
					sb.append('\n');
				}
				sb.append(']').append('\n');
			} else if (o instanceof Map) {
				Map<?, ?> map = (Map<?, ?>) o;
				sb.append("[").append('\n');
				for (Map.Entry<?, ?> entry : map.entrySet()) {
					deepToString(sb, entry.getKey(), off2);
					sb.append(" = ");
					deepToString(sb, entry.getValue(), off2);
				}
				sb.append(']').append('\n');
			} else if (o.getClass().isArray()) {
				if (o.getClass().getComponentType().isPrimitive()) {
					sb.append(Arrays.class.getDeclaredMethod("toString", o.getClass()).invoke(null, o));
				} else {
					sb.append(Arrays.deepToString((Object[]) o));
				}
			} else {
				sb.append(o);
			}
		} catch (Throwable ignored) {}
	}

	// endregion
	// region regionMatches

	public static int lastMatches(CharSequence a, int aIndex, CharSequence b, int bIndex, int max) {
		int min = Math.min(Math.min(a.length() - aIndex, b.length() - bIndex), max);
		int i = 0;
		for (; i < min; i++) {
			if (a.charAt(aIndex++) != b.charAt(bIndex++)) break;
		}

		return i;
	}

	public static boolean regionMatches(CharSequence a, int aIndex, CharSequence b, int bIndex) {
		int min = Math.min(a.length() - aIndex, b.length() - bIndex);
		for (; min > 0; min--) {
			if (a.charAt(aIndex++) != b.charAt(bIndex++)) return false;
		}

		return true;
	}

	// endregion

	public static int gNextCRLF(CharSequence in, int i) {
		while (i < in.length()) {
			char c = in.charAt(i++);
			if (c == '\r') {
				if (i >= in.length()) return -1;
				if (in.charAt(i) != '\n') continue;
				return i + 1;
			} else if (c == '\n') {
				return i;
			}
		}
		return -1;
	}

	public static int gAppendToNextCRLF(CharSequence in, final int prevI, Appendable to) {
		int i = prevI;
		try {
			while (i < in.length()) {
				char c = in.charAt(i);
				if (c == '\r') {
					if (++i == in.length()) break;
					if (in.charAt(i) != '\n') continue;

					to.append(in, prevI, i-1);
					return i+1;
				} else if (c == '\n') {
					to.append(in, prevI, i);
					return i+1;
				}
				i++;
			}
			to.append(in, prevI, i);
		} catch (IOException e) {
			Helpers.athrow(e);
		}
		return in.length();
	}

	// region indexOf

	public static int gIndexOf(CharSequence haystack, char needle) {
		for (int i = 0; i < haystack.length(); i++) {
			if (haystack.charAt(i) == needle) return i;
		}
		return -1;
	}
	public static int gIndexOf(CharSequence haystack, char needle, int i) {
		for (; i < haystack.length(); i++) {
			if (haystack.charAt(i) == needle) return i;
		}
		return -1;
	}
	public static int gIndexOf(CharSequence haystack, CharSequence needle, int i, int max) {
		char first = needle.charAt(0);
		o:
		for (; i < max; i++) {
			if (haystack.charAt(i) == first) {
				for (int j = 1; j < needle.length(); j++) {
					if (haystack.charAt(i+j) != needle.charAt(j)) {
						continue o;
					}
				}
				return i;
			}
		}

		return -1;
	}

	public static int gLastIndexOf(CharSequence haystack, CharSequence needle) {
		int i = haystack.length()-needle.length();
		return gLastIndexOf(haystack, needle, i, 0);
	}
	public static int gLastIndexOf(CharSequence haystack, CharSequence needle, int i, int min) {
		// or sub loop will throw
		if (haystack.length() - i > needle.length()) return -1;

		char first = needle.charAt(0);
		o:
		for (; i >= min; i--) {
			if (haystack.charAt(i) == first) {
				for (int j = 1; j < needle.length(); j++) {
					if (haystack.charAt(i+j) != needle.charAt(j)) {
						continue o;
					}
				}
				return i;
			}
		}

		return -1;
	}
	public static int gLastIndexOf(CharSequence haystack, char needle) {
		for (int i = haystack.length() - 1; i >= 0; i--) {
			if (haystack.charAt(i) == needle) return i;
		}
		return -1;
	}

	// endregion
	// region split

	/**
	 * @implSpec 忽略空字符
	 */
	public static String[] split1(CharSequence keys, char c) {
		List<String> list = new SimpleList<>();
		return split(list, keys, c).toArray(new String[list.size()]);
	}

	public static List<String> split(CharSequence keys, char c) {
		return split(new SimpleList<>(), keys, c);
	}

	public static List<String> split(List<String> list, CharSequence str, char splitter) {
		return split(list, str, splitter, Integer.MAX_VALUE, false);
	}

	public static List<String> split(List<String> list, CharSequence str, char splitter, int max) {
		return split(list, str, splitter, max, false);
	}

	public static List<String> split(List<String> list, CharSequence str, char splitter, int max, boolean keepEmpty) {
		int i = 0, prev = 0;
		while (i < str.length()) {
			if (splitter == str.charAt(i)) {
				if (prev < i || keepEmpty) {
					if (--max == 0) {
						list.add(str.subSequence(prev, str.length()).toString());
						return list;
					}
					list.add(prev == i ? "" : str.subSequence(prev, i).toString());
				}
				prev = i + 1;
			}
			i++;
		}

		if (max != 0 && (prev < i || keepEmpty)) {
			list.add(prev == i ? "" : str.subSequence(prev, i).toString());
		}

		return list;
	}

	public static List<String> split(CharSequence str, CharSequence splitter) {
		return split(new SimpleList<>(), str, splitter, Integer.MAX_VALUE, false);
	}

	public static List<String> splitKeepEmpty(CharSequence str, CharSequence splitter) {
		return split(new SimpleList<>(), str, splitter, Integer.MAX_VALUE, true);
	}

	public static List<String> split(List<String> list, CharSequence str, CharSequence splitter) {
		return split(list, str, splitter, Integer.MAX_VALUE, false);
	}

	public static List<String> split(List<String> list, CharSequence str, CharSequence splitter, int max, boolean keepEmpty) {
		switch (splitter.length()) {
			case 0:
				for (int i = 0; i < str.length(); i++) {
					list.add(String.valueOf(str.charAt(i)));
				}
				return list;
			case 1:
				return split(list, str, splitter.charAt(0), max, keepEmpty);
		}

		char first = splitter.charAt(0);

		int len = splitter.length();
		int i = 0, prev = 0;
		while (i < str.length()) {
			if (first == str.charAt(i) && lastMatches(str, i, splitter, 0, len) == len) {
				if (prev < i || keepEmpty) {
					if (--max == 0) {
						list.add(str.subSequence(prev, str.length()).toString());
						return list;
					}
					list.add(prev == i ? "" : str.subSequence(prev, i).toString());
				}
				i += len;
				prev = i;
			}
			i++;
		}

		i = Math.min(i, str.length());
		if (max != 0 && (prev < i || keepEmpty)) {
			list.add(prev == i ? "" : str.subSequence(prev, i).toString());
		}

		return list;
	}

	// endregion

	public static <T extends Appendable> T prettyTable(T a, Object... parm) {
		return prettyTable(a, " ", " ", " ", parm);
	}
	public static <T extends Appendable> T prettyTable(T a, String headSep, Object... parm) {
		return prettyTable(a, headSep, headSep, " ", parm);
	}
	public static <T extends Appendable> T prettyTable(T a, String headSep, String sep, String pfx, Object... parm) {
		if (headSep.length() != sep.length()) throw new IllegalArgumentException("headSep.length != sep.length");
		List<List<String>> lines = new SimpleList<>();
		List<String> val = new SimpleList<>();
		IntList len1 = new IntList();

		for (Object o : parm) {
			if (o == IntMap.UNDEFINED) {
				lines.add(val);
				val = new SimpleList<>();
				continue;
			}
			String s = String.valueOf(o);
			val.add(s);
			while (len1.size() < val.size()) len1.add(0);

			int len = len1.get(val.size()-1);
			int strlen = uiLen(s);
			if (strlen > len) len1.set(val.size()-1, strlen);
		}
		lines.add(val);

		try {
			for (int i = 0; i < lines.size(); i++) {
				a.append('\n');

				List<String> line = lines.get(i);
				if (!line.isEmpty()) {
					a.append(pfx);

					for (int j = 0; j < line.size()-1; j++) {
						String s = line.get(j);
						a.append(s);
						int k = len1.get(j)-uiLen(s);
						while (k-- > 0) a.append(' ');
						a.append(i == 0 ? headSep : sep);
					}

					String s = line.get(line.size()-1);
					a.append(s);
					if (i == lines.size()-1) break;
					int k = len1.get(line.size()-1)-uiLen(s);
					while (k-- > 0) a.append(' ');
				}
			}
		} catch (IOException e) {
			Helpers.athrow(e);
		}
		return a;
	}

	private static int uiLen(String s) {
		int len = 0;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c > 0xFF) len += 2;
			else if (c == '\t') len += 4;
			else len++;
		}
		return len;
	}

	public static boolean safeEquals(CharSequence a, CharSequence b) {
		if (a.length() != b.length()) return false;
		int r = 0;
		for (int i = a.length() - 1; i >= 0; i--) {
			r |= a.charAt(i) ^ b.charAt(i);
		}
		return r == 0;
	}

	public static String replaceAll(CharSequence str, CharSequence find, CharSequence replace) {
		return IOUtil.getSharedCharBuf().append(str).replace(find, replace).toString();
	}

	public static int codepoint(int h, int l) {
		return ((h << 10) + l) + (MIN_SUPPLEMENTARY_CODE_POINT
			- (MIN_HIGH_SURROGATE << 10)
			- MIN_LOW_SURROGATE);
	}

	public static String join(Iterable<?> split, CharSequence c) {
		Iterator<?> itr = split.iterator();
		if (!itr.hasNext()) return "";

		CharList tmp = IOUtil.ddLayeredCharBuf();
		while (true) {
			tmp.append(itr.next());
			if (!itr.hasNext()) break;
			tmp.append(c);
		}
		return tmp.toStringAndFree();
	}

	private static JPinyin pinyin;
	public static JPinyin pinyin() {
		if (pinyin == null) {
			try {
				pinyin = new JPinyin(IOUtil.readResUTF("META-INF/pinyin/char_t2s_yin.txt"),
					IOUtil.readResUTF("META-INF/pinyin/word_s2t.txt"), IOUtil.readResUTF("META-INF/pinyin/word_t2s.txt"),
					IOUtil.readResUTF("META-INF/pinyin/word_yin.txt"), -1);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return pinyin;
	}
}
