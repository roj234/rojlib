package roj.crypt;

import roj.optimizer.FastVarHandle;
import roj.reflect.Telescope;

import java.lang.invoke.VarHandle;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import static roj.reflect.Unsafe.U;

/**
 * @author Roj234-N
 * @since 2025/5/10 5:54
 */
@FastVarHandle
class WyRand extends Random {
	private static final VarHandle
			SEED = Telescope.trustedLookup().findVarHandle(Random.class, "seed", AtomicLong.class),
			HAVE_NEXT_NEXT_GAUSSIAN = Telescope.trustedLookup().findVarHandle(Random.class, "haveNextNextGaussian", boolean.class);

	static final long WY_INCREMENT = 0x2d358dccaa6c78a5L, WY_XOR = 0x8bb84b93962eacc9L;

	long w;

	public WyRand() {}
	public WyRand(long seed) {super(seed);}

	@Override
	public void setSeed(long seed) {
		w = seed;
		SEED.set(this, null);
		HAVE_NEXT_NEXT_GAUSSIAN.set(this, false);
	}

	/*@Override
	public double nextGaussian() {
		long r = nextLong();
		return ((r&0x1fffff) + ((r>>>21)&0x1fffff) + ((r>>>42)&0x1fffff)) * 0x1.0p-20 - 3.0;
	}*/

	@Override
	public double nextDouble() {return (nextLong() >>> 11) * 0x1.0p-53;}

	@Override
	protected int next(int bits) {return (int) (nextLong() >>> (64 - bits));}

	public long nextLong() {
		long a = w + WY_INCREMENT;
		long b = a ^ WY_XOR;
		w = a;
		return wymix(a, b);
	}

	static final long[] WYHASH_SECRET = {
			0x2d358dccaa6c78a5L,
			0x8bb84b93962eacc9L,
			0x4b33a62ed433d4a3L,
			0x4d5a2da51de1aa47L
	};

	/**
	 * translated by Roj234-N
	 * @since 2025/5/9 15:17
	 */
	// This is free and unencumbered software released into the public domain under The Unlicense (http://unlicense.org/)
	// main repo: https://github.com/wangyi-fudan/wyhash
	// author: 王一 Wang Yi <godspeed_china@yeah.net>
	// contributors: Reini Urban, Dietrich Epp, Joshua Haberman, Tommy Ettinger, Daniel Lemire, Otmar Ertl, cocowalla, leo-yuriev, Diego Barrios Romero, paulie-g, dumblob, Yann Collet, ivte-ms, hyb, James Z.M. Gao, easyaspi314 (Devin), TheOneric
	public static long wyhash(Object p1, long p2, int len, long seed, long[] secret) {
		seed ^= wymix(seed ^ secret[0], secret[1]);
		long a, b;

		if (/*_likely_*/len <= 16) {
			if (/*_likely_*/len >= 4) {
				a = ((U.get32UL(p1, p2) & 0xFFFFFFFFL) << 32) | U.get32UL(p1, p2 + ((len >> 3) << 2)) & 0xFFFFFFFFL;
				b = ((U.get32UL(p1, p2 + len - 4) & 0xFFFFFFFFL) << 32) | U.get32UL(p1, p2 + len - 4 - ((len >> 3) << 2)) & 0xFFFFFFFFL;
			} else if (/*_likely_*/len > 0) {
				a = wyr3(p1, p2, len);
				b = 0;
			} else {
				a = b = 0;
			}
		} else {
			int i = len;
			if (/*_unlikely_*/i >= 48) {
				long see1 = seed, see2 = seed;
				do {
					seed = wymix(U.get64UL(p1, p2) ^ secret[1], U.get64UL(p1, p2 + 8) ^ seed);
					see1 = wymix(U.get64UL(p1, p2 + 16) ^ secret[2], U.get64UL(p1, p2 + 24) ^ see1);
					see2 = wymix(U.get64UL(p1, p2 + 32) ^ secret[3], U.get64UL(p1, p2 + 40) ^ see2);
					p2 += 48;
					i -= 48;
				} while (i >= 48);
				seed ^= see1 ^ see2;
			}
			while (i > 16) {
				seed = wymix(U.get64UL(p1, p2) ^ secret[1], U.get64UL(p1, p2 + 8) ^ seed);
				i -= 16;
				p2 += 16;
			}
			a = U.get64UL(p1, p2 + i - 16);
			b = U.get64UL(p1, p2 + i - 8);
		}

		a ^= secret[1];
		b ^= seed;

		long hi = Math.multiplyHigh(a, b);
		long lo = a * b;
		a = lo;
		b = hi;

		return wymix(a ^ secret[0] ^ len, b ^ secret[1]);
	}

	public static long wymix(long a, long b) {
		long hi = Math.multiplyHigh(a, b);
		long lo = a * b;
		a = lo;
		b = hi;

		return a ^ b;
	}

	private static long wyr3(Object base, long offset, long k) {
		long b0 = U.getByte(base, offset) & 0xFFL;
		long b1 = U.getByte(base, offset + (k >> 1)) & 0xFFL;
		long b2 = U.getByte(base, offset + k - 1) & 0xFFL;
		return (b0 << 16) | (b1 << 8) | b2;
	}
}