package roj.crypt;

import roj.util.ByteList;
import roj.util.DynByteBuf;

import javax.crypto.AEADBadTagException;
import javax.crypto.ShortBufferException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Random;

/**
 * @author Roj234
 * @since 2022/12/28 0028 14:20
 */
public class AES_GCM extends AES {
	private static final int AES_BLOCK_SIZE = 16;

	public AES_GCM() {}

	private byte[] iv;
	private DynByteBuf AAD;
	private int tagLen = AES_BLOCK_SIZE;
	private int lenAAD, processed;

	private final ByteList tmp = ByteList.allocate(AES_BLOCK_SIZE,AES_BLOCK_SIZE);

	@Override
	public int getCryptSize(int data) {
		return mode ? data - tagLen : data + tagLen;
	}

	@Override
	public void setKey(byte[] key, int cryptFlags) {
		super.setKey(key, ENCRYPT);
		mode = (cryptFlags & DECRYPT) != 0;

		ByteList H = tmp; H.clear(); Arrays.fill(H.array(), (byte) 0);
		aes_encrypt(encrypt_key, limit, H.slice(0, AES_BLOCK_SIZE), H);

		H0 = H.readLong();
		H1 = H.readLong();

		if (iv != null) _setIv(ByteList.wrap(iv));
	}

	@Override
	public void setOption(String key, Object v) {
		switch (key) {
			case "IV":
				iv = (byte[]) v;
				if (encrypt_key != null) _setIv(ByteList.wrap(iv));
				break;
			case "TAG_LEN":
				tagLen = (int) v;
				break;
			case "PRNG":
				iv = new byte[13];
				((Random) v).nextBytes(iv);
				if (encrypt_key != null) _setIv(ByteList.wrap(iv));
				break;
		}
	}

	private void _setIv(DynByteBuf iv) {
		counter_iv.clear();

		int len = iv.readableBytes();
		if (len == 12) {
			counter_iv.put(iv).putInt(1);
		} else {
			long[] h = hash;
			h[0] = h[1] = 0;

			int block = len / AES_BLOCK_SIZE;
			while (block-- > 0) {
				long in0 = iv.readLong();
				long in1 = iv.readLong();

				h[0] ^= in0; h[1] ^= in1;
				GaloisHash(h, H0, H1);
			}

			if (iv.isReadable()) {
				paddedHash(iv);
			}

			h[0] ^= 0; h[1] ^= (long) len << 3;
			GaloisHash(h, H0, H1);
			counter_iv.putLong(hash[0]).putLong(hash[1]);
		}
	}

	@Override
	public void crypt(DynByteBuf in, DynByteBuf out) throws GeneralSecurityException {
		if (mode) {
			if (out.writableBytes() < in.readableBytes()+tagLen) throw new ShortBufferException();
			in.wIndex(in.wIndex()-tagLen);
		} else {
			if (out.writableBytes() < in.readableBytes()-tagLen) throw new ShortBufferException();
		}

		cryptInit();
		if (AAD != null) insertAAD(AAD);
		if (mode) {
			decryptBlock(in, out);
			decryptFinal(in, out);
			in.wIndex(in.wIndex()+tagLen);
			checkHash(in);
		} else {
			encryptBlock(in, out);
			encryptFinal(in, out);
		}
	}

