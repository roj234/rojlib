package roj.text;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;
import org.jetbrains.annotations.Unmodifiable;
import roj.RojLib;
import roj.collect.ArrayList;
import roj.collect.BitSet;
import roj.collect.IntList;
import roj.collect.IntMap;
import roj.compiler.plugins.annotations.Attach;
import roj.io.IOUtil;
import roj.reflect.Unsafe;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.Console;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import static roj.ui.Tty.getStringWidth;

/**
 * @author Roj234
 * @since 2021/6/19 0:14
 */
public class TextUtil {
	@NotNull public static Charset outputCharset;
	@NotNull public static Charset consoleCharset;
	public static boolean consoleAbsent;
	static {
		String property = System.getProperty("roj.text.outputCharset");
		outputCharset = property == null ? StandardCharsets.UTF_8 : Charset.forName(property);

		var encoding = System.getProperty("stdout.encoding");
		if (encoding == null) encoding = System.getProperty("sun.stdout.encoding");
		if (encoding == null) {
			Console console = System.console();
			if (console != null) RojLib.debug("TextUtil", "正在使用fallback方案获取控制台编码");
			else consoleAbsent = true;
			consoleCharset = console == null ? Charset.defaultCharset() : console.charset();
		} else {
			consoleCharset = Charset.forName(encoding);
		}
	}

	@Deprecated
	public static final BitSet HEX = BitSet.from("0123456789ABCDEFabcdef");

	@Unmodifiable
	public final static byte[] DIGITS = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
										 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j',
										 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't',
										 'u', 'v', 'w', 'x', 'y', 'z', 'A', 'B', 'C', 'D',
										 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N',
										 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'};

	public static String scaledNumber1024(long size) {return scaledNumber1024(IOUtil.getSharedCharBuf(), size).toString();}
	public static CharList scaledNumber1024(CharList sb, long size) {
		if (size < 1024) return sb.append(size).append('B');

		long cap = 1024;
		int i = 1;
		for (;i < 6;i++) {
			long next = cap << 10;
			if (next > size) break;

			cap = next;
		}

		return sb.append(TextUtil.toFixed(size / (double) cap, 2)).append(" KMGTPE".charAt(i)).append('B');
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

		// 这个性能真的会比循环好吗？
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
	public static char b2h(int a) { return (char) DIGITS[a&0xF]; }

	/**
	 * hex to byte
	 */
	public static int h2b(char c) {
		if (c < '0' || c > '9') {
			if ((c >= 'A' && c <= 'F') || ((c = Character.toUpperCase(c)) >= 'A' && c <= 'F')) {
				return c - 55;
			}
			throw new IllegalArgumentException("Not a hex character '"+c+"'");
		}
		return c - '0';
	}

	public static byte[] hex2bytes(String str) { return hex2bytes(str, IOUtil.getSharedByteBuf()).toByteArray(); }
	public static DynByteBuf hex2bytes(CharSequence hex, DynByteBuf bytes) {
		bytes.ensureCapacity(bytes.wIndex() + (hex.length() >> 1));

		for (int i = 0; i < hex.length(); ) {
			char c = hex.charAt(i++);
			if (Tokenizer.WHITESPACE.contains(c) || c == ':') continue;
			bytes.put((h2b(c) << 4) | h2b(hex.charAt(i++)));
		}
		return bytes;
	}

	@Attach("hex")
	public static String bytes2hex(byte[] b) {return bytes2hex(b, 0, b.length, new CharList()).toStringAndFree();}
	public static CharList bytes2hex(byte[] b, CharList sb) {return bytes2hex(b, 0, b.length, sb);}
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

	// 20250728 改进版本，考虑字符交换
	public static int editDistance(CharSequence s1, CharSequence s2) {
		int m = s1.length();
		int n = s2.length();

		if (m == 0) return n;
		if (n == 0) return m;

		// 初始化三个数组：dp0（上上行），dp1（上行），dp2（当前行）
		int[] dp0 = new int[n + 1];  // 对应 i-2
		int[] dp1 = new int[n + 1];  // 对应 i-1

		// 初始化第0行（i=0）
		for (int j = 0; j <= n; j++) {
			dp0[j] = j;
		}

		// 计算第1行（i=1）
		dp1[0] = 1; // i=1, j=0
		for (int j = 1; j <= n; j++) {
			if (s1.charAt(0) == s2.charAt(j - 1)) {
				dp1[j] = dp0[j - 1]; // 字符相等，取左上角值
			} else {
				// 取上方、左方、左上角的最小值加1
				dp1[j] = 1 + Math.min(Math.min(dp0[j], dp1[j - 1]), dp0[j - 1]);
			}
		}

		if (m == 1) return dp1[n]; // 如果只有一行，直接返回结果

		int[] dp2 = new int[n + 1];  // 对应 i
		// 从第2行开始计算（i >= 2）
		for (int i = 2; i <= m; i++) {
			dp2[0] = i; // 当前行首列（j=0）值为i
			for (int j = 1; j <= n; j++) {
				char c1 = s1.charAt(i - 1);
				char c2 = s2.charAt(j - 1);

				// 标准编辑操作
				if (c1 == c2) {
					dp2[j] = dp1[j - 1]; // 字符相等，取左上角值
				} else {
					// 插入、删除、替换的最小值加1
					dp2[j] = 1 + Math.min(Math.min(dp1[j], dp2[j - 1]), dp1[j - 1]);
				}

				// 检查相邻字符交换操作（需满足 i>=2 且 j>=2）
				if (i >= 2 && j >= 2) {
					char c1Prev = s1.charAt(i - 2);
					char c2Prev = s2.charAt(j - 2);
					if (c1 == c2Prev && c1Prev == c2) {
						// 交换操作：取上上行 j-2 位置的值加1
						dp2[j] = Math.min(dp2[j], dp0[j - 2] + 1);
					}
				}
			}

			// 滚动数组：为下一行准备
			int[] temp = dp0;
			dp0 = dp1; // 上上行更新为原上行
			dp1 = dp2; // 上行更新为原当前行
			dp2 = temp; // 当前行复用原上上行的数组
		}

		return dp1[n]; // 最终结果在 dp1（即最后一行）
	}

	public static double weightedContiguousSimilarity(CharSequence s1, CharSequence s2) {
		int[][] dp = new int[s1.length() + 1][s2.length() + 1];
		int maxLen = 0;  // 最长连续匹配长度
		int totalMatches = 0; // 总匹配字符数

		// 计算最长公共子串
		for (int i = 1; i <= s1.length(); i++) {
			for (int j = 1; j <= s2.length(); j++) {
				if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
					dp[i][j] = dp[i - 1][j - 1] + 1;
					if (dp[i][j] > maxLen) maxLen = dp[i][j];
					totalMatches++;
				} else {
					dp[i][j] = 0;
				}
			}
		}

		// 加权计算：给予长连续匹配更高权重
		int maxLength = Math.max(s1.length(), s2.length());
		double contiguousWeight = (double) maxLen / maxLength;  // 最长连续匹配占比
		double totalMatchRatio = (double) totalMatches / (s1.length() * s2.length());

		// 最终相似度 = (连续匹配权重*2 + 总匹配度)/3
		return (contiguousWeight * 2 + totalMatchRatio);
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
			if (s.charAt(i) > maxs[i-off]) return false;
		}

