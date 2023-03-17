package roj.crypt;

import roj.io.IOUtil;
import roj.math.MutableBigInteger;
import roj.util.ByteList;
import roj.util.DynByteBuf;

/**
 * @author solo6975
 * @since 2022/2/14 10:36
 */
public final class Poly1305 extends BufferedDigest implements MessageAuthenticCode {
	private static final MutableBigInteger P = new MutableBigInteger(0x3, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFb);

	private final MutableBigInteger
		R = new MutableBigInteger(new int[4]),
		S = new MutableBigInteger(new int[4]),
		Tmp = new MutableBigInteger(new int[10]);
	private MutableBigInteger Acc = new MutableBigInteger(new int[9]);

	public Poly1305() { super("Poly1305", 16); }

	@Override
	protected int engineGetDigestLength() { return 16; }

	@Override
	public void setSignKey(byte[] key, int off, int len) {
		if (len != 32) throw new IllegalStateException("Poly1305 requires 256 bits of key");
		System.arraycopy(key, off, key = new byte[32], 0, 32);

		key[3] &= 15;key[7] &= 15;key[11] &= 15;key[15] &= 15;
		key[4] &= 252;key[8] &= 252;key[12] &= 252;

		int[] T;
		ByteList b = IOUtil.SharedCoder.get().wrap(key);

		T = R.getArray0();
		T[3] = b.readIntLE();
		T[2] = b.readIntLE();
		T[1] = b.readIntLE();
		T[0] = b.readIntLE();
		R.setValue(T, 4);

		T = S.getArray0();
		T[3] = b.readIntLE();
		T[2] = b.readIntLE();
		T[1] = b.readIntLE();
		T[0] = b.readIntLE();
		S.setValue(T, 4);
	}

	@Override
	protected void engineUpdateBlock(DynByteBuf in) {
		MutableBigInteger T = Tmp, A = Acc;

		int[] TI = T.getArray0();
		TI[4] = in.readIntLE();
		TI[3] = in.readIntLE();
		TI[2] = in.readIntLE();
		TI[1] = in.readIntLE();
		TI[0] = 0x01;
		T.setValue(TI, 5);

		// if the multiplication is performed as a separate operation from the
		// modulus, the result will sometimes be under 2^256 and sometimes be
		// above 2^256.
		A.add(T);
		A.multiply(R, T);
		// this is a 'naive' implement... constant-time operation required
		Acc = T.divide(P, A, true);
	}

	@Override
	protected void engineDigest(ByteList in, DynByteBuf out) {
		if (in.isReadable()) {
			int r = in.wIndex();
			in.wIndex(16);
			byte[] b = in.list;

			b[r++] = 0x01;
			while (r < 16) b[r++] = 0;

			engineUpdateBlock(in);
		}

		Acc.add(S);
		int[] digest = Acc.getArray0();
		if (digest.length < 4) throw new IllegalStateException("你...你是不是没设sign key?");

		int i = digest.length-1;
		out.putIntLE(digest[i--])
		   .putIntLE(digest[i--])
		   .putIntLE(digest[i--])
		   .putIntLE(digest[i]);
	}

	@Override
	protected void engineReset() {
		Acc.clear();
		buf.clear();
	}
}
