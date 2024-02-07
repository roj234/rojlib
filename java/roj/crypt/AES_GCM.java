package roj.crypt;

import roj.util.ByteList;
import roj.util.DynByteBuf;
import sun.misc.Unsafe;

import javax.crypto.AEADBadTagException;
import javax.crypto.BadPaddingException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.IvParameterSpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Arrays;

/**
 * @author Roj234
 * @since 2022/12/28 0028 14:20
 */
public class AES_GCM extends AES {

	public AES_GCM() {}

	private ByteList aadBuffer;
	private int lenAAD, processed;

	SecureRandom prng;
	byte state, tagLen;

	private final ByteList tmp = ByteList.allocate(AES_BLOCK_SIZE,AES_BLOCK_SIZE);

	public void init(int mode, byte[] key, AlgorithmParameterSpec par, SecureRandom random) throws InvalidAlgorithmParameterException, InvalidKeyException {
		super.init(mode, key, null, null);

		ByteList H = tmp; H.clear(); Arrays.fill(H.array(), (byte) 0);
		aes_encrypt(encrypt_key, limit, H.slice(0, AES_BLOCK_SIZE), H);

		H0 = H.readLong();
		H1 = H.readLong();

		state = 0;
		tagLen = AES_BLOCK_SIZE;

		if (par != null) {
			if (random != null) throw new IllegalArgumentException("IV和随机器必须也只能提供一个");
			byte[] newIv;

			if (par instanceof IvParameterSpec) {
				newIv = ((IvParameterSpec) par).getIV();
			} else if (par instanceof IvParameterSpecNC) {
				newIv = ((IvParameterSpecNC) par).getIV();
				if (par instanceof AEADParameterSpec) {
					int size = ((AEADParameterSpec) par).getTagSize();
					if (size != 0) {
						if (size < 6 || size > 8) throw new InvalidAlgorithmParameterException("tag size无效, got " + size);
						tagLen = (byte) size;
					}
				}
			} else {
				throw new InvalidAlgorithmParameterException();
			}

			iv.clear();
			if (newIv.length == 12) {
				iv.put(newIv).putInt(1);
			} else {
				long[] h = hash;
				h[0] = h[1] = 0;

				int block = newIv.length / AES_BLOCK_SIZE;
				ByteList b = ByteList.wrap(newIv);
				while (block-- > 0) {
					h[0] ^= b.readLong(); h[1] ^= b.readLong();
					GaloisHash(h, H0, H1);
				}

				if (b.isReadable()) paddedHash(b);

				// lenAAD and processed
				h[0] ^= 0; h[1] ^= (long) newIv.length << 3;
				GaloisHash(h, H0, H1);
				iv.putLong(hash[0]).putLong(hash[1]);
			}
		} else {
			if (random == null) throw new IllegalArgumentException("IV和随机器必须也只能提供一个");

			this.prng = random;
		}
	}

	public int engineGetOutputSize(int data) { return decrypt ? data - AES_BLOCK_SIZE : data + AES_BLOCK_SIZE; }

