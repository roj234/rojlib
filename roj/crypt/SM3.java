package roj.crypt;

import roj.util.ByteList;
import roj.util.DynByteBuf;

/**
 * 国密SM3 - 校验码
 */
public final class SM3 extends BufferedDigest {
	private static final int[] T = new int[64];
	static {
		for(int i = 0; i < 16; ++i) {
			final int Tj1 = 0x79cc4519;
			T[i] = (Tj1 << i) | (Tj1 >>> (32-i));
		}
		for(int i = 16; i < 64; ++i) {
			final int Tj2 = 0x7a879d8a;
			int j = i&31;
			T[i] = (Tj2 << j) | (Tj2 >>> (32 - j));
		}
	}

	private final int[] digest = new int[68 + 8];
	private int bufOff;

	public SM3() {
		super("SM3", 4);
		engineReset();
	}

	@Override
	protected int engineGetDigestLength() { return 32; }

	@Override
	protected void engineUpdateBlock(DynByteBuf b) {
		digest[bufOff++] = b.readInt();
		if (bufOff == 16) {
			GetSM3(digest);
			bufOff = 0;
		}
	}

	@Override
	protected void engineDigest(ByteList in, DynByteBuf out) {
		in.put(0x80);
		while (in.isWritable()) in.put(0);
		engineUpdateBlock(in);

		if (bufOff == 15) {
			digest[15] = 0;
			GetSM3(digest);
			bufOff = 0;
		}

		for (int i = bufOff; i < 14; i++) digest[i] = 0;

		long v = length << 3;
		digest[14] = (int) (v>>>32);
		digest[15] = (int) v;
		GetSM3(digest);

		out.putInt(digest[68 + 0])
		   .putInt(digest[68 + 1])
		   .putInt(digest[68 + 2])
		   .putInt(digest[68 + 3])
		   .putInt(digest[68 + 4])
		   .putInt(digest[68 + 5])
		   .putInt(digest[68 + 6])
		   .putInt(digest[68 + 7]);
	}

	@Override
	protected void engineReset() {
		buf.clear();
		bufOff = 0;
		digest[68 + 0] = 1937774191;
		digest[68 + 1] = 1226093241;
		digest[68 + 2] = 388252375;
		digest[68 + 3] = -628488704;
		digest[68 + 4] = -1452330820;
		digest[68 + 5] = 372324522;
		digest[68 + 6] = -477237683;
		digest[68 + 7] = -1325724082;
	}

	private static void GetSM3(int[] W) {
		for (int i = 16; i < 68; i++) {
			W[i] = P1(W[i - 16] ^ W[i - 9] ^ Conv.IRL(W[i - 3], 15)) ^ Conv.IRL(W[i - 13], 7) ^ W[i - 6];
		}

		int t1, t2;
		int a = W[68 + 0];
		int b = W[68 + 1];
		int c = W[68 + 2];
		int d = W[68 + 3];
		int e = W[68 + 4];
		int f = W[68 + 5];
		int g = W[68 + 6];
		int h = W[68 + 7];

		int i = 0;
		for (; i < 16; i++) {
			t2 = Conv.IRL(a, 12);
			t1 = Conv.IRL(t2 + e + T[i], 7);
			t2 = t1 ^ t2;

			t2 = FF1(a, b, c) + d + t2 + (W[i] ^ W[i + 4]);
			d = c;
			c = Conv.IRL(b, 9);
			b = a;
			a = t2;

			t2 = FF1(e, f, g) + h + t1 + W[i];
			h = g;
			g = Conv.IRL(f, 19);
			f = e;
			e = P0(t2);
		}
		for (; i < 64; i++) {
			t2 = Conv.IRL(a, 12);
			t1 = Conv.IRL(t2 + e + T[i], 7);
			t2 = t1 ^ t2;

			t2 = FF2(a, b, c) + d + t2 + (W[i] ^ W[i + 4]);
			d = c;
			c = Conv.IRL(b, 9);
			b = a;
			a = t2;

			t2 = GG(e, f, g) + h + t1 + W[i];
			h = g;
			g = Conv.IRL(f, 19);
			f = e;
			e = P0(t2);
		}

		W[68 + 0] ^= a;
		W[68 + 1] ^= b;
		W[68 + 2] ^= c;
		W[68 + 3] ^= d;
		W[68 + 4] ^= e;
		W[68 + 5] ^= f;
		W[68 + 6] ^= g;
		W[68 + 7] ^= h;
	}

	private static int FF1(int X, int Y, int Z) { return X ^ Y ^ Z; }
	private static int FF2(int X, int Y, int Z) { return ((X & Y) | (X & Z) | (Y & Z)); }
	private static int GG(int X, int Y, int Z) { return (X & Y) | (~X & Z); }
	private static int P0(int X) { return X ^ Conv.IRL(X, 9) ^ Conv.IRL(X, 17); }
	private static int P1(int X) { return X ^ Conv.IRL(X, 15) ^ Conv.IRL(X, 23); }
}
