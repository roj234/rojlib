package roj.text;

import roj.ci.annotation.Public;
import roj.reflect.Bypass;

/**
 * 提供高效解析双精度（{@code double}）和单精度（{@code float}）浮点数的方法。
 * @author Roj234-N
 * @since 2025/5/11 7:51
 */
public class FastDoubleParser {
	public static float parseFloat(CharList digits) {
		if (digits.startsWith("0x")) return Float.parseFloat(digits.toString());

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
					exp = TextUtil.parseInt(digits, i+1, digits.len);
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
			if (totalLength > 8) {
				// 截断并进行四舍五入
				roundUp = roundUpToNearestEven(digits, 8);
				totalLength = 8;
			}
			int adjustedExp = exp - totalLength;

			float significantNumber = Integer.parseInt(digits.substring(0, totalLength)) + roundUp;
			if (adjustedExp == 0) return significantNumber;
			if (adjustedExp < -45) return 0.0f;
			if (adjustedExp > 38) return Float.POSITIVE_INFINITY;

			if (Math.abs(adjustedExp) < SINGLE_SMALL_10_POW.length) {
				return adjustedExp > 0 ? significantNumber * SINGLE_SMALL_10_POW[adjustedExp] : significantNumber / SINGLE_SMALL_10_POW[-adjustedExp];
			}
			//pow(significantNumber, Math.abs(adjustedExp), adjustedExp < 0 ? NEGATIVE_POW_D : POSITIVE_POW_D);
		}

		Object parser = PARSER.get();
		INSTANCE.decExponent(parser, exp);
		INSTANCE.digits(parser, digits.list);
		INSTANCE.nDigits(parser, digits.len);
		return INSTANCE.floatValue(parser);
	}

	public static double parseDouble(CharList digits) {
		if (digits.startsWith("0x")) return Double.parseDouble(digits.toString());

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

					exp = (int) Tokenizer.parseNumber(digits, n, digits.len, 0, next == '-');
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
			if (totalLength > 17) {
				roundUp = roundUpToNearestEven(digits, 17);
				totalLength = 17;
			}
			int adjustedExp = exp - totalLength;

			double significantNumber = Long.parseLong(digits.substring(0, totalLength)) + roundUp;

			if (adjustedExp == 0) return significantNumber;
			if (adjustedExp < -324) return 0.0;
			if (adjustedExp > 308) return Double.POSITIVE_INFINITY;

			if (Math.abs(adjustedExp) < SMALL_10_POW.length) {
				return adjustedExp > 0 ? significantNumber * SMALL_10_POW[adjustedExp] : significantNumber / SMALL_10_POW[-adjustedExp];
			}
			//pow(significantNumber, Math.abs(adjustedExp), adjustedExp < 0 ? NEGATIVE_POW_D : POSITIVE_POW_D);
		}

		Object parser = PARSER.get();
		INSTANCE.decExponent(parser, exp);
		INSTANCE.digits(parser, digits.list);
		INSTANCE.nDigits(parser, digits.len);
		return INSTANCE.doubleValue(parser);
	}

	private static final H INSTANCE;
	static {
		try {
			var target = Class.forName("jdk.internal.math.FloatingDecimal$ASCIIToBinaryBuffer");
			INSTANCE = Bypass.builder(H.class).inline().unchecked()
					.construct(target, "newParser")
					.delegate(target, new String[]{"floatValue","doubleValue"})
					.access(target, new String[]{"isNegative","decExponent","digits","nDigits"}, null, new String[]{"isNegative","decExponent","digits","nDigits"})
					.build();
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}
	private static final ThreadLocal<Object> PARSER = ThreadLocal.withInitial(() -> INSTANCE.newParser(false, 0, null, 0));

	@Public
	private interface H {
		Object newParser(boolean isNegative, int decExponent, char[] digits, int nDigits);
		float floatValue(Object buf);
		double doubleValue(Object buf);

		void isNegative(Object buf, boolean isNegative);
		void decExponent(Object buf, int decExponent);
		void digits(Object buf, char[] digits);
		void nDigits(Object buf, int nDigits);
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
}