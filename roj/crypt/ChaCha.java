package roj.crypt;

import roj.util.DynByteBuf;

import javax.crypto.ShortBufferException;
import java.util.Random;

/**
 * @author solo6975
 * @since 2022/2/14 9:03
 */
public class ChaCha implements CipheR {
	public static final String COUNTER = "COUNTER", NONCE = "IV", RANDOM_GENERATE_NONCE = "PRNG";

	//       cccccccc  cccccccc  cccccccc  cccccccc
	//       kkkkkkkk  kkkkkkkk  kkkkkkkk  kkkkkkkk
	//       kkkkkkkk  kkkkkkkk  kkkkkkkk  kkkkkkkk
	//       bbbbbbbb  nnnnnnnn  nnnnnnnn  nnnnnnnn
	final int[] key = new int[16], tmp = new int[16];
	final int round;
	private int pos;

	public ChaCha(int round) {
		this.round = round;

		int[] T = key;
		T[0] = 0x61707865;
		T[1] = 0x3320646e;
		T[2] = 0x79622d32;
		T[3] = 0x6b206574;
		reset();
	}

	public ChaCha() {
		this(10);
	}

	public void reset() {
		key[12] = 0;
		pos = 64;
	}

	@Override
	public String getAlgorithm() {
		return "ChaCha";
	}

	@Override
	public int getMaxKeySize() {
		return 32;
	}

	@Override
	public void setKey(byte[] pass, int flags) {
		if (pass.length > 32) throw new IllegalArgumentException("len > 32: " + pass.length);
		for (int i = 4; i < 12; i++) key[i] = 0;
		Conv.b2i_LE(pass, 0, pass.length, key, 4);
		reset();
	}

	@Override
	public void setOption(String key, Object value) {
		switch (key) {
			case NONCE:
				setNonce((byte[]) value);
				break;
			case COUNTER:
				this.key[12] = (int) value;
				break;
			case RANDOM_GENERATE_NONCE:
				Random rng = (Random) value;
				this.key[13] = rng.nextInt();
				this.key[14] = rng.nextInt();
				this.key[15] = rng.nextInt();
				break;
		}
		reset();
	}

	public void setNonce(byte[] v) {
		if (v.length < 12) throw new IllegalArgumentException("Nonce(IV) should be 96 bits length");
		Conv.b2i_LE(v, 0, 12, key, 13);
	}

	@Override
	public int getBlockSize() {
		return 0;
	}

	@Override
	public void crypt(DynByteBuf in, DynByteBuf out) throws ShortBufferException {
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
		d = Conv.IRL1(d, 16);
		c += d;
		b ^= c;
		b = Conv.IRL1(b, 12);
		a += b;
		d ^= a;
		d = Conv.IRL1(d, 8);
		c += d;
		b ^= c;
		b = Conv.IRL1(b, 7);

		T[x] = a;
		T[y] = b;
		T[z] = c;
		T[w] = d;
	}
}
