package roj.text;

import roj.reflect.ReflectionUtils;
import sun.misc.Unsafe;

import java.nio.charset.Charset;
import java.util.function.IntConsumer;

import static java.lang.Character.*;
import static roj.reflect.ReflectionUtils.u;

/**
 * 不验证并且仅内存拷贝的UTF16
 * @author Roj234
 */
public final class UTF16n extends UnsafeCharset {
	public static final UnsafeCharset CODER = Boolean.getBoolean("roj.text.disableUTF16n") ? null : new UTF16n();
	public static boolean is(Charset charset) {return CODER != null && charset.name().equals(CODER.name());}

	@Override
	public String name() {return ReflectionUtils.BIG_ENDIAN ? "UTF-16BE" : "UTF-16LE";}

	@Override
	public long unsafeEncode(char[] s, int i, int end, Object ref, long addr, int max_len) {
		int copyChars = Math.min(max_len/2, end-i);
		u.copyMemory(s, Unsafe.ARRAY_CHAR_BASE_OFFSET+(i * 2L), ref, addr, copyChars * 2L);
		i += copyChars;
		return ((long) i << 32) | (copyChars * 2L);
	}

	@Override
	public long unsafeDecode(Object ref, long base, int pos, int end, char[] out, int off, int outMax) {
		int copyChars = Math.min((end-pos)/2, outMax);
		u.copyMemory(ref, base+pos, out, Unsafe.ARRAY_CHAR_BASE_OFFSET+(off * 2L), copyChars * 2L);
		return ((pos+copyChars*2L)<<32) | (off+copyChars);
	}

	@Override
	public void unsafeValidate(Object ref, long i, long max, IntConsumer cs) {
		while (i+2 <= max) {
			int c = u.getChar(ref, i); i += 2;

			if (c >= MIN_HIGH_SURROGATE) {
				if (c >= MIN_LOW_SURROGATE) {cs.accept(MALFORMED - 2); continue;}
				if (i+2 > max) {i -= 2; break;}

				int ls = u.getChar(ref, i); i += 2;
				if (ls < MIN_LOW_SURROGATE || ls >= MAX_LOW_SURROGATE) cs.accept(MALFORMED - 2);
				c = codepoint(c, ls);
			}
			cs.accept(c);
		}
		if (i != max) cs.accept(TRUNCATED);
	}

	@Override
	public int byteCount(CharSequence s, int i, int len) {return len*2;}
}