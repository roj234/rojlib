package roj.crypt;

import roj.reflect.Unaligned;

import java.util.Random;

/**
 * @author Roj234-N
 * @since 2025/5/10 5:54
 */
class WyRand extends Random {
	static final long
			SEED = Unaligned.fieldOffset(Random.class, "seed"),
			HAVE_NEXT_NEXT_GAUSSIAN = Unaligned.fieldOffset(Random.class, "haveNextNextGaussian");

	static final long WY_INCREMENT = 0x2d358dccaa6c78a5L, WY_XOR = 0x8bb84b93962eacc9L;

	long w;

	public WyRand() {}
	public WyRand(long seed) {super(seed);}

	@Override
	public void setSeed(long seed) {
		w = seed;
		Unaligned.U.putObject(this, SEED, null);
	}

	@Override
	public double nextGaussian() {
		long r = nextLong();
		return ((r&0x1fffff) + ((r>>>21)&0x1fffff) + ((r>>>42)&0x1fffff)) * 0x1.0p-20 - 3.0;
	}

	@Override
	public double nextDouble() {return (nextLong() >>> 11) * 0x1.0p-53;}

	@Override
	protected int next(int bits) {return (int) (nextLong() >>> (64 - bits));}

	public long nextLong() {
		long a = w + WY_INCREMENT;
		long b = a ^ WY_XOR;
		w = a;
		return CryptoFactory.wymix(a, b);
	}
}