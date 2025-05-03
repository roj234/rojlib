package roj.crypt;

import java.util.Random;

import static roj.crypt.L64W64X128Mix.HAVE_NEXT_NEXT_GAUSSIAN;
import static roj.crypt.L64W64X128Mix.SEED;
import static roj.reflect.Unaligned.U;

/**
 * @implNote MT19937 is not thread safe
 * @author Roj234
 * @since 2022/11/14 22:21
 */
@Deprecated
final class MT19937 extends Random {
	private int i;
	private final int[] MT = new int[624];
	private long _seed;

	public MT19937() { setSeed(_seed); }
	public MT19937(long seed) { super(0); setSeed(seed); }

	public final void nextBytes(byte[] bytes, int i, int len) {
		while (i < len) {
			for (int rnd = nextInt(),
				 n = Math.min(len - i, Integer.SIZE/Byte.SIZE);
				 n-- > 0; rnd >>= Byte.SIZE)
				bytes[i++] = (byte)rnd;
		}
	}

	@Override
	public void setSeed(long seed) {
		// Random will invoke setSeed() before this class initialize
		if (MT == null) {
			_seed = seed;
			U.putObject(this, SEED, null);
			return;
		}

		MT[0] = (int) (seed ^ (seed >>> 32));
		for(int i = 1; i < 624; i++)
			MT[i] = 1812433253 * (MT[i-1] ^ (MT[i-1] >>> 30)) + i;
		this.i = 0;


		U.putBoolean(this, HAVE_NEXT_NEXT_GAUSSIAN, false);
	}

	private void nextIter() {
		int[] mt = MT;
		for(int i = 0; i < 624; i++) {
			int y = (mt[i] & 0x80000000) + (mt[(i+1) % 624] & 0x7fffffff);
			int x = mt[(i+397) % 624] ^ (y >>> 1);
			if ((y & 1) != 0)
				x ^= -172748368;

			mt[i] = x;
		}
		i = 0;
	}

	@Override
	protected int next(int bits) {
		if(i == 624) nextIter();

		int y = MT[i++];
		y ^= (y >>> 11);
		y ^= ((y << 7) & -1658038656);
		y ^= ((y << 15) & -272236544);
		y ^= (y >>> 18);

		if (bits == 32) return y;
		return y & ((1 << bits) - 1);
	}
}
