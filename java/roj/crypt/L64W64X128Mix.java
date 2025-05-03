package roj.crypt;

import static roj.reflect.Unaligned.U;

/**
 * LCG64 + WyHash64 + XorShift128
 * @author Roj234-N
 * @since 2025/5/9 13:22
 */
final class L64W64X128Mix extends WyRand {
	static final long LCG_MULTIPLIER = 0x5851F42D4C957F2DL, LCG_INCREMENT = 0xBL;
	static final long MIX_MULTIPLIER = 0x9E3779B97F4A7C15L;

	private long l, x0, x1;

	public L64W64X128Mix() {}
	public L64W64X128Mix(long seed) {super(seed);}

	@Override
	public void setSeed(long seed) {
		l = seed;
		w = seed += MIX_MULTIPLIER;
		x0 = mixStafford13(seed += MIX_MULTIPLIER);
		x1 = mixStafford13(seed += MIX_MULTIPLIER);

		U.putObject(this, SEED, null);
		U.putBoolean(this, HAVE_NEXT_NEXT_GAUSSIAN, false);
	}

	static long mixStafford13(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	public long nextLong() {
		l = l * LCG_MULTIPLIER + LCG_INCREMENT;

		long a = w + WY_INCREMENT;
		long b = a ^ WY_XOR;
		w = a;
		a = CryptoFactory.wymix(a, b);

		long s1 = x0;
		long s0 = x1;
		s1 ^= s1 << 23;
		x0 = s0;
		x1 = s0 ^ s1 ^ (s0 >>> 17) ^ (s1 >>> 26);

		return (l + a + x1) * MIX_MULTIPLIER;
	}
}