package roj.crypt;

import roj.reflect.Unaligned;
import roj.util.DynByteBuf;

import javax.crypto.ShortBufferException;
import javax.crypto.spec.IvParameterSpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;

import static java.lang.Integer.rotateLeft;

/**
 * @author solo6975
 * @since 2022/2/14 9:03
 */
class ChaCha extends RCipherSpi {
	//       cccccccc  cccccccc  cccccccc  cccccccc
	//       kkkkkkkk  kkkkkkkk  kkkkkkkk  kkkkkkkk
	//       kkkkkkkk  kkkkkkkk  kkkkkkkk  kkkkkkkk
	//       bbbbbbbb  nnnnnnnn  nnnnnnnn  nnnnnnnn
	final int[] key = new int[16], tmp = new int[16];
	final int round;
	private int pos;

	ChaCha() {this(10);}
	ChaCha(int round) {
		this.round = round;

		int[] T = key;
		T[0] = 0x61707865;
		T[1] = 0x3320646e;
		T[2] = 0x79622d32;
		T[3] = 0x6b206574;
	}

	@Override
	public void init(int flags, byte[] key, AlgorithmParameterSpec par, SecureRandom random) throws InvalidAlgorithmParameterException, InvalidKeyException {
		if (key.length != 32) throw new InvalidKeyException("Key should be 256 bits length");

		for (int i = 4; i < 12; i++) {
			this.key[i] = Unaligned.U.get32UL(key, Unaligned.ARRAY_BYTE_BASE_OFFSET + ((long) i << 2));
		}

		if (par != null) {
			if (random != null) throw new IllegalArgumentException("IV和随机器必须也只能提供一个");

			byte[] newIv;
			if (par instanceof IvParameterSpec spec) {
				newIv = spec.getIV();
			} else if (par instanceof IvParameterSpecNC) {
				newIv = ((IvParameterSpecNC) par).getIV();
				if (par instanceof AEADParameterSpec spec) {
					int size = spec.getTagSize();
					if (size != 0 && size != 16) {
						throw new InvalidAlgorithmParameterException("tag size无效, 只能是16");
					}
				}
			} else {
				throw new InvalidAlgorithmParameterException();
			}

			setIv(newIv);
		} else {
			if (random == null) throw new IllegalArgumentException("IV和随机器必须也只能提供一个");
			randIV(random);
		}

		reset();
	}

	final void reset() {
		key[12] = 0;
		pos = 64;
	}

	// Note that it is not acceptable to use a truncation of a counter encrypted with a
	// 128-bit or 256-bit cipher, because such a truncation may repeat after a short time.
	void randIV(SecureRandom rnd) {
		key[13] = rnd.nextInt();
		key[14] = rnd.nextInt();
		key[15] = rnd.nextInt();
	}
	void incrIV() {
		if (++key[15] == 0)
			if (++key[14] == 0)
				key[13]++;
	}
	void setIv(byte[] iv) throws InvalidAlgorithmParameterException {
		if (iv.length != 12) throw new InvalidAlgorithmParameterException("iv.length("+iv.length+") != 12");
		key[13] = Unaligned.U.get32UL(iv, Unaligned.ARRAY_BYTE_BASE_OFFSET);
		key[14] = Unaligned.U.get32UL(iv, Unaligned.ARRAY_BYTE_BASE_OFFSET + 4);
		key[15] = Unaligned.U.get32UL(iv, Unaligned.ARRAY_BYTE_BASE_OFFSET + 8);
	}

	@Override
	public final void crypt(DynByteBuf in, DynByteBuf out) throws ShortBufferException {
		if (out.writableBytes() < in.readableBytes()) throw new ShortBufferException();

		int[] T = this.tmp;
		int i = this.pos;

		if ((i & 3) != 0) {
			int j = Integer.reverseBytes(T[i >> 2]) >>> ((i & 3) << 3);
			while (in.isReadable() && (i & 3) != 0) {
				out.put((byte) (in.readByte() ^ j));
				j >>>= 8;
				i++;
			}
		}

		while (in.readableBytes() >= 4) {
			if (i == 64) {
				KeyStream();
				i = 0;
			}
			out.putInt(in.readInt() ^ T[i >> 2]);
			i += 4;
		}

		if (in.isReadable()) {
			if (i == 64) {
				KeyStream();
				i = 0;
			}
			this.pos = i + in.readableBytes();
			i = Integer.reverseBytes(T[i >> 2]);
			while (in.isReadable()) {
				out.put((byte) (in.readByte() ^ i));
				i >>>= 8;
			}
		} else {
			this.pos = i;
		}
	}
	protected final void cryptFinal1(DynByteBuf in, DynByteBuf out) { reset(); }

	final void KeyStream() {
		int[] Src = key;
		int[] Dst = tmp;

		System.arraycopy(Src, 0, Dst, 0, 16);
		Round(Dst, round);
		for (int i = 0; i < 16; i++) Dst[i] = Integer.reverseBytes(Dst[i] + Src[i]);
		Src[12]++; // Counter
	}

	static void Round(int[] T, int round) {
		while (round-- > 0) {
			Quarter(T, 0, 4, 8, 12);
			Quarter(T, 1, 5, 9, 13);
			Quarter(T, 2, 6, 10, 14);
			Quarter(T, 3, 7, 11, 15);

			Quarter(T, 0, 5, 10, 15);
			Quarter(T, 1, 6, 11, 12);
			Quarter(T, 2, 7, 8, 13);
			Quarter(T, 3, 4, 9, 14);
		}
	}
	private static void Quarter(int[] T, int x, int y, int z, int w) {
		int a = T[x], b = T[y], c = T[z], d = T[w];

		a += b;
		d ^= a;
		d = rotateLeft(d, 16);
		c += d;
		b ^= c;
		b = rotateLeft(b, 12);
		a += b;
		d ^= a;
		d = rotateLeft(d, 8);
		c += d;
		b ^= c;
		b = rotateLeft(b, 7);

		T[x] = a;
		T[y] = b;
		T[z] = c;
		T[w] = d;
	}
}