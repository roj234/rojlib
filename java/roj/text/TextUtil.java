package roj.text;

import org.jetbrains.annotations.Range;
import roj.collect.IntList;
import roj.collect.IntMap;
import roj.collect.MyBitSet;
import roj.collect.SimpleList;
import roj.config.Tokenizer;
import roj.io.IOUtil;
import roj.util.ArrayCache;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import static roj.ui.Terminal.getStringWidth;

/**
 * @author Roj234
 * @since 2021/6/19 0:14
 */
public class TextUtil {
	public static Charset DefaultOutputCharset;
	static {
		String property = System.getProperty("roj.text.outputCharset", null);
		DefaultOutputCharset = property == null ? Charset.defaultCharset()/*file.encoding*/ : Charset.forName(property);
	}

	public static final MyBitSet HEX = MyBitSet.from("0123456789ABCDEFabcdef");

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

	public final static byte[] digits = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
										 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j',
										 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't',
										 'u', 'v', 'w', 'x', 'y', 'z', 'A', 'B', 'C', 'D',
										 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N',
										 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'};

	public static String scaledNumber1024(long size) { return scaledNumber1024(IOUtil.getSharedCharBuf(), size).toString(); }
	private static final String[] SCALE = {"B", "KB", "MB", "GB", "TB", "PB", "EB"};
	public static CharList scaledNumber1024(CharList sb, long size) {
		long cap = 1;
		int i = 0;
		for (;;) {
			long next = cap << 10;
			if (next > size) break;

			cap = next;
			i++;
		}

		return sb.append(TextUtil.toFixed(size / (double) cap, i == 0 ? 0 : 2)).append(SCALE[i]);
	}
	public static double unscaledNumber1024(String seq) {
		int offset = 1;
		double multiplier = 1;

		if (seq.endsWith("B")) offset = 2;
		else if (seq.endsWith("b")) {
			offset = 2;
			multiplier = 8;
		}

		char c = seq.charAt(seq.length()-offset);
		switch (c) {
			default: throw new IllegalStateException("Unknown char "+c);
			case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9': offset--; break;

			case 'K', 'k': multiplier *= 1L<<10; break;
			case 'M', 'm': multiplier *= 1L<<20; break;
			case 'G', 'g': multiplier *= 1L<<30; break;
			case 'T', 't': multiplier *= 1L<<40; break;
			case 'P', 'p': multiplier *= 1L<<50; break;
			case 'E', 'e': multiplier *= 1L<<60; break;
		}

		return Double.parseDouble(seq.substring(0, seq.length()-offset)) * multiplier;
	}

	private static final long[] PET = {1_000,1_000_000,1_000_000_000,1_000_000_000_000L,1_000_000_000_000_000L,1_000_000_000_000_000_000L};
	public static String scaledNumber(long number) {
		if (number < 0) return "-"+scaledNumber(number == Long.MIN_VALUE ? Long.MAX_VALUE : -number);

		int i;
		if (number >= 1_000_000_000_000L) {
			if (number >= 1_000_000_000_000_000_000L) i = 5;
			else i = number >= 1_000_000_000_000_000L ? 4 : 3;
		} else if (number >= 1_000_000) {
			i = number >= 1_000_000_000 ? 2 : 1;
		} else if (number >= 1000) {
			i = 0;
		} else {
			return String.valueOf(number);
		}

		CharList sb = new CharList().append(number / PET[i]).append('.');
		int v = (int) (number % PET[i] / (PET[i] / 100));
		if (v < 10) sb.append('0');
		return sb.append(v).append("KMGTPE".charAt(i)).toStringAndFree();
	}

	public static boolean isPrintableAscii(int j) {return j > 31 && j < 127;}

	/**
	 * byte to hex
	 */
	public static char b2h(int a) { return (char) digits[a&0xF]; }

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


	public static byte[] hex2bytes(String str) { return hex2bytes(str, IOUtil.getSharedByteBuf()).toByteArray(); }
	public static DynByteBuf hex2bytes(CharSequence hex, DynByteBuf bl) {
		bl.ensureCapacity(bl.wIndex() + (hex.length() >> 1));

		for (int i = 0; i < hex.length(); ) {
			char c = hex.charAt(i++);
			if (Tokenizer.WHITESPACE.contains(c)) continue;
			bl.put((byte) ((h2b(c) << 4) | h2b(hex.charAt(i++))));
		}
		return bl;
	}

