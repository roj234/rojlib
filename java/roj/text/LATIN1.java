package roj.text;

import java.util.function.IntConsumer;

import static roj.reflect.Unaligned.U;

/**
 * @author Roj234
 * @since 2025/4/27 1:46
 */
final class LATIN1 extends FastCharset {
	static final FastCharset INSTANCE = new LATIN1();
	private LATIN1() {}

	@Override public String name() {return "ISO-8859-1";}
	@Override public long fastEncode(char[] s, int i, int end, Object ref, long addr, int outMax) {
		while (i < end && outMax > 0) {
			char c = s[i++];
			if (c > 0xFF) c = '?';

			U.putByte(ref, addr++, (byte) c);
			outMax--;
		}

		return ((long)i << 32) | outMax;
	}
	@Override public long fastDecode(Object ref, long base, int pos, int end, char[] out, int off, int outMax) {
		long i = base+pos;
		outMax += off;

		for (long max = base+end; i < max && off < outMax;) {
			int c = U.getByte(ref, i++);
			out[off++] = (char) c;
		}

		return ((i-base) << 32) | off;
	}
	@Override public void fastValidate(Object ref, long i, long max, IntConsumer cs) {
		while (i < max) cs.accept(U.getByte(ref,i++));
	}
	@Override public int byteCount(CharSequence s, int i, int len) {return len;}
	@Override public int encodeSize(int codepoint) {return 1;}
}