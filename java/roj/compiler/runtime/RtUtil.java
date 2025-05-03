package roj.compiler.runtime;

import org.jetbrains.annotations.Contract;
import roj.ReferenceByGeneratedClass;
import roj.compiler.plugins.eval.Constexpr;

import java.nio.charset.StandardCharsets;

/**
 * @author Roj234
 * @since 2024/6/10 1:35
 */
public class RtUtil {
	public static final String CLASS_NAME = "roj/compiler/runtime/RtUtil";

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

	@ReferenceByGeneratedClass
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