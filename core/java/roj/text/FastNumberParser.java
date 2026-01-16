package roj.text;

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.Range;
import roj.annotation.MayMutate;
import roj.ci.annotation.Public;
import roj.reflect.Bypass;
import roj.reflect.Telescope;

/**
 * 提供高效解析浮点数，和支持解析substring到数字的方法。
 * @implNote parseInt/parseLong 没有比标准库快很多(也就是+30%-+100%)，它们支持解析substring而且没有GC开销，这才是更重要的
 * @implNote parseFloat/parseDouble 似乎并没有更快
 * @author Roj234-N
 * @since 2025/5/11 7:51
 */
public class FastNumberParser {
	/**
	 * Checks if the string content in {@code digits} represents mathematically zero.
	 * Supports decimal, hexadecimal (with 'p' exponent), and considers only zeros and dots as valid.
	 *
	 * @param digits the character list containing the number string
	 * @return {@code true} if the content is zero, {@code false} otherwise
	 */
	public static boolean isZeroValue(CharList digits) {
		if (digits.startsWith("0x")) {
			for (int i = 2; i < digits.length(); i++) {
				char c = digits.list[i];
				if (c == 'p' || c == 'P') return true;
				if (c != '0' && c != '.') return false;
			}
		} else {
			for (int i = 0; i < digits.length(); i++) {
				char c = digits.list[i];
				if (c == 'e' || c == 'E') return true;
				if (c != '0' && c != '.') return false;
			}
		}

		return true;
	}

	//region 整数部分
	public static final byte[] INT_MAXS = new byte[] {'2', '1', '4', '7', '4', '8', '3', '6', '4', '8'};
	public static final byte[] LONG_MAXS = new byte[] {'9', '2', '2', '3', '3', '7', '2', '0', '3', '6', '8', '5', '4', '7', '7', '5', '8', '0', '8'};

	public static final int NUM_DEC = 0, NUM_HEX = 1, NUM_BIN = 2, NUM_OCT = 3, NUM_LONG = 4;
	public static final byte[][] RADIX_MAX = new byte[8][];
	public static final byte[] RADIX = {10, 16, 2, 8};

	static {
		// n mean see digitReader()
		// RM[n] => int max
		// RM[n|LONG] => long max

		RADIX_MAX[0] = INT_MAXS;
		RADIX_MAX[4] = LONG_MAXS;

		RADIX_MAX[NUM_HEX] = new byte[8];
		RADIX_MAX[NUM_HEX|4] = new byte[16];
		fill(NUM_HEX, 'f');

		RADIX_MAX[NUM_OCT] = new byte[16];
		RADIX_MAX[NUM_OCT|4] = new byte[32];
		fill(NUM_OCT, '7');

		RADIX_MAX[NUM_BIN] = new byte[32];
		RADIX_MAX[NUM_BIN|4] = new byte[64];
		fill(NUM_BIN, '1');
	}
	private static void fill(int off, char num) {
		fill(RADIX_MAX[off], num);
		fill(RADIX_MAX[off|4], num);
	}
	private static void fill(byte[] a0, char num) {
		int i = a0.length-1;
		// +1是因为他们是(be treated as)unsigned的
		a0[i] = (byte) (num+1);
		while (--i >= 0) {
			a0[i] = (byte) num;
		}
	}

	/**
	 * Checks if the number represented by the substring {@code str[off:end)} does not exceed the maximum value defined by {@code maxs}.
	 * Skips leading zeros and handles negative numbers specially for the last digit.
	 *
	 * @param maxs the maximum digit array (e.g. INT_MAXS or LONG_MAXS)
	 * @return {@code true} if within bounds, {@code false} if overflow
	 */
	public static boolean checkMax(byte[] maxs, CharSequence str, int off, int end, boolean isNegative) {
		//noinspection StatementWithEmptyBody
		while (str.charAt(off) == '0' && ++off < end);

		int k = maxs.length + off;
		if (end != k) return end < k;

		for (int i = off; i < k; i++) {
			int cmp = str.charAt(i) - maxs[i-off];
			if (cmp != 0) return cmp < 0;
		}

		return str.charAt(k-1) - maxs[maxs.length-1] < (isNegative?0:1);
	}

