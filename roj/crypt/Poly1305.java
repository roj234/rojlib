package roj.crypt;

import roj.io.IOUtil;
import roj.math.MutableBigInteger;
import roj.util.ByteList;

import java.nio.ByteBuffer;
import java.security.DigestException;
import java.security.MessageDigest;

/**
 * @author solo6975
 * @since 2022/2/14 10:36
 */
public final class Poly1305 extends MessageDigest implements MessageAuthenticCode {
	private static final MutableBigInteger P = new MutableBigInteger(new int[] {0x3, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFb});

	final ByteList bList = new ByteList(16);
	final byte[] bBuf = bList.list;
	final int[] iBuf = new int[5];

	private MutableBigInteger Acc = new MutableBigInteger();
	final MutableBigInteger R = new MutableBigInteger(), S = new MutableBigInteger(), Tmp = new MutableBigInteger();

	public Poly1305() {
		super("Poly1305");
	}

	@Override
	public void setSignKey(byte[] key, int off, int len) {
		if (len != 32) throw new IllegalStateException("Poly1305 requires 256 bits of key");
		byte[] tmp = new byte[32];
		System.arraycopy(key, off, tmp, 0, 32);
		key = tmp;

		key[3] &= 15;key[7] &= 15;key[11] &= 15;key[15] &= 15;
		key[4] &= 252;key[8] &= 252;key[12] &= 252;

		int[] T;
		int i, E;

		T = R.getArray0();
		if (T.length < 4) T = new int[4];
		else for (int j = 0; j < T.length - 4; j++) T[j] = 0;
		E = T.length - 4;

		Conv.b2i_LE(key, 0, 16, T, 0);
		i = T[E];
		T[E] = T[E + 3];
		T[E + 3] = i;
		i = T[E + 2];
		T[E + 2] = T[E + 1];
		T[E + 1] = i;
		R.setValue(T, 4);

		T = S.getArray0();
		if (T.length < 4) T = new int[4];
		else for (int j = 0; j < T.length - 4; j++) T[j] = 0;
		E = T.length - 4;

		Conv.b2i_LE(key, 16, 16, T, 0);
		i = T[E];
		T[E] = T[E + 3];
		T[E + 3] = i;
		i = T[E + 2];
		T[E + 2] = T[E + 1];
		T[E + 1] = i;
		S.setValue(T, 4);
	}

	@Override
	protected void engineUpdate(byte b) {
		ByteList L = this.bList;
		L.put(b);
		if (L.wIndex() == 16) {
			_digest(L);
			L.clear();
		}
	}

	@Override
	protected void engineUpdate(byte[] b, int off, int len) {
		ByteList L = this.bList;
		byte[] bBuf = L.list;

		if (L.wIndex() > 0) {
			int len1 = Math.min(16 - L.wIndex(), len);
			L.put(b, off, len1);
			len -= len1;
			if (L.wIndex() == 16) {
				_digest(L);
				L.clear();
			} else {
				return; // no more
			}
		}

		L.wIndex(16);
		while (len >= 16) {
			len -= 16;
			System.arraycopy(b, off, bBuf, 0, 16);
			off += 16;

			L.rIndex = 0;
			_digest(L);
		}
		L.clear();

		L.put(b, off, len);
	}

	@Override
	protected void engineUpdate(ByteBuffer b) {
		if (b.hasArray()) super.engineUpdate(b);
		else {
			int len = b.remaining();
			int off = b.position();

			ByteList L = this.bList;
			byte[] bBuf = L.list;

			if (L.wIndex() > 0) {
				int len1 = Math.min(16 - L.wIndex(), len);
				b.get(bBuf, 0, len1);
				L.wIndex(L.wIndex() + len1);
				len -= len1;
				if (L.wIndex() == 16) {
					_digest(L);
					L.clear();
				} else {
					return; // no more
				}
			}

			L.wIndex(16);
			while (len >= 16) {
				len -= 16;
				b.get(bBuf, 0, 16);
				off += 16;

				L.rIndex = 0;
				L.wIndex(16);
				_digest(L);
			}
			L.clear();

			b.get(bBuf, 0, len);
			L.wIndex(len);
		}
	}

	final void _digest(ByteList in) {
		int[] TI = iBuf;
		TI[4] = in.readIntLE();
		TI[3] = in.readIntLE();
		TI[2] = in.readIntLE();
		TI[1] = in.readIntLE();
		TI[0] = 0x01;

		MutableBigInteger T = Tmp, A = Acc;

		// if the multiplication is performed as a separate operation from the
		// modulus, the result will sometimes be under 2^256 and sometimes be
		// above 2^256.
		int[] bak = T.getArray0();
		T.setValue(TI, 5);
		A.add(T);
		T.setArray0(bak);
		A.multiply(R, T);
		// this is a 'naive' implement... constant-time operation required
		Acc = T.divide(P, A, true);
	}

	final void _digestFinal(ByteList in, ByteList out) {
		MutableBigInteger A = Acc;

		while (in.readableBytes() >= 16) _digest(in);
		if (in.isReadable()) {
			byte[] TB = this.bBuf;
			int r = in.readableBytes();
			in.read(TB, 0, r);
			TB[r++] = 0x01;
			while (r < 16) TB[r++] = 0;

			int[] TI = iBuf;
			TI[0] = 0;
			for (int i = 0; i < 4; i++) {
				TI[4 - i] =
					(TB[3 + (i << 2)] & 0xFF) << 24 |
					(TB[2 + (i << 2)] & 0xFF) << 16 |
					(TB[1 + (i << 2)] & 0xFF) << 8 |
					(TB[(i << 2)] & 0xFF);
			}

			MutableBigInteger T = Tmp;

			int[] bak = T.getArray0();
			T.setValue(TI, 5);
			A.add(T);
			T.setArray0(bak);
			A.multiply(R, T);
			A = T.divide(P, A, true);
		}

		A.add(S);
		int[] arr = A.getArray0();
		int end = arr.length - 4;
		for (int i = arr.length - 1; i >= end; i--) {
			out.putIntLE(arr[i]);
		}
		(Acc = A).clear();
	}

	@Override
	protected byte[] engineDigest() {
		ByteList b = IOUtil.SharedCoder.get().wrap(new byte[16]);
		b.clear();
		_digestFinal(bList, b);
		bList.clear();
		return b.list;
	}

	@Override
	protected int engineDigest(byte[] buf, int offset, int len) throws DigestException {
		if (len < 16) throw new DigestException("partial digests not returned");
		if (buf.length - offset < 16) throw new DigestException("insufficient space in the output buffer to store the digest");

		ByteList b = new ByteList.Slice(buf, offset, len);
		b.wIndex(0);
		_digestFinal(bList, b);
		bList.clear();
		return 16;
	}

	@Override
	public byte[] digestShared() {
		return new byte[0];
	}

	@Override
	protected void engineReset() {
		Acc.clear();
	}

	@Override
	public String toString() {
		return getAlgorithm() + " Message Digest from RojLib";
	}

	@Override
	protected int engineGetDigestLength() {
		return 16;
	}
}