		return maxs[maxs.length - 1] - s.charAt(k-1) >= (negative?0:1);
	}

	public static String toFixed(double d) { return toFixed(d, 5); }
	public static String toFixed(double d, int fract) { return toFixed(new CharList(), d, fract).toStringAndFree(); }
	// 保留n位小数
	public static CharList toFixed(CharList sb, double d, int fract) {
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
	public static int indexOf(CharSequence haystack, char needle) {
		for (int i = 0; i < haystack.length(); i++) {
			if (haystack.charAt(i) == needle) return i;
		}
		return -1;
	}
	public static int indexOf(CharSequence haystack, char needle, int i) {
		for (; i < haystack.length(); i++) {
			if (haystack.charAt(i) == needle) return i;
		}
		return -1;
	}
	public static int lastIndexOf(CharSequence haystack, char needle) {
		for (int i = haystack.length() - 1; i >= 0; i--) {
			if (haystack.charAt(i) == needle) return i;
		}
		return -1;
	}

	public static int indexOf(CharSequence haystack, CharSequence needle, int i, int max) {
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
	public static int lastIndexOf(CharSequence haystack, CharSequence needle, int i, int min) {
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
		List<String> list = new ArrayList<>();
		return split(list, keys, c).toArray(new String[list.size()]);
	}

	public static List<String> split(CharSequence keys, char c) { return split(new ArrayList<>(), keys, c); }
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

	public static List<String> split(CharSequence str, CharSequence splitter) { return split(new ArrayList<>(), str, splitter, Integer.MAX_VALUE); }
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
		List<Object[]> table = new ArrayList<>();
		List<String> row = new ArrayList<>();
		List<List<String>> multiLineRef = new ArrayList<>();
		IntList maxLens = new IntList();

		List<Object> myList = data instanceof List ? Helpers.cast(data) : ArrayList.asModifiableList((Object[]) data);
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
				List<String> _sLines = LineReader.getAllLines(String.valueOf(o), false);
				row.add(s = _sLines.get(0));
				sLen = getStringWidth(s);

				while (multiLineRef.size() < _sLines.size()-1) multiLineRef.add(new ArrayList<>());
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

	@Attach
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

	private static long CODER_OFFSET = -1;
	@Attach
	public static boolean isLatin1(String s) {
		long offset = CODER_OFFSET;
		if (offset < 0) {
			try {
				offset = Unsafe.fieldOffset(String.class, "coder");
			} catch (Exception e) {
				offset = 0;
			}
			CODER_OFFSET = offset;
		}
		return offset != 0 ? Unsafe.U.getByte(Objects.requireNonNull(s), offset) == 0 : legacyIsLatin1(s);
	}
	private static boolean legacyIsLatin1(String s) {
		for (int i = 0; i < s.length(); i++) {
			if (s.charAt(i) > 255) return false;
		}
		return true;
	}
}