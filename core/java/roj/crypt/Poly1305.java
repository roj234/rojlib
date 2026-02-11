package roj.crypt;

import roj.io.IOUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;

/**
 * @since 2022/2/14 10:36
 * @revised 2026/2/25
 */
final class Poly1305 extends BufferedDigest implements MessageAuthenticCode, Cloneable {
	// 26-bit limbs
	private long h0, h1, h2, h3, h4;
	private int R0, R1, R2, R3, R4;
	private int pr1, pr2, pr3, pr4;
	private int S0, S1, S2, S3;

	Poly1305() { super("Poly1305", 16); }

	@Override
	protected int engineGetDigestLength() { return 16; }

	@Override
	public void init(byte[] key, int off, int len) {
		if (len != 32) throw new IllegalStateException("Poly1305 requires 256 bits of key");
		var in = IOUtil.SharedBuf.get().wrap(key, off, len);

		// R &= 0xffffffc0ffffffc0ffffffc0fffffff
		int r0 = in.readIntLE() & 0x0FFFFFFF;
		int r1 = in.readIntLE() & 0x0FFFFFFC;
		int r2 = in.readIntLE() & 0x0FFFFFFC;
		int r3 = in.readIntLE() & 0x0FFFFFFC;

		// to limbs
		R0 = r0 & 0x3FFFFFF;
		R1 = ((r0 >>> 26) | (r1 << 6)) & 0x3FFFFFF;
		R2 = ((r1 >>> 20) | (r2 << 12)) & 0x3FFFFFF;
		R3 = ((r2 >>> 14) | (r3 << 18)) & 0x3FFFFFF;
		R4 = (r3 >>> 8) & 0x3FFFFFF;

		S0 = in.readIntLE();
		S1 = in.readIntLE();
		S2 = in.readIntLE();
		S3 = in.readIntLE();

		pr1 = R1 * 5;
		pr2 = R2 * 5;
		pr3 = R3 * 5;
		pr4 = R4 * 5;

		reset();
	}

	@Override
	protected void engineUpdateBlock(DynByteBuf in) {doBlock(in, 1);}

	private void doBlock(DynByteBuf in, int chunk) {
		long m0 = in.readUnsignedIntLE();
		long m1 = in.readUnsignedIntLE();
		long m2 = in.readUnsignedIntLE();
		long m3 = in.readUnsignedIntLE();

		// h += m
		h0 += m0 & 0x3FFFFFF;
		h1 += ((m0 >>> 26) | (m1 << 6)) & 0x3FFFFFF;
		h2 += ((m1 >>> 20) | (m2 << 12)) & 0x3FFFFFF;
		h3 += ((m2 >>> 14) | (m3 << 18)) & 0x3FFFFFF;
		h4 += (m3 >>> 8) | (chunk << 24); // 129th bit

		// h = (h * r) % (2^130 - 5)
		long t0 = h0* R0 + h1*pr4 + h2*pr3 + h3*pr2 + h4*pr1;
		long t1 = h0* R1 + h1* R0 + h2*pr4 + h3*pr3 + h4*pr2;
		long t2 = h0* R2 + h1* R1 + h2* R0 + h3*pr4 + h4*pr3;
		long t3 = h0* R3 + h1* R2 + h2* R1 + h3* R0 + h4*pr4;
		long t4 = h0* R4 + h1* R3 + h2* R2 + h3* R1 + h4* R0;

		// carry
		h0 = t0 & 0x3FFFFFF; t1 += (t0 >>> 26);
		h1 = t1 & 0x3FFFFFF; t2 += (t1 >>> 26);
		h2 = t2 & 0x3FFFFFF; t3 += (t2 >>> 26);
		h3 = t3 & 0x3FFFFFF; t4 += (t3 >>> 26);
		h4 = t4 & 0x3FFFFFF;

		// 超过 2^130 的部分乘 5 回到低位
		h0 += (t4 >>> 26) * 5;
		// Edit: 并不能删除
		h1 += (h0 >>> 26); h0 &= 0x3FFFFFF;
	}

	@Override
	protected void engineDigest(ByteList in, DynByteBuf out) {
		// h += S
		long f0 = ((h0 | (h1 << 26)) & 0xFFFFFFFFL) + (S0 & 0xFFFFFFFFL);
		long f1 = (((h1 >>> 6) | (h2 << 20)) & 0xFFFFFFFFL) + (S1 & 0xFFFFFFFFL);
		long f2 = (((h2 >>> 12) | (h3 << 14)) & 0xFFFFFFFFL) + (S2 & 0xFFFFFFFFL);
		long f3 = (((h3 >>> 18) | (h4 << 8)) & 0xFFFFFFFFL) + (S3 & 0xFFFFFFFFL);

		// carry
		f1 += (f0 >>> 32);
		f2 += (f1 >>> 32);
		f3 += (f2 >>> 32);

		out.putIntLE((int)f0).putIntLE((int)f1).putIntLE((int)f2).putIntLE((int)f3);
	}

	@Override
	protected void engineReset() {
		h0 = h1 = h2 = h3 = h4 = 0;
	}

	@Override
	public Object clone() {
		Poly1305 clone;
		try {
			clone = (Poly1305) super.clone();
		} catch (CloneNotSupportedException e) {
			throw new RuntimeException(e);
		}
		clone.reset();
		return clone;
	}
}