	public void crypt(DynByteBuf in, DynByteBuf out) throws ShortBufferException {
		if (out.writableBytes() < in.readableBytes()) throw new ShortBufferException();

		if (state != 2) {
			if (state == 0) cryptBegin();
			else {
				paddedHash(aadBuffer);
				aadBuffer.clear();
			}
			state = 2;
		}

		long[] h = hash;
		ByteList t = tmp;

		int block = in.readableBytes() / AES_BLOCK_SIZE;
		if (decrypt) {
			// 防止tag被处理掉
			if (--block == 0) return;
		}
		processed += block * AES_BLOCK_SIZE;

		ByteList ctr = counter;
		if (decrypt) {
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
		} else {
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
	}
	public void cryptOneBlock(DynByteBuf in, DynByteBuf out) {
		throw new IllegalArgumentException();
	}
	protected void cryptFinal1(DynByteBuf in, DynByteBuf out) throws ShortBufferException, BadPaddingException {
		if (out.writableBytes() < engineGetOutputSize(in.readableBytes())) throw new ShortBufferException();

		if (decrypt) {
			if (in.readableBytes() < AES_BLOCK_SIZE) throw new AEADBadTagException("怪");
			decryptFinal(in, out);
		} else encryptFinal(in, out);

		state = 0;
	}

	public final void insertAAD(DynByteBuf aad) {
		if (state == 0) {
			cryptBegin();
			state = 1;
		} else if (state > 1) throw new IllegalStateException("AAD 必须在加密开始前提供");

		lenAAD += aad.readableBytes();

		long[] h = hash;

		int block = aad.readableBytes() / AES_BLOCK_SIZE;
		while (block-- > 0) {
			h[0] ^= aad.readLong(); h[1] ^= aad.readLong();
			GaloisHash(h, H0, H1);
		}

		if (aadBuffer == null) aadBuffer = new ByteList();
		aadBuffer.put(aad);
	}

	private void cryptBegin() {
		if (encrypt_key == null) throw new IllegalArgumentException("你还没设置密钥");

		if (prng != null) {
			if (prng instanceof HKDFPRNG) ((HKDFPRNG) prng).generate(iv.array(), Unsafe.ARRAY_BYTE_BASE_OFFSET, 12);
			else prng.nextBytes(iv.array());

			iv.rIndex = 0;
			iv.wIndex(AES_BLOCK_SIZE);
			iv.putInt(12, 1);
		}

		long[] h = hash;
		h[0] = h[1] = 0;

		System.arraycopy(iv.array(), 0, counter.array(), 0, AES_BLOCK_SIZE);
		counter.wIndex(AES_BLOCK_SIZE);
		incr(counter.array());

		lenAAD = processed = 0;
	}
	private void encryptFinal(DynByteBuf in, DynByteBuf out) {
		ByteList ctr = counter; ctr.clear();

		int len = in.readableBytes();
		if (len > 0) {
			assert len < AES_BLOCK_SIZE;
			// encrypt ctr inline
			aes_encrypt(encrypt_key, limit, ctr.slice(0, AES_BLOCK_SIZE), ctr);

			processed += len;

			// ctr update final
			int len1 = len;
			while (len1-- > 0) out.put((byte) (in.get() ^ ctr.get()));

			paddedHash(out.slice(out.wIndex()-len, len));
		}

		// final block - lengths
		finalHashBlock();

		ctr.clear();
		aes_encrypt(encrypt_key, limit, iv, ctr);

		ByteList t = getHash();
		len = tagLen;
		while (len-- > 0) out.put((byte) (t.get() ^ ctr.get()));
	}
	private void decryptFinal(DynByteBuf in, DynByteBuf out) throws AEADBadTagException {
		ByteList ctr = counter; ctr.clear();

		int len = in.readableBytes() - AES_BLOCK_SIZE;
		if (len > 0) {
			in.wIndex(in.wIndex() - AES_BLOCK_SIZE);

			// encrypt ctr inline
			aes_encrypt(encrypt_key, limit, ctr.slice(0, AES_BLOCK_SIZE), ctr);

			processed += len;

			paddedHash(in);

			// ctr update final
			while (len-- > 0) out.put((byte) (in.get() ^ ctr.get()));

			in.wIndex(in.wIndex() + AES_BLOCK_SIZE);
		}

		finalHashBlock();

		ctr.clear();
		aes_encrypt(encrypt_key, limit, iv, ctr);

		ByteList t = getHash();
		int v = 0;
		for (int i = tagLen; i > 0; i--) {
			v |= in.get() ^ t.get() ^ ctr.get();
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
		ByteList t = tmp; t.clear();
		t.put(in);

		// zero padded
		byte[] t0 = t.array();
		for (int i = t.wIndex(); i < AES_BLOCK_SIZE; i++) t0[i] = 0;
		t.wIndex(AES_BLOCK_SIZE);

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
	private final ByteList counter = ByteList.allocate(AES_BLOCK_SIZE,AES_BLOCK_SIZE), iv = ByteList.allocate(AES_BLOCK_SIZE,AES_BLOCK_SIZE);
	private void incr(byte[] b) {
		// start from last byte and only go over 4 bytes, i.e. total 32 bits
		int n = AES_BLOCK_SIZE - 1;
		while ((n >= AES_BLOCK_SIZE - 4) && (++b[n] == 0)) {
			n--;
		}
	}
	// endregion
}