	public static int parseInt(CharSequence str) {return parseInt(str, NUM_DEC);}
	public static int parseInt(CharSequence str, @MagicConstant(intValues = {NUM_DEC, NUM_HEX, NUM_BIN, NUM_OCT}) int radix) {
		char c = str.charAt(0);
		int off = 0;
		boolean isNegative = false;
		if (c < '0') {
			off = 1;
			if (c == '-') {
				isNegative = true;
			} else if (c != '+') {
				throw new NumberFormatException("Expected digit");
			}
		}
		return (int) parseLong(str, off, str.length(), radix, isNegative);
	}
	public static int parseInt(CharSequence str, @MagicConstant(intValues = {NUM_DEC, NUM_HEX, NUM_BIN, NUM_OCT}) int radixId, boolean isNegative) {return (int) parseLong(str, 0, str.length(), radixId, isNegative);}
	public static int parseInt(CharSequence str, int off, int len) {return (int) parseLong(str, off, len, NUM_DEC, false);}
	public static int parseInt(CharSequence str, int off, int len, @MagicConstant(intValues = {NUM_DEC, NUM_HEX, NUM_BIN, NUM_OCT}) int radixId) {return (int) parseLong(str, off, len, radixId, false);}

	public static long parseLong(CharSequence str) {return parseLong(str, NUM_DEC|NUM_LONG);}
	public static long parseLong(CharSequence str, @Range(from = 0, to = 7, enforce = false) int radix) {
		char c = str.charAt(0);
		int off = 0;
		boolean isNegative = false;
		if (c < '0') {
			off = 1;
			if (c == '-') {
				isNegative = true;
			} else if (c != '+') {
				throw new NumberFormatException("Expected digit");
			}
		}
		return parseLong(str, off, str.length(), radix, isNegative);
	}
	public static long parseLong(CharSequence str, @Range(from = 0, to = 7, enforce = false) int radixId, boolean isNegative) {return parseLong(str, 0, str.length(), radixId, isNegative);}
	public static long parseLong(CharSequence str, int off, int len) {return parseLong(str, off, len, NUM_DEC|NUM_LONG, false);}
	public static long parseLong(CharSequence str, int off, int len, @Range(from = 0, to = 7, enforce = false) int radixId) {return parseLong(str, off, len, radixId, false);}
	public static long parseLong(CharSequence str, int off, int len, @Range(from = 0, to = 7, enforce = false) int radixId, boolean isNegative) throws NumberFormatException {
		// range check done
		if (!checkMax(RADIX_MAX[radixId], str, off, len, isNegative))
			throw new NumberFormatException("lexer.number.overflow:"+str);

		int radix = RADIX[radixId&3];
		long v = 0;
		while (off < len) {
			v *= radix;
			v -= TextUtil.h2b(str.charAt(off++));
		}
		return isNegative ? v : -v;
	}

