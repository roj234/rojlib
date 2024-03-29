package roj.text;

import roj.collect.*;
import roj.config.word.ITokenizer;
import roj.config.word.Tokenizer;
import roj.io.IOUtil;
import roj.util.ArrayCache;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;

import static java.lang.Character.*;
import static roj.ui.CLIUtil.getStringWidth;

/**
 * @author Roj234
 * @since 2021/6/19 0:14
 */
public class TextUtil {
	public static Charset DefaultOutputCharset;
	static {
		String property = System.getProperty("roj.text.outputCharset", null);
		DefaultOutputCharset = property == null ? Charset.defaultCharset() : Charset.forName(property);
	}

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
	 */
	public static boolean isChinese(int c) {
		TrieEntry node = JPinyin.getPinyinWords().getRoot();
		if (Character.isSupplementaryCodePoint(c)) {
			node = node.getChild(Character.highSurrogate(c));
			if (node == null) return false;
			node = node.getChild(Character.lowSurrogate(c));
		} else {
			node = node.getChild((char) c);
		}
		return node != null && node.isLeaf();
	}

	public static String scaledNumber1024(double size) { return scaledNumber1024(IOUtil.getSharedCharBuf(), size).toString(); }
	private static final String[] SCALE = {"B", "KB", "MB", "GB", "TB"};
	public static CharList scaledNumber1024(CharList sb, double size) {
		int i = 0;
		while (size >= 1024) {
			size /= 1024;
			i++;
		}

		return sb.append(TextUtil.toFixed(size, i == 0 ? 0 : 2)).append(SCALE[i]);
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
			default: return Double.parseDouble(seq.substring(0, seq.length()-offset+1));

			case 'K': case 'k': multiplier *= 1024; break;
			case 'M': case 'm': multiplier *= 1024 * 1024; break;
			case 'G': case 'g': multiplier *= 1024 * 1024 * 1024; break;
			case 'T': case 't': multiplier *= 1024L * 1024 * 1024 * 1024; break;
		}
		return Double.parseDouble(seq.substring(0, seq.length()-offset)) * multiplier;
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
	 * char to (ascii) number it represents
	 */
	public static int c2i(char c) {
		if (c < 0x30 || c > 0x39) return -1;
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
			if (Tokenizer.WHITESPACE.contains(c)) continue;
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

	public static int editDistance(CharSequence s1, CharSequence s2) {
		int l1 = s1.length();
		int l2 = s2.length();

		if (l1 == 0) return l2;
		if (l2 == 0) return l1;

		int[] prevDistI = ArrayCache.getIntArray(l2+1, 0);

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
	//region WIP
	//todo
	public static final class Diff {
		public static final byte SAME = 0, CHANGE = 1, INSERT = 2, DELETE = 3;
		public final byte type;
		public final int leftOff, rightOff, len;
		public int advance;

		public static Diff link(Diff a, Diff next) {
			a.next = next;
			next.prev = a;
			return next;
		}

		private Diff(byte type, int leftOff, int rightOff, int len) {
			this.type = type;
			this.leftOff = leftOff;
			this.rightOff = rightOff;
			this.len = len;
		}

		public static Diff same(int leftOff, int rightOff, int len) { return new Diff(SAME, leftOff, rightOff, len); }
		public static Diff change(int leftOff, int rightOff, int len) { return new Diff(CHANGE, leftOff, rightOff, len); }
		public static Diff insert(int rightOff, int len) { return new Diff(INSERT, -1, rightOff, len); }
		public static Diff delete(int leftOff, int len) { return new Diff(DELETE, leftOff, -1, len); }

		Diff prev, next;
	}

	public List<Diff> getDiff(byte[] right) {
		Diff head = Diff.insert(0,0), tail = head;



		return toRealDiff(right, head.next);
	}
	private List<Diff> toRealDiff(byte[] right, Diff in) {
		// todo merge nearby diff and insert SAME diff
		SimpleList<Diff> list = new SimpleList<>();

		return list;
	}
	public void toMarkdown(byte[] left, byte[] right, List<Diff> diffs, Appender sb) throws IOException {
		Charset cs = Charset.forName("GB18030");

		System.out.println(diffs.size());
		long l = 0;
		for (Diff diff : diffs) {
			l += diff.len;
		}
		System.out.println(TextUtil.scaledNumber(l)+"B");

		ByteList buf1 = new ByteList(), buf2 = new ByteList();
		int type = Diff.SAME;
		for (Diff diff : diffs) {
			if (diff.type != type) {
				finishBlock(sb, buf1, buf2, type, cs);
				type = diff.type;
			}

			switch (diff.type) {
				default: buf1.put(left, diff.leftOff, diff.len); break;
				case Diff.CHANGE:
					buf1.put(left, diff.leftOff, diff.len);
					buf2.put(right, diff.rightOff, diff.len);
					break;
				case Diff.INSERT: buf1.put(right, diff.rightOff, diff.len); break;
			}
		}

		finishBlock(sb, buf1, buf2, type, cs);
	}
	private static void finishBlock(Appender sb, ByteList buf1, ByteList buf2, int type, Charset cs) throws IOException {
		switch (type) {
			default: case Diff.SAME: sb.append(new String(buf1.list, 0, buf1.length(), cs)); break;
			case Diff.CHANGE: sb.append("<i title=\"").append(ITokenizer.addSlashes(new String(buf1.list, 0, buf1.length(), cs))).append("\">")
								.append(new String(buf2.list, 0, buf2.length(), cs)).append("</i>"); break;
			case Diff.INSERT: sb.append("<b>").append(new String(buf1.list, 0, buf1.length(), cs)).append("</b>"); break;
			case Diff.DELETE: sb.append("<del>").append(new String(buf1.list, 0, buf1.length(), cs)).append("</del>"); break;
		}
		buf1.clear();
		buf2.clear();
	}
	// endregion

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
		long result = parseLong(s,i,end,radix);
		if (result > 4294967295L || result < Integer.MIN_VALUE) throw new NumberFormatException("Value overflow " + result + " : " + s.subSequence(i,end));
		return (int) result;
	}
	public static long parseLong(CharSequence s, int i, int end, int radix) throws NumberFormatException {
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

		return result;
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
			if (fract == 0) ex--;
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

	public static <T extends Appendable> T prettyTable(T sb, String linePrefix, Object data, String... separators) {
		List<Object[]> table = new SimpleList<>();
		List<String> row = new SimpleList<>();
		List<List<String>> multiLineRef = new SimpleList<>();
		IntList maxLens = new IntList();

		Object _EMPTY = new String();

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
				List<String> _sLines = LineReader.slrParserV2(String.valueOf(o), false);
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
					if (myMaxLen < 100) {
						int k = myMaxLen - getStringWidth(s);
						while (k-- > 0) sb.append(' ');
					}

					if (++j == line.length) break;
					sb.append(separators.length == 0 ? " " : separators[j > separators.length ? separators.length-1 : j-1]);
				}
			}
		} catch (IOException e) {
			Helpers.athrow(e);
		}
		return sb;
	}

	public static int codepoint(int h, int l) {
		if (l < MIN_LOW_SURROGATE || l >= (MAX_LOW_SURROGATE + 1)) throw new IllegalStateException("invalid surrogate pair "+h+","+l);
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
	public static JPinyin pinyin() { return pinyin == null ? pinyin = new JPinyin() : pinyin; }
}
