package roj.util;

import roj.reflect.Bypass;
import roj.reflect.Telescope;

/**
 * @author Roj234
 * @since 2026/01/23 01:22
 */
public interface StringAccess {
	StringAccess INSTANCE = init();
	private static StringAccess init() {
		Bypass<StringAccess> builder = Bypass.builder(StringAccess.class);
		try {
			builder.delegate(Telescope.findClass("java.lang.StringLatin1"), "inflate");
		} catch (Exception ignored) {}
		try {
			builder.delegate(Telescope.findClass("java.lang.StringUTF16"), "compress");
		} catch (Exception ignored) {}
		return builder.build();
	}

	default void inflate(byte[] src, int srcOff, char[] dst, int dstOff, int len) {
		for (int i = 0; i < len; i++) {
			dst[dstOff++] = (char)(src[srcOff++] & 0xff);
		}
	}
	default int compress(char[] src, int srcOff, byte[] dst, int dstOff, int len) {
		for (int i = 0; i < len; i++) {
			char c = src[srcOff];
			if (c > 0xFF) {
				len = 0;
				break;
			}
			dst[dstOff] = (byte)c;
			srcOff++;
			dstOff++;
		}
		return len;
	}
}
