package roj.misc;

import roj.annotation.MayMutate;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;
import java.util.Random;

/**
 * @author Roj234
 * @since 2026/01/25 01:59
 */
public abstract class WeightedRandom {
	/**
	 * @param pdf 概率密度函数 (probability density function)
	 */
	public static WeightedRandom get(@MayMutate double[] pdf) {
		if (pdf.length < 8) return new CDF(pdf);
		return new WVAlias(pdf);
	}

	public abstract int sample(Random rand);

	public static final class CDF extends WeightedRandom {
		private final double[] cdf;

		public CDF(double[] pdf) {this.cdf = makeCDF(pdf);}

		@Override
		public int sample(Random rand) {return sample(cdf, rand);}

		public static double[] makeCDF(double[] pdf) {
			double[] cdf = new double[pdf.length];
			cdf[0] = pdf[0];
			for (int i = 1; i < cdf.length-1; i++) cdf[i] = pdf[i] + cdf[i-1];
			// Force set last cdf to 1, preventing floating-point summing error in the loop.
			cdf[cdf.length-1] = 1;
			return cdf;
		}

		public static int sample(double[] cdf, Random rand) {
			double x = rand.nextDouble();
			int pos = Arrays.binarySearch(cdf, x);
			return pos >= 0 ? pos : -pos - 1;
		}
	}

	/**
	 * 使用 Walker-Vose Alias Method 在常数时间内实现加权采样
	 */
	public static final class WVAlias extends WeightedRandom {
		private final int[] alias;
		private final double[] probability;
		private final int count;

		public WVAlias(double[] pdf) {
			this.count = pdf.length;
			this.alias = new int[count];
			this.probability = new double[count];

			double sum = 0;
			for (double p : pdf) sum += p;

			var probs = this.probability;

			int size = Math.max(16, pdf.length / 2);
			Queue<Integer> small = new ArrayDeque<>(size);
			Queue<Integer> large = new ArrayDeque<>(size);

			for (int i = 0; i < count; i++) {
				// 归一化
				probs[i] = pdf[i] * count / sum;
				if (probs[i] < 1.0) small.add(i);
				else large.add(i);
			}

			while (!small.isEmpty() && !large.isEmpty()) {
				int less = small.remove();
				int more = large.remove();

				alias[less] = more;

				// 这种计算方式误差最小
				probs[more] -= 1.0 - probs[less];

				if (probs[more] < 1.0) small.add(more);
				else large.add(more);
			}

			// probs[i]几乎是1，但是关乎逻辑正确性（
			while (!large.isEmpty()) probs[large.remove()] = 1.0;
			while (!small.isEmpty()) probs[small.remove()] = 1.0;
		}

		@Override
		public int sample(Random rand) {
			int i = rand.nextInt(count);
			boolean useAlias = rand.nextDouble() > probability[i];
			return useAlias ? alias[i] : i;
		}
	}
}
