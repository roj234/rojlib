package roj.text;

import roj.util.DynByteBuf;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static roj.reflect.Unsafe.U;

/**
 * 这是全新的BM字符串匹配实现
 * @author Roj234
 * @since 2023/1/24 4:02
 */
public class FastMatcher {
	private byte[] pattern;
	private int[] skip;
	private int k;

	public FastMatcher() {}
	public FastMatcher(String pattern) { setPattern(pattern); }
	public FastMatcher(DynByteBuf pattern) { setPattern(pattern); }
	public void setPattern(String p) {setPattern(p.getBytes(StandardCharsets.UTF_8));}
	public void setPattern(DynByteBuf p) {setPattern(p.toByteArray());}
	public void setPattern(byte[] pattern) {
		int[] skip = new int[256];
		Arrays.fill(skip, pattern.length);

		int plen = pattern.length - 1;
		for(int i = 0; i < plen; i++)
			skip[pattern[i]&0xFF] = plen - i;

		this.k = skip[pattern[plen]&0xFF];
		this.skip = skip;
		this.pattern = pattern;
	}

	public byte[] pattern() { return pattern; }
	public long next(long i) {return i+k;}

	public int match(DynByteBuf buf, int off) {
		long start = buf._unsafeAddr()+buf.rIndex;
		long pos = match(buf.array(), start + off, buf.readableBytes());
		if (pos < 0) return -1;
		return (int) (pos - start);
	}

	public long match(Object ref, long off, int len) {
		byte[] p = pattern;
		int p_end = p.length-1;

		long end = off+len-p.length;
		while(off <= end) {
			int tmp = p_end;
			while(U.getByte(ref, off+tmp) == p[tmp])
				if (--tmp < 0) return off;

			off += skip[U.getByte(ref, off + p_end) & 0xFF];
		}
		return -1;
	}
}