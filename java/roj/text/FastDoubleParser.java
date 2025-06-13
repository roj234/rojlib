package roj.text;

import roj.collect.BitArray;
import roj.compiler.runtime.RtUtil;
import roj.reflect.Bypass;

/**
 * 提供高效解析双精度（{@code double}）和单精度（{@code float}）浮点数的方法。
 * <p>
 * 该类通过预计算10的幂次方和误差校正表优化性能，支持符合IEEE 754标准的舍入规则。
 * 解析过程处理有效数字截断、指数调整及误差校正，适用于高性能数值转换场景。
 * @author Roj234-N
 * @since 2025/5/11 7:51
 */
public class FastDoubleParser {
	private interface H {
		Object newParser(boolean isNegative, int exponent, char[] digits, int nDigits);
		float parseFloat(Object buf);
		double parseDouble(Object buf);
	}
	private static H instance;
	static {
		try {
			var target = Class.forName("jdk.internal.math.FloatingDecimal$ASCIIToBinaryBuffer");
			instance = Bypass.builder(H.class)
					.construct(target, "newParser")
					.delegate(target, new String[]{"floatValue","doubleValue"}, new String[]{"parseFloat","parseDouble"}).build();
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}
	private static ThreadLocal<Object> parserCache = new ThreadLocal<>();

	public static double parseDoubleDec(CharList digits, int fractionalIndex, int exp) {
		Object o = instance.newParser(false, exp + fractionalIndex, digits.list, digits.length());
		return instance.parseDouble(o);
	}

	//region 存在无法解决的问题，先放着
	// 预计算10的二次方幂（1, 2, 4, 8...256）
	private static final double[] POSITIVE_POW_D = new double[] {1e1, 1e2, 1e4, 1e8, 1e16, 1e32, 1e64, 1e128, 1e256};
	private static final double[] NEGATIVE_POW_D = new double[] {1e-1, 1e-2, 1e-4, 1e-8, 1e-16, 1e-32, 1e-64, 1e-128, 1e-256};
	private static final BitArray ERROR_D = new BitArray(2, 324+308+1, RtUtil.unpackI("V+V+UgV\u00075WU{V\u001a6+4'I{4\13V\26\f\23L\6V*3W\"\32Kfk^-SwR+U3&\16\u001b3a3g3F4\30^\32MZ+j5n6+L\32S[M[0\26n'6+\13j,\27+kT+,\27T\27V\33V+V+V+V+V*N\27V\26V+6\u001b6*V+SkV+V'4'Vg\rk3fV\27.&S[5WV\26+V+j#V+ZT*,+Uc+W6\u001a4\7\f*)jT\23\6+Uk&*^*6+5gUka\1\25WM"));
	private static final float[] POSITIVE_POW_F = new float[] {1e1f, 1e2f, 1e4f, 1e8f, 1e16f, 1e32f};
	private static final float[] NEGATIVE_POW_F = new float[] {1e-1f, 1e-2f, 1e-4f, 1e-8f, 1e-16f, 1e-32f};
	private static final BitArray ERROR_F = new BitArray(2, 45+38+1, RtUtil.unpackI("V'N+V'6,N+V+WjV+V+L+5gU\1\1\1\13a"));
	/*static {
		for (int i = -324; i <= 308; i++) {
			long v = Double.doubleToRawLongBits(applyExponentD(1, i));
			long v2 = Double.doubleToRawLongBits(Math.pow(10, i));
			ERROR_D.set(i+324, (int) (v2 - v + 2));
		}
		for (int i = -45; i <= 38; i++) {
			int v = Float.floatToRawIntBits(applyExponentF(1, i));
			int v2 = Float.floatToRawIntBits((float) Math.pow(10, i));
			ERROR_F.set(i+45, (v2 - v + 2));
		}
		System.out.println(Tokenizer.addSlashes(ERROR_D.pack()));
		System.out.println(Tokenizer.addSlashes(ERROR_F.pack()));
	}*/

	/**
	 * 解析十进制数字序列为双精度浮点数。
	 * <p>
	 * 当有效数字超过17位时进行截断和舍入，应用指数调整后通过预计算表和误差校正生成最终结果。
	 *
	 * @param digits          十进制数字序列（不包含小数点），如"120345"对应1.20345或120345.0
	 * @param fractionalIndex 原始小数点位置索引。例如"1.20345"中为1，若无小数点则为digits长度
	 * @param exp             十进制指数部分（如"1.2e3"中的3）
	 * @return                解析后的双精度浮点数
	 * @throws NumberFormatException 如果digits包含非数字字符或数值超出范围
	 */
	private static double parseDoubleDec_(CharList digits, int fractionalIndex, int exp) {
		int fractionalLength = digits.length() - fractionalIndex;
		int adjustedExp = exp - fractionalLength;

		int roundUp = 0;

		int totalLength = digits.length();
		if (totalLength > 17) {
			// 截断并进行四舍五入
			roundUp = roundUpToNearestEven(digits, 17);
			adjustedExp += totalLength - 17;
			totalLength = 17;
		}

		long significantNumber = Long.parseLong(digits.substring(0, totalLength)) + roundUp;
		return applyExponentD(significantNumber, adjustedExp);
	}
	private static double applyExponentD(long number, int exponent) {
		double value = number;

		if (exponent == 0) return value;
		if (exponent < -324) return Math.copySign(0.0, value);
		if (exponent > 308) return Math.copySign(Double.POSITIVE_INFINITY, value);

		boolean isNegative = exponent < 0;

		int exp = Math.abs(exponent);
		int bit = 1;
		double pow = 1.0;

		for (int i = 0; exp > 0; i++) {
			if ((exp & bit) != 0) {
				pow *= isNegative ? NEGATIVE_POW_D[i] : POSITIVE_POW_D[i];
				exp -= bit;
			}
			bit <<= 1;
		}

		return Double.longBitsToDouble(Double.doubleToRawLongBits(value * pow) + ERROR_D.get(exponent + 324) - 2);
	}

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

	/**
	 * 解析十进制数字序列为单精度浮点数。
	 * <p>
	 * 当有效数字超过8位时进行截断和舍入，应用指数调整后通过预计算表和误差校正生成最终结果。
	 *
	 * @param digits          十进制数字序列（不包含小数点），如"120345"对应1.20345或120345.0
	 * @param fractionalIndex 原始小数点位置索引。例如"1.20345"中为1，若无小数点则为digits长度
	 * @param exp             十进制指数部分（如"1.2e3"中的3）
	 * @return                解析后的单精度浮点数
	 * @throws NumberFormatException 如果digits包含非数字字符或数值超出范围
	 */
    private static float parseFloatDec_(CharList digits, int fractionalIndex, int exp) {
        if (digits.isEmpty()) return 0.0f;

        int fractionalLength = digits.length() - fractionalIndex;
        int adjustedExp = exp - fractionalLength;

        int roundUp = 0;

        int totalLength = digits.length();
        if (totalLength > 8) {
            // 截断并进行四舍五入
			roundUp = roundUpToNearestEven(digits, 8);
            adjustedExp += totalLength - 8;
			totalLength = 8;
        }

        int significantNumber = Integer.parseInt(digits.substring(0, totalLength)) + roundUp;
		return applyExponentF(significantNumber, adjustedExp);
    }
    private static float applyExponentF(int number, int exponent) {
        float value = number;

        if (exponent == 0) return value;
        if (exponent < -45) return Math.copySign(0.0f, value);
        if (exponent > 38) return Math.copySign(Float.POSITIVE_INFINITY, value);

		boolean isNegative = exponent < 0;

		int exp = Math.abs(exponent);
		int bit = 1;
		float pow = 1.0F;

		for (int i = 0; exp > 0; i++) {
			if ((exp & bit) != 0) {
				pow *= isNegative ? NEGATIVE_POW_F[i] : POSITIVE_POW_F[i];
				exp -= bit;
			}
			bit <<= 1;
		}

		value *= pow;
		value = Float.intBitsToFloat(Float.floatToRawIntBits(value)+ERROR_F.get(exponent+45)-2);
		return value;
    }
	//endregion
}