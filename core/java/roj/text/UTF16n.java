package roj.text;

import roj.reflect.Reflection;
import roj.reflect.Unaligned;

import java.util.function.IntConsumer;

import static java.lang.Character.*;
import static roj.reflect.Unaligned.U;

/**
 * 不验证并且仅内存拷贝的UTF16
 * @author Roj234
 */
final class UTF16n extends FastCharset {
	static final boolean DISABLE_AUTO = Boolean.getBoolean("roj.text.disableUTF16n");
	static final FastCharset INSTANCE = new UTF16n();
	private UTF16n() {}

	@Override public String name() {return Reflection.BIG_ENDIAN ? "UTF-16BE" : "UTF-16LE";}
	@Override public long fastEncode(char[] s, int i, int end, Object ref, long addr, int max_len) {
		int copyChars = Math.min(max_len/2, end-i);
		U.copyMemory(s, Unaligned.ARRAY_CHAR_BASE_OFFSET + (i * 2L), ref, addr, copyChars * 2L);
		i += copyChars;
		return ((long) i << 32) | (max_len - copyChars * 2L);
	}
	@Override public long fastDecode(Object ref, long base, int pos, int end, char[] out, int off, int outMax) {
		int copyChars = Math.min((end-pos)/2, outMax);
		U.copyMemory(ref, base+pos, out, Unaligned.ARRAY_CHAR_BASE_OFFSET + (off * 2L), copyChars * 2L);
		return ((pos+copyChars*2L)<<32) | (off+copyChars);
	}
	@Override public void fastValidate(Object ref, long i, long max, IntConsumer cs) {
		while (i+2 <= max) {
			int c = U.getChar(ref, i); i += 2;

			if (c >= MIN_HIGH_SURROGATE) {
				if (c >= MIN_LOW_SURROGATE) {cs.accept(MALFORMED - 2); continue;}
				if (i+2 > max) {i -= 2; break;}

				int ls = U.getChar(ref, i); i += 2;
				if (ls < MIN_LOW_SURROGATE || ls >= MAX_LOW_SURROGATE) cs.accept(MALFORMED - 2);
				c = codepoint(c, ls);
			}
			cs.accept(c);
		}
		if (i != max) cs.accept(TRUNCATED);
	}
	@Override public int byteCount(CharSequence s, int i, int len) {return len*2;}
	@Override public int addBOM(Object ref, long addr) {U.putChar(ref, addr, (char) 0xFEFF);return 2;}
	@Override public int encodeSize(int codepoint) {return codepoint > 0xFFFF ? 4 : 2;}
}