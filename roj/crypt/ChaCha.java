package roj.crypt;

import roj.io.IOUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import sun.misc.Unsafe;

import javax.crypto.ShortBufferException;
import javax.crypto.spec.IvParameterSpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;

/**
 * @author solo6975
 * @since 2022/2/14 9:03
 */
public class ChaCha extends RCipherSpi {
	//       cccccccc  cccccccc  cccccccc  cccccccc
	//       kkkkkkkk  kkkkkkkk  kkkkkkkk  kkkkkkkk
	//       kkkkkkkk  kkkkkkkk  kkkkkkkk  kkkkkkkk
	//       bbbbbbbb  nnnnnnnn  nnnnnnnn  nnnnnnnn
	final int[] key = new int[16], tmp = new int[16];
	final int round;
	private int pos;

	SecureRandom rng;

	public ChaCha() { this(10); }
	public ChaCha(int round) {
		this.round = round;

		int[] T = key;
		T[0] = 0x61707865;
		T[1] = 0x3320646e;
		T[2] = 0x79622d32;
		T[3] = 0x6b206574;
	}

	@Override
	public void init(int flags, byte[] pass, AlgorithmParameterSpec par, SecureRandom random) throws InvalidAlgorithmParameterException, InvalidKeyException {
		if (pass.length != 32) throw new InvalidKeyException("Key should be 256 bits length");

		ByteList b = IOUtil.SharedCoder.get().wrap(pass);
		for (int i = 4; i < 12; i++) key[i] = b.readIntLE();

		if (par != null) {
			if (random != null) throw new IllegalArgumentException("IV和随机器必须也只能提供一个");

			byte[] newIv;
			if (par instanceof IvParameterSpec) {
				newIv = ((IvParameterSpec) par).getIV();
			} else if (par instanceof IvParameterSpecNC) {
				newIv = ((IvParameterSpecNC) par).getIV();
				if (par instanceof AEADParameterSpec) {
					int size = ((AEADParameterSpec) par).getTagSize();
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

			this.rng = random;
		}

		reset();
	}

	public final void reset() {
		if (rng != null) rngIv();
		key[12] = 0;
		pos = 64;
	}

	// Note that it is not acceptable to use a truncation of a counter encrypted with a
	// 128-bit or 256-bit cipher, because such a truncation may repeat after a short time.
	void rngIv() {
		if (rng instanceof HKDFPRNG) {
			((HKDFPRNG) rng).generate(key, Unsafe.ARRAY_INT_BASE_OFFSET + 13 * 4, 3 * 4);
		} else {
			key[13] = rng.nextInt();
			key[14] = rng.nextInt();
			key[15] = rng.nextInt();
		}
	}

	void setIv(byte[] iv) throws InvalidAlgorithmParameterException {
		if (iv.length != 12) throw new InvalidAlgorithmParameterException("Nonce 长度应当是12, got " + iv.length);
		ByteList b = IOUtil.SharedCoder.get().wrap(iv);
		key[13] = b.readIntLE();
		key[14] = b.readIntLE();
		key[15] = b.readIntLE();
	}

	@Override
	public final void crypt(DynByteBuf in, DynByteBuf out) throws ShortBufferException {
		if (out.writableBytes() < in.readableBytes()) throw new ShortBufferException();

		int[] T = this.tmp;
		int i = this.pos;

		if ((i & 3) != 0) {
			int j = Integer.reverseBytes(T[i >> 2]) >>> ((i & 3) << 3);
			while (in.isReadable() && (i & 3) != 0) {
				out.put((byte) (in.get() ^ j));
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
				out.put((byte) (in.get() ^ i));
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
		d = Conv.IRL(d, 16);
		c += d;
		b ^= c;
		b = Conv.IRL(b, 12);
		a += b;
		d ^= a;
		d = Conv.IRL(d, 8);
		c += d;
		b ^= c;
		b = Conv.IRL(b, 7);

		T[x] = a;
		T[y] = b;
		T[z] = c;
		T[w] = d;
	}
}