	public static String bytes2hex(byte[] b) {return bytes2hex(b, 0, b.length, new CharList()).toStringAndFree();}

	// not recommend to use!
	public static CharList bytes2hex(byte[] b, int off, int end, CharList sb) {
		sb.ensureCapacity(sb.len + (end-off) << 1);
		char[] tmp = sb.list;
		int j = sb.len;
		while (off < end) {
			int bb = b[off++];
			tmp[j++] = b2h(bb >>> 4);
			tmp[j++] = b2h(bb & 0xf);
		}
		sb.setLength(j);
		return sb;
	}

	public static int editDistance(CharSequence s1, CharSequence s2) {
		int l1 = s1.length();
		int l2 = s2.length();

		if (l1 == 0) return l2;
		if (l2 == 0) return l1;

		int[] prevDistI = ArrayCache.getIntArray(l2+1, l2+1);

		for (int i = 0; i < l1;) {
			char sourceChar = s1.charAt(i++);

			int previousDiagonal = prevDistI[0];
			int previousColumn = prevDistI[0]++;

			for (int j = 1; j <= l2; ++j) {
				int prevDistIJ = prevDistI[j];

				if (sourceChar == s2.charAt(j-1)) {
					previousColumn = previousDiagonal;
				} else {
					if (previousDiagonal < previousColumn) previousColumn = previousDiagonal;
					if (prevDistIJ < previousColumn) previousColumn = prevDistIJ;
					previousColumn++;
				}

				previousDiagonal = prevDistIJ;
				prevDistI[j] = previousColumn;
			}
		}

		int editDist = prevDistI[l2];

		ArrayCache.putArray(prevDistI);

		return editDist;
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

		if (dot > 0) return s.length() == 1 ? -1 : 1;
		return max != null && !checkMax(max, s, off, s.length(), s.charAt(0) == '-') ? 1 : 0;
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

	public static int parseInt(CharSequence s) throws NumberFormatException { return parseInt(s, 0); }
	public static int parseInt(CharSequence s, @Range(from = 0, to = 3) int radix) throws NumberFormatException {
		boolean n = s.charAt(0) == '-';
		return (int) Tokenizer.parseNumber(s, n?1:0, s.length(), radix, n);
	}
	public static int parseInt(CharSequence s, int start, int end) throws NumberFormatException {
		return (int) Tokenizer.parseNumber(s, start, end, 0, false);
	}

	public static final byte[] INT_MAXS = new byte[] {'2', '1', '4', '7', '4', '8', '3', '6', '4', '8'};
	public static final byte[] LONG_MAXS = new byte[] {'9', '2', '2', '3', '3', '7', '2', '0', '3', '6', '8', '5', '4', '7', '7', '5', '8', '0', '8'};
	public static boolean checkMax(byte[] maxs, CharSequence s, int off, int end, boolean negative) {
		//noinspection StatementWithEmptyBody
		while (s.charAt(off) == '0' && ++off < end);

		int k = maxs.length + off;
		if (end != k) return end < k;

		for (int i = off; i < k; i++) {
			if (s.charAt(i) < maxs[i-off]) return true;
		}

		return maxs[maxs.length - 1] - s.charAt(k-1) >= (negative?0:1);
	}

	public static String toFixed(double d) { return toFixed(d, 5); }
	public static String toFixed(double d, int fract) { return toFixed(new StringBuilder(), d, fract).toString(); }
	// 保留n位小数
	public static StringBuilder toFixed(StringBuilder sb, double d, int fract) {
		sb.append(d);
		int dot = sb.lastIndexOf(".");

		int ex = dot+fract+1;
		if (sb.length() < ex) {
			while (sb.length() < ex) sb.append('0');
		} else {
			if (fract == 0) ex--;
			sb.setLength(ex);
		}
		return sb;
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
	public static String dumpBytes(byte[] b) { return dumpBytes(new CharList(), b, 0, b.length).toStringAndFree(); }
	public static String dumpBytes(byte[] b, int off, int len) { return dumpBytes(new CharList(), b, off, len).toStringAndFree(); }
	public static CharList dumpBytes(CharList sb, byte[] b, int off, final int len) {
		if (len <= off) return sb;

		int prefix = Integer.toHexString(off+len-1).length();

		int rem = off & 15;
		_off(sb, off ^ rem, prefix);
		if (rem != 0) sb.padEnd(' ', (rem << 1) + (rem >> 1));

		int d = off;
		while (true) {
			int i1 = b[off++] & 0xFF;
			sb.append(b2h(i1 >>> 4)).append(b2h(i1 & 0xf));

			if (off == len) {
				rem = 16 + d - off;
				sb.padEnd(' ', (rem << 1) + (rem >> 1) + 2);

				for (int i = d; i < off; i++) {
					int j = b[i] & 0xFF;
					sb.append(isPrintableAscii(j) ? (char) j : '.');
				}
				break;
			}

			if ((off & 1) == 0) {
				sb.append(' ');

				if ((off & 15) == 0) {
					sb.append(' ');
					d = off;
					for (int i = 16; i > 0; i--) {
						int j = b[off-i] & 0xFF;
						sb.append(rem-- > 0 ? ' ' : isPrintableAscii(j) ? (char) j : '.');
					}
					_off(sb, off, prefix);
				}
			}
		}

		return sb;
	}
	private static void _off(CharList sb, int off, int prefix) {
		String offStr = Integer.toHexString(off);
		sb.append("\n0x").padEnd('0', prefix-offStr.length()).append(offStr).append("  ");
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
	// region nextCRLF
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

	public static int gAppendToNextCRLF(CharSequence in, final int prevI, Appendable to) { return gAppendToNextCRLF(in, prevI, to, 0); }
	public static int gAppendToNextCRLF(CharSequence in, int prevI, Appendable to, int def) {
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
		// well, in.length may vary over time (I mean TextReader)
		return def == 0 ? in.length() : def;
	}
	// endregion
	// region indexOf / lastIndexOf
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
	public static int gLastIndexOf(CharSequence haystack, char needle) {
		for (int i = haystack.length() - 1; i >= 0; i--) {
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
	// endregion
	// region split

	/**
	 * @implSpec 忽略空字符
	 */
	public static String[] split1(CharSequence keys, char c) {
		List<String> list = new SimpleList<>();
		return split(list, keys, c).toArray(new String[list.size()]);
	}

	public static List<String> split(CharSequence keys, char c) { return split(new SimpleList<>(), keys, c); }
	public static List<String> split(List<String> list, CharSequence str, char splitter) { return split(list, str, splitter, Integer.MAX_VALUE); }
	/**
	 * 策略：不保留最后的连续空行
	 * 比如: a||b|| => ["a", "", "b"]
	 */
	public static List<String> split(List<String> list, CharSequence str, char splitter, int max) {
		int i = 0, prev = 0, lastNonEmpty = max;
		while (i < str.length()) {
			if (splitter == str.charAt(i)) {
				if (--max == 0) i = str.length();

				if (prev < i) {
					list.add(str.subSequence(prev, i).toString());
					lastNonEmpty = max;
				} else {
					list.add("");
				}

				prev = ++i;
			} else {
				i++;
			}
		}

		if (prev < i) list.add(str.subSequence(prev, str.length()).toString());
		else {
			lastNonEmpty -= max;
			while (lastNonEmpty-- > 0)
				list.remove(list.size()-1);
		}

		return list;
	}

	public static List<String> split(CharSequence str, CharSequence splitter) { return split(new SimpleList<>(), str, splitter, Integer.MAX_VALUE); }
	public static List<String> split(List<String> list, CharSequence str, CharSequence splitter) { return split(list, str, splitter, Integer.MAX_VALUE); }
	public static List<String> split(List<String> list, CharSequence str, CharSequence splitter, int max) {
		switch (splitter.length()) {
			case 1: return split(list, str, splitter.charAt(0), max);
			case 0:
				for (int i = 0; i < str.length(); i++) {
					list.add(String.valueOf(str.charAt(i)));
				}
				return list;
		}

		char first = splitter.charAt(0);

		int len = splitter.length();
		int i = 0, prev = 0, lastNonEmpty = max;
		while (i < str.length()) {
			if (first == str.charAt(i) && lastMatches(str, i, splitter, 0, len) == len) {
				if (--max == 0) i = str.length();

				if (prev < i) {
					list.add(str.subSequence(prev, i).toString());
					lastNonEmpty = max;
				} else {
					list.add("");
				}

				i += len;
				prev = i;
			} else {
				i++;
			}
		}

		if (prev < i && prev < str.length()) list.add(str.subSequence(prev, str.length()).toString());
		else {
			lastNonEmpty -= max;
			while (lastNonEmpty-- > 0)
				list.remove(list.size()-1);
		}

		return list;
	}
	// endregion

	public static <T extends Appendable> T prettyTable(T sb, String linePrefix, Object data, String... separators) {
		List<Object[]> table = new SimpleList<>();
		List<String> row = new SimpleList<>();
		List<List<String>> multiLineRef = new SimpleList<>();
		IntList maxLens = new IntList();

		List<Object> myList = data instanceof List ? Helpers.cast(data) : SimpleList.asModifiableList((Object[]) data);
		for (Object o : myList) {
			if (o == IntMap.UNDEFINED) {
				table.add(row.toArray());
				row.clear();

				for (int i = 0; i < multiLineRef.size(); i++) {
					table.add(multiLineRef.get(i).toArray());
				}
				multiLineRef.clear();
				continue;
			}

			String s = String.valueOf(o);
			int sLen;
			if (s.indexOf('\n') >= 0) {
				List<String> _sLines = LineReader.toLines(String.valueOf(o), false);
				row.add(s = _sLines.get(0));
				sLen = getStringWidth(s);

				while (multiLineRef.size() < _sLines.size()-1) multiLineRef.add(new SimpleList<>());
				for (int i = 1; i < _sLines.size(); i++) {
					List<String> line = multiLineRef.get(i-1);
					while (line.size() < row.size()) line.add("");
					String str = _sLines.get(i);
					line.set(row.size()-1, str);
					sLen = Math.max(sLen, getStringWidth(str));
				}
			} else {
				row.add(s);
				sLen = getStringWidth(s);
			}

			while (maxLens.size() < row.size()) maxLens.add(0);

			int len = maxLens.get(row.size()-1);
			if (sLen > len) maxLens.set(row.size()-1, sLen);
		}

		table.add(row.toArray());
		for (int i = 0; i < multiLineRef.size(); i++) {
			table.add(multiLineRef.get(i).toArray());
		}
		multiLineRef.clear();

		try {
			for (int i = 0; i < table.size(); i++) {
				sb.append('\n');

				Object[] line = table.get(i);
				if (line.length > 0) sb.append(linePrefix);

				for (int j = 0; j < line.length;) {
					String s = line[j].toString();
					sb.append(s);

					int myMaxLen = maxLens.get(j);
					if (++j == line.length) break;

					if (myMaxLen < 100) {
						int k = myMaxLen - getStringWidth(s);
						while (k-- > 0) sb.append(' ');
					}

					sb.append(separators.length == 0 ? " " : separators[j > separators.length ? separators.length-1 : j-1]);
				}
			}
		} catch (IOException e) {
			Helpers.athrow(e);
		}
		return sb;
	}

	public static String join(Iterable<?> split, CharSequence c) {
		Iterator<?> itr = split.iterator();
		if (!itr.hasNext()) return "";

		CharList tmp = new CharList();
		while (true) {
			tmp.append(itr.next());
			if (!itr.hasNext()) break;
			tmp.append(c);
		}
		return tmp.toStringAndFree();
	}
	public static CharList join(Iterable<?> split, CharSequence c, CharList sb) {
		Iterator<?> itr = split.iterator();
		if (itr.hasNext()) for(;;) {
			sb.append(itr.next());
			if (!itr.hasNext()) break;
			sb.append(c);
		}
		return sb;
	}

	public static String substr(String s, int off) {return substr(s, 0, off);}
	public static String substr(String s, int begin, int end) {
		if (end < 0) end = s.length() + end;
		if (begin < 0) begin = s.length() + begin;
		if (begin > end) {
			int tmp = begin;
			begin = end;
			end = tmp;
		}
		if (begin < 0 || end > s.length()) return "";
		return s.substring(begin, end);
	}
}