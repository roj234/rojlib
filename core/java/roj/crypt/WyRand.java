package roj.crypt;

import roj.optimizer.FastVarHandle;
import roj.reflect.Telescope;

import java.lang.invoke.VarHandle;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Roj234-N
 * @since 2025/5/10 5:54
 */
@FastVarHandle
class WyRand extends Random {
	private static final VarHandle
			SEED = Telescope.lookup().findVarHandle(Random.class, "seed", AtomicLong.class),
			HAVE_NEXT_NEXT_GAUSSIAN = Telescope.lookup().findVarHandle(Random.class, "haveNextNextGaussian", boolean.class);

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
		return CryptoFactory.wymix(a, b);
	}
}