package roj.compiler.runtime;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Range;
import roj.ci.annotation.IndirectReference;
import roj.compiler.plugins.eval.Constexpr;

import java.nio.charset.StandardCharsets;

/**
 * @author Roj234
 * @since 2024/6/10 1:35
 */
public class RtUtil {
	public static final String CLASS_NAME = "roj/compiler/runtime/RtUtil";

	@SuppressWarnings("unchecked")
	@Contract("_ -> fail")
	public static <T extends Throwable> void athrow(Throwable e) throws T {throw (T) e;}

	@IndirectReference
	public static void nullCheck(Object value, String name) {
		if (value == null)
			throw new NullPointerException(name);
	}

	@IndirectReference
	public static long rangeCheck(long value, String name, long min, long max) {
		if (value < min || value > max)
			throw createException(value, name, min, max);
		return value;
	}
	private static IllegalArgumentException createException(long value, String name, long min, long max) {
		return new IllegalArgumentException("'"+name+"' must be between "+min+" and "+max+": "+value);
	}

	@IndirectReference
	public static int rangeCheck(int value, String name, int min, int max) {
		if (value < min || value > max)
			throw createException(value, name, min, max);
		return value;
	}
	private static IllegalArgumentException createException(int value, String name, int min, int max) {
		return new IllegalArgumentException("'"+name+"' must be between "+min+" and "+max+": "+value);
	}

	public static String flagToString(int flag, String... flagNames) {
		var sb = new StringBuilder();
		for (int i = 0; i < flagNames.length; i++) {
			if ((flag & (1 << i)) != 0) {
				String name = flagNames[i];
				if (name != null) sb.append(name).append(' ');
			}
		}
		if (sb.length() > 0) sb.setLength(sb.length()-1);
		return sb.toString();
	}

	@IndirectReference
	public static Throwable twr(Throwable local, AutoCloseable closeable) {
		if (closeable != null) {
			try {
				closeable.close();
			} catch (Exception stack) {
				if (local == null) return stack;
				local.addSuppressed(stack);
			}
		}

		return local;
	}

	@Constexpr
	@Contract(pure = true)
	public static int pow(int base, int exponent) {
		int result = 1;
		while (exponent > 0) {
			if ((exponent & 1) == 1) {
				result *= base;
			}
			base *= base;
			exponent >>= 1;
		}
		return result;
	}

	@Constexpr
	@Contract(pure = true)
	public static long pow(long base, long exponent) {
		long result = 1;
		while (exponent > 0) {
			if ((exponent & 1) == 1) {
				result *= base;
			}
			base *= base;
			exponent >>= 1;
		}
		return result;
	}

	@Constexpr
	@Contract(pure = true)
	public static int positiveMod(int dividend, @Range(from = 1, to = Integer.MAX_VALUE, enforce = false) int divisor) {
		var remainder = dividend % divisor;
		return remainder >= 0 ? remainder : (remainder + divisor) % divisor;
	}

	@Constexpr
	@Contract(pure = true)
	public static long positiveMod(long dividend, @Range(from = 1, to = Integer.MAX_VALUE, enforce = false) long divisor) {
		var remainder = dividend % divisor;
		return remainder >= 0 ? remainder : (remainder + divisor) % divisor;
	}

	@Constexpr
	@Contract(pure = true)
	public static String pack(int[] array) {
		byte[] sb = new byte[(array.length * 32 + 6) / 7];
		int sbIndex = 0;

		int bitIndex = 6;
		int buffer = 0;

		for (int value : array) {
			for (int i = 31; i >= 0; i--) {
				buffer |= ((value>>>i)&1) << bitIndex;
				if (bitIndex-- == 0) {
					sb[sbIndex++] = (byte)(buffer+1);
					buffer = 0;
					bitIndex = 6;
				}
			}
		}

		if (bitIndex != 6) sb[sbIndex] = (byte)(buffer+1);
		return new String(sb, StandardCharsets.ISO_8859_1);
	}

	@Constexpr
	@Contract(pure = true)
	public static String pack(byte[] array) {
		byte[] sb = new byte[(array.length * 8 + 6) / 7];
		int sbIndex = 0;

		int bitIndex = 6;
		int buffer = 0;

		for (int value : array) {
			for (int i = 7; i >= 0; i--) {
				buffer |= ((value>>>i)&1) << bitIndex;
				if (bitIndex-- == 0) {
					sb[sbIndex++] = (byte)(buffer+1);
					buffer = 0;
					bitIndex = 6;
				}
			}
		}

		if (bitIndex != 6) sb[sbIndex] = (byte)(buffer+1);
		return new String(sb, StandardCharsets.ISO_8859_1);
	}

	@Contract(pure = true)
	public static int[] unpackI(String data) {
		int length = data.length();
		int[] array = new int[(length * 7) >>> 5];
		int arrayIndex = 0;
		int bitIndex = 31;
		int buffer = 0;

		for (int i = 0; i < length; i++) {
			int value = data.charAt(i) - 1;

			for (int j = 6; j >= 0; j--) {
				buffer |= ((value>>j)&1) << bitIndex;

				if (bitIndex-- == 0) {
					array[arrayIndex++] = buffer;
					buffer = 0;
					bitIndex = 31;
				}
			}
		}

		return array;
	}

	@Contract(pure = true)
	public static byte[] unpackB(String data) {
		int length = data.length();
		byte[] array = new byte[(length * 7) >>> 3];
		int arrayIndex = 0;
		int bitIndex = 7;
		int buffer = 0;

		for (int i = 0; i < length; i++) {
			int value = data.charAt(i) - 1;

			for (int j = 6; j >= 0; j--) {
				buffer |= ((value>>j)&1) << bitIndex;

				if (bitIndex-- == 0) {
					array[arrayIndex++] = (byte) buffer;
					buffer = 0;
					bitIndex = 7;
				}
			}
		}

		return array;
	}
}