	public static long parseLongRaw(CharList digits, @Range(from = 0, to = 7, enforce = false) int radixId, boolean isNegative) {return parseLongRaw(digits, 0, digits.len, radixId, isNegative);}
	/**
	 * Parses the substring of digits as a raw unsigned long (no overflow check).
	 * @throws NumberFormatException if any character in digits[off..off+len] is not valid hex char
	 * @return the parsed long value
	 */
	public static long parseLongRaw(CharList digits, int off, int len, @Range(from = 0, to = 7, enforce = false) int radixId, boolean isNegative) {
		int radix = RADIX[radixId&3];
		long v = 0;
		while (off < len) {
			v *= radix;
			v -= TextUtil.h2b(digits.charAt(off++));
		}
		return isNegative ? v : -v;
	}
	//endregion
	//region 浮点数
	/**
	 * Parses the mutable character list as a float.
	 * Handles decimal and hex formats. Mutates {@code digits} by removing decimal point and exponent.
	 * Falls back to JDK's {@code FloatingDecimal} for complex cases.
	 *
	 * @param digits the digits (mutated in-place)
	 * @return the parsed float value
	 */
	public static float parseFloat(@MayMutate CharList digits) {
		if (digits.startsWith("0x")) return Float.parseFloat(digits.toString());
		var tl = PARSER.get();

		int exp = 0;
		int fractionalIndex = 1;
		for (int i = 0; i < digits.len; i++) {
			if (digits.list[i] == '.') {
				// 删掉小数点
				digits.delete(i);
				fractionalIndex = i;
			} else if (digits.list[i] == 'e') {
				digits.setLength(i);
				try {
					exp = parseInt(digits, i + 1, digits.len);
				} catch (NumberFormatException e) {
					boolean zero = i == 1 && digits.list[0] == '0';
					return zero ? 0.0f : Float.POSITIVE_INFINITY;
				}
				break;
			}
		}
		exp += fractionalIndex;

		if (digits.length() <= 7 && Math.abs(exp) < SINGLE_SMALL_10_POW.length) {
			int roundUp = 0;

			int totalLength = digits.length();
			/*if (totalLength > 8) {
				// 截断并进行四舍五入
				roundUp = roundUpToNearestEven(digits, 8);
				totalLength = 8;
			}*/
			int adjustedExp = exp - totalLength;

			float significantNumber = (int)parseLongRaw(digits, 0, totalLength, 0, false) + roundUp;
			if (adjustedExp == 0) return significantNumber;
			if (adjustedExp < -45) return 0.0f;
			if (adjustedExp > 38) return Float.POSITIVE_INFINITY;

			if (Math.abs(adjustedExp) < SINGLE_SMALL_10_POW.length) {
				return adjustedExp > 0 ? significantNumber * SINGLE_SMALL_10_POW[adjustedExp] : significantNumber / SINGLE_SMALL_10_POW[-adjustedExp];
			}
			//pow(significantNumber, Math.abs(adjustedExp), adjustedExp < 0 ? NEGATIVE_POW_D : POSITIVE_POW_D);
		}

		var parser = tl.parser;
		H.INSTANCE.decExponent(parser, exp);
		H.INSTANCE.digits(parser, digits.list);
		H.INSTANCE.nDigits(parser, digits.len);
		return H.INSTANCE.floatValue(parser);
	}

	/**
	 * Parses the mutable character list as a double.
	 * Handles decimal and hex formats. Mutates {@code digits} by removing decimal point and exponent.
	 * Uses fast path for small exponents and JDK fallback otherwise.
	 *
	 * @param digits the digits (mutated in-place)
	 * @return the parsed double value
	 */
	public static double parseDouble(@MayMutate CharList digits) {
		if (digits.startsWith("0x")) return Double.parseDouble(digits.toString());

		var tl = PARSER.get();

		/**
		 * 十进制指数部分（如"1.2e3"中的3）
		 */
		int exp = 0;
		/**
		 * 原始小数点位置索引。例如"1.20345"中为1，若无小数点则为digits长度
		 */
		int fractionalIndex = 1;

		for (int i = 0; i < digits.len; i++) {
			if (digits.list[i] == '.') {
				// 删掉小数点
				digits.delete(i);
				fractionalIndex = i;
			} else if (digits.list[i] == 'e') {
				try {
					int n = i+1;
					char next = digits.list[n];
					if (next == '-' || next == '+') n++;

					exp = (int) parseLong(digits, n, digits.len, 0, next == '-');
				} catch (NumberFormatException e) {
					boolean zero = i == 1 && digits.list[0] == '0';
					return zero ? 0.0 : Double.POSITIVE_INFINITY;
				}
				digits.setLength(i);
				break;
			}
		}

		exp += fractionalIndex;

		if (digits.length() <= 15 && Math.abs(exp) < SMALL_10_POW.length) {
			int roundUp = 0;
			int totalLength = digits.length();
			// 截断并舍入
			/*if (totalLength > 17) {
				roundUp = roundUpToNearestEven(digits, 17);
				totalLength = 17;
			}*/
			int adjustedExp = exp - totalLength;

			double significantNumber = parseLongRaw(digits, 0, totalLength, 0, false) + roundUp;

			if (adjustedExp == 0) return significantNumber;
			if (adjustedExp < -324) return 0.0;
			if (adjustedExp > 308) return Double.POSITIVE_INFINITY;

			if (Math.abs(adjustedExp) < SMALL_10_POW.length) {
				return adjustedExp > 0 ? significantNumber * SMALL_10_POW[adjustedExp] : significantNumber / SMALL_10_POW[-adjustedExp];
			}
			//pow(significantNumber, Math.abs(adjustedExp), adjustedExp < 0 ? NEGATIVE_POW_D : POSITIVE_POW_D);
		}

		var parser = tl.parser;
		H.INSTANCE.decExponent(parser, exp);
		H.INSTANCE.digits(parser, digits.list);
		H.INSTANCE.nDigits(parser, digits.len);
		return H.INSTANCE.doubleValue(parser);
	}