	public final void cryptInit() {
		if (iv == null || encrypt_key == null) throw new IllegalArgumentException("AES_GCM must have IV set");

		long[] h = hash;
		h[0] = h[1] = 0;

		counter_iv.rIndex = 0;
		counter.clear(); counter.put(counter_iv);
		incr(counter.array());

		lenAAD = processed = 0;
	}
	public final void insertAAD(DynByteBuf AAD) {
		lenAAD += AAD.readableBytes();

		long[] h = hash;

		int block = AAD.readableBytes() / AES_BLOCK_SIZE;
		while (block-- > 0) {
			h[0] ^= AAD.readLong(); h[1] ^= AAD.readLong();
			GaloisHash(h, H0, H1);
		}

		if (AAD.isReadable()) paddedHash(AAD);
	}
	public final void encryptBlock(DynByteBuf in, DynByteBuf out) {
		long[] h = hash;
		ByteList t = tmp;

		int block = in.readableBytes() / AES_BLOCK_SIZE;
		processed += block * AES_BLOCK_SIZE;

		ByteList ctr = counter;
		while (block > 0) {
			t.clear();
			ctr.rIndex = 0;
			aes_encrypt(encrypt_key, limit, ctr, t);
			incr(ctr.array());

			long in0 = in.readLong()^t.readLong(), in1 = in.readLong()^t.readLong();

			h[0] ^= in0; h[1] ^= in1;
			GaloisHash(h, H0, H1);

			out.putLong(in0).putLong(in1);
			block--;
		}
	}
	public final void encryptFinal(DynByteBuf in, DynByteBuf out) {
		int len = in.readableBytes();
		if (len > 0) {
			// gen ctr inline
			ByteList ctr = counter; ctr.clear();
			aes_encrypt(encrypt_key, limit, ctr.slice(0, AES_BLOCK_SIZE), ctr);

			processed += len;

			// ctr update final
			int len1 = len;
			while (len1-- > 0) out.put((byte) (in.get() ^ ctr.get()));

			paddedHash(out.slice(out.wIndex()-len, len));
		}

		// final block - lengths
		finalHashBlock();

		ByteList t = getHash();

		ByteList ctr = counter_iv; ctr.rIndex = 0;
		ByteList t1 = counter; t1.clear();
		aes_encrypt(encrypt_key, limit, ctr, t1);

		len = tagLen;
		while (len-- > 0) out.put((byte) (t.get() ^ t1.get()));
	}
	public final void decryptBlock(DynByteBuf in, DynByteBuf out) {
		long[] h = hash;
		ByteList t = tmp;

		int block = in.readableBytes() / AES_BLOCK_SIZE;
		processed += block * AES_BLOCK_SIZE;

		ByteList ctr = counter;
		while (block > 0) {
			t.clear();
			ctr.rIndex = 0;
			aes_encrypt(encrypt_key, limit, ctr, t);
			incr(ctr.array());

			long in0 = in.readLong(), in1 = in.readLong();

			h[0] ^= in0; h[1] ^= in1;
			GaloisHash(h, H0, H1);

			out.putLong(in0^t.readLong()).putLong(in1^t.readLong());
			block--;
		}
	}
	public final void decryptFinal(DynByteBuf in, DynByteBuf out) {
		int len = in.readableBytes();
		if (len == 0) return;

		// gen ctr inline
		ByteList ctr = counter;
		ctr.clear();
		aes_encrypt(encrypt_key, limit, ctr.slice(0, AES_BLOCK_SIZE), ctr);

		processed += len;

		paddedHash(in);

		// ctr update final
		while (len-- > 0) out.put((byte) (in.get() ^ ctr.get()));
	}
	public final void checkHash(DynByteBuf in) throws AEADBadTagException {
		ByteList ctr = counter_iv; ctr.rIndex = 0;
		ByteList t1 = counter; t1.clear();
		aes_encrypt(encrypt_key, limit, ctr, t1);

		finalHashBlock();
		ByteList t = getHash();
		int v = 0;
		for (int i = tagLen; i > 0; i--) {
			v |= in.get() ^ t.get() ^ t1.get();
		}
		if (v != 0) throw new AEADBadTagException();
	}
	private void finalHashBlock() {
		long[] h = hash;
		h[0] ^= (long) lenAAD << 3;
		h[1] ^= (long) processed << 3;
		GaloisHash(h, H0, H1);
	}
	private ByteList getHash() {
		ByteList b = tmp; b.clear();
		b.putLong(hash[0]).putLong(hash[1]);
		return b;
	}

	// region Galois Hash
	private void paddedHash(DynByteBuf in) {
		ByteList t = tmp;
		t.clear(); t.put(in); t.wIndex(AES_BLOCK_SIZE);

		// zero padded
		byte[] t0 = t.array();
		for (int i = in.readableBytes(); i < t0.length; i++) t0[i] = 0;

		long[] h = hash;
		h[0] ^= t.readLong(); h[1] ^= t.readLong();
		GaloisHash(h, H0, H1);
	}
	// subkey H
	private long H0, H1;
	private final long[] hash = new long[2];
	private static void GaloisHash(long[] h, long H0, long H1) {
		long Z0 = 0;
		long Z1 = 0;
		long X;

		// Separate loops for processing state[0] and state[1].
		X = h[0];
		for (int i = 0; i < 64; i++) {
			// Zi+1 = Zi if bit i of x is 0
			long mask = X >> 63;
			Z0 ^= H0 & mask;
			Z1 ^= H1 & mask;

			// Save mask for conditional reduction below.
			mask = (H1 << 63) >> 63;

			// V = rightshift(V)
			long carry = H0 & 1;
			H0 = H0 >>> 1;
			H1 = (H1 >>> 1) | (carry << 63);

			// Conditional reduction modulo P128.
			H0 ^= 0xe100000000000000L & mask;
			X <<= 1;
		}

		X = h[1];
		for (int i = 64; i < 127; i++) {
			// Zi+1 = Zi if bit i of x is 0
			long mask = X >> 63;
			Z0 ^= H0 & mask;
			Z1 ^= H1 & mask;

			// Save mask for conditional reduction below.
			mask = (H1 << 63) >> 63;

			// V = rightshift(V)
			long carry = H0 & 1;
			H0 = H0 >>> 1;
			H1 = (H1 >>> 1) | (carry << 63);

			// Conditional reduction.
			H0 ^= 0xe100000000000000L & mask;
			X <<= 1;
		}

		// calculate Z128
		long mask = X >> 63;
		Z0 ^= H0 & mask;
		Z1 ^= H1 & mask;

		// Save result.
		h[0] = Z0;
		h[1] = Z1;

	}
	// endregion
	// region Galois Counter
	private final ByteList counter = ByteList.allocate(AES_BLOCK_SIZE,AES_BLOCK_SIZE), counter_iv = ByteList.allocate(AES_BLOCK_SIZE,AES_BLOCK_SIZE);
	private void incr(byte[] b) {
		// start from last byte and only go over 4 bytes, i.e. total 32 bits
		int n = AES_BLOCK_SIZE - 1;
		while ((n >= AES_BLOCK_SIZE - 4) && (++b[n] == 0)) {
			n--;
		}
	}
	// endregion
}
