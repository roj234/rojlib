package ilib.api;

import roj.math.MathUtils;

import java.util.Random;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class RandomEntry {
	public final int min, add;
	public final double[] cdf;

	public static final Random rand = new Random();

	public RandomEntry(int min, int max, double[] factor) {
		this.min = min; //1
		this.add = max - min; //1
		if (add == 0 || factor == null) {
			this.cdf = null;
		} else {
			this.cdf = MathUtils.pdf2cdf(factor); // 0.7 1
		}
	}

	public int get() {
		if (add == 0) return min;
		if (cdf == null) return MathUtils.randomRange(rand, min, min + add);
		return min + MathUtils.cdfRandom(rand, cdf);
	}
}
