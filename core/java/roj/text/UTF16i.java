package roj.text;

import roj.util.JVM;

import java.util.function.IntConsumer;

import static java.lang.Character.*;
import static roj.reflect.Unsafe.U;

/**
 * Inversed UTF16n
 * @author Roj234
 * @since 2025/09/14 2:57:44
 */
final class UTF16i extends FastCharset {
	static final FastCharset INSTANCE = new UTF16i();
	private UTF16i() {}

	@Override public String name() {return !JVM.BIG_ENDIAN ? "UTF-16BE" : "UTF-16LE";}
	@Override public long fastEncode(char[] s, int i, int end, Object ref, long addr, int outMax) {
		while (i < end && outMax > 1) {
			char c = s[i++];
			outMax -= 2;
			U.putChar(ref, addr += 2, Character.reverseBytes(c));
		}

		return ((long) i << 32) | outMax;
	}
	@Override public long fastDecode(Object ref, long base, int pos, int end, char[] out, int off, int outMax) {
		long i = base+pos;
		if (((end-pos) & 1) != 0) end--;
		long max = base+end;
		outMax += off;

		char c;
		while (i < max && off < outMax) {
			c = Character.reverseBytes(U.getChar(ref, i));
			i += 2;
			out[off++] = c;
		}

		return ((i-base) << 32) | off;
	}
	@Override public void fastValidate(Object ref, long i, long max, IntConsumer verifier) {
		while (i+2 <= max) {
			int c = Character.reverseBytes(U.getChar(ref, i)); i += 2;

			if (c >= MIN_HIGH_SURROGATE) {
				if (c >= MIN_LOW_SURROGATE) {
					verifier.accept(MALFORMED - 2); continue;}
				if (i+2 > max) {i -= 2; break;}

				int ls = Character.reverseBytes(U.getChar(ref, i)); i += 2;
				if (ls < MIN_LOW_SURROGATE || ls >= MAX_LOW_SURROGATE) verifier.accept(MALFORMED - 2);
				c = codepointNoExc(c, ls);
			}
			verifier.accept(c);
		}
		if (i != max) verifier.accept(TRUNCATED);
	}
	@Override public int byteCount(CharSequence s, int i, int len) {return len*2;}
	@Override public int addBOM(Object ref, long addr) {U.putChar(ref, addr, (char) 0xFFFE);return 2;}
	@Override public int encodeSize(int codepoint) {return codepoint > 0xFFFF ? 4 : 2;}
}