	private static final double[] SMALL_10_POW = {
			1.0e0,
			1.0e1, 1.0e2, 1.0e3, 1.0e4, 1.0e5,
			1.0e6, 1.0e7, 1.0e8, 1.0e9, 1.0e10,
			1.0e11, 1.0e12, 1.0e13, 1.0e14, 1.0e15,
			1.0e16, 1.0e17, 1.0e18, 1.0e19, 1.0e20,
			1.0e21, 1.0e22
	};
	private static final float[] SINGLE_SMALL_10_POW = {
			1.0e0f,
			1.0e1f, 1.0e2f, 1.0e3f, 1.0e4f, 1.0e5f,
			1.0e6f, 1.0e7f, 1.0e8f, 1.0e9f, 1.0e10f
	};

	/**
	 * 根据IEEE 754标准对截断位置进行四舍五入到最近偶数。
	 *
	 * @param digits 数字序列
	 * @param index  检查进位的起始位置（如第17位）
	 * @return       进位值（0或1）
	 */
	private static int roundUpToNearestEven(CharList digits, int index) {
		char nextChar = digits.charAt(index);
		if (nextChar > '5') return 1;
		if (nextChar == '5') {
			// 检查后续是否全为0
			for (int i = index +1; i < digits.length(); i++) {
				if (digits.charAt(i) != '0') {
					return 1;
				}
			}

			// 全为0时，前一位是否为奇数
			int lastDigit = digits.charAt(index-1) - '0';
			return lastDigit & 1;
		}
		return 0;
	}

	private static final ThreadLocal<TL> PARSER = ThreadLocal.withInitial(TL::new);
	private static final class TL {
		Object parser = H.INSTANCE.newParser(false, 0, null, 0);
		char[] buf = new char[32];
	}

	@Public
	private interface H {
		H INSTANCE = getInstance();
		private static H getInstance() {
			var target = Telescope.findClass("jdk.internal.math.FloatingDecimal$ASCIIToBinaryBuffer");
			String[] fieldNames = {"isNegative", "decExponent", "digits", "nDigits"};
			return Bypass.builder(H.class).unchecked()
					.construct(target, "newParser")
					.delegate(target, new String[]{"floatValue","doubleValue"})
					.access(target, fieldNames, null, fieldNames)
					.build();
		}

		Object newParser(boolean isNegative, int decExponent, char[] digits, int nDigits);
		float floatValue(Object buf);
		double doubleValue(Object buf);

		void isNegative(Object buf, boolean isNegative);
		void decExponent(Object buf, int decExponent);
		void digits(Object buf, char[] digits);
		void nDigits(Object buf, int nDigits);
	}
	//endregion
}