package roj.math;

import org.jetbrains.annotations.Range;
import roj.collect.Int2IntMap;
import roj.collect.IntList;
import roj.compiler.runtime.RtUtil;
import roj.util.Helpers;

import java.util.*;
import java.util.function.ToIntFunction;

/**
 * Math utilities.
 */
public abstract class MathUtils {
	public static final double HALF_PI = Math.PI / 2, TWO_PI = Math.PI * 2;

	public static <T> IntList discretization(Iterable<T> list, ToIntFunction<T> retriever) {
		if (retriever == null) retriever = Helpers.cast((ToIntFunction<Number>) Number::intValue);

		IntList out = new IntList(list instanceof Collection ? ((Collection<T>) list).size() : 10);
		Int2IntMap map = new Int2IntMap();
		for (T t : list) out.add(map.putIntIfAbsent(retriever.applyAsInt(t), map.size()));
		return out;
	}

	public static int clamp(int val, int min, int max) { return val < min ? min : val > max ? max : val; }
	public static long clamp(long val, long min, long max) { return val < min ? min : val > max ? max : val; }
	// 碰到NaN时，返回min
	public static double clamp(double val, double min, double max) { return val >= min ? val > max ? max : val : min; }
	// Will this faster than Math ?
	public static int floor(float value) {
		int i = (int) value;
		return value < i ? i - 1 : i;
	}
	public static int floor(double value) {
		int i = (int) value;
		return value < i ? i - 1 : i;
	}
	public static int ceil(float value) {
		int i = (int) value;
		return value > i ? i + 1 : i;
	}

	public static long multiplyHigh(long x, long y) {return Math.multiplyHigh(x, y);}
	public static long unsignedMultiplyHigh(long x, long y) {return Math.multiplyHigh(x, y) + ((x >> 63) & y) + ((y >> 63) & x);}

	/**
	 * Returns an iterator providing a number sequence. This sequence starts with <i>{@code from}</i> (given as
	 * parameter) and ends with <i>{@code to}</i> (given as parameter). In between, new values are calculated as
	 * sigmoid function e^t(x) / (1 + e^t(x)).
	 *
	 * @param from First number in the sequence
	 * @param to Last number in the sequence
	 * @param steps The length of the sequence (exclusive <i>{@code from}</i> and <i>{@code to}</i>)
	 *
	 * @return an iterator providing a number sequence calculated as sigmoid function.
	 * {@link Iterator#hasNext() hasNext()} returns false, if the sequence has finished, but
	 * {@link Iterator#next() next()} will return <i>{@code to}</i>
	 *
	 * @throws IllegalArgumentException if <i>{@code from}</i> {@code >=} <i>{@code to}</i>
	 * @throws IllegalArgumentException if <i>{@code from}</i> {@code <} <i>{@code 0}</i>
	 * @throws IllegalArgumentException if <i>{@code steps}</i> {@code <} <i>{@code 0}</i>
	 */
	public static PrimitiveIterator.OfInt createSigmoidSequence(int from, int to, int steps) {

		if (from >= to) throw new IllegalArgumentException("from >= to");
		if (from < 0) throw new IllegalArgumentException("from < 0");
		if (steps < 0) throw new IllegalArgumentException("steps < 0");

		return new PrimitiveIterator.OfInt() {
			// e^t(x) / (1 + e^t(x)) in [0, xmax] for t(x) = 8 * x / xmax - 4

			private int step = 0;
			private final int delta = to - from;
			private final int maxStep = steps + 1;

			@Override
			public boolean hasNext() {
				return step <= maxStep;
			}

			@Override
			public int nextInt() {
				if (step > maxStep) throw new NoSuchElementException();
				int s = step++;

				if (s == maxStep) return to;
				else if (s == 0) return from;
				else {
					double x = s / (double) maxStep;
					double tmp = Math.exp(8 * x - 4);
					return (int) (delta * (tmp / (1 + tmp)) + from);
				}
			}

			// result for (from, to, steps) = (0, 20, 18)

			// |20|  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  | x|
			// |19|  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  | x| x|  |
			// |18|  |  |  |  |  |  |  |  |  |  |  |  |  |  |  | x| x|  |  |  |
			// |17|  |  |  |  |  |  |  |  |  |  |  |  |  |  | x|  |  |  |  |  |
			// |16|  |  |  |  |  |  |  |  |  |  |  |  |  | x|  |  |  |  |  |  |
			// |15|  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
			// |14|  |  |  |  |  |  |  |  |  |  |  |  | x|  |  |  |  |  |  |  |
			// |13|  |  |  |  |  |  |  |  |  |  |  | x|  |  |  |  |  |  |  |  |
			// |12|  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
			// |11|  |  |  |  |  |  |  |  |  |  | x|  |  |  |  |  |  |  |  |  |
			// |10|  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
			// | 9|  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
			// | 8|  |  |  |  |  |  |  |  |  | x|  |  |  |  |  |  |  |  |  |  |
			// | 7|  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
			// | 6|  |  |  |  |  |  |  |  | x|  |  |  |  |  |  |  |  |  |  |  |
			// | 5|  |  |  |  |  |  |  | x|  |  |  |  |  |  |  |  |  |  |  |  |
			// | 4|  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
			// | 3|  |  |  |  |  |  | x|  |  |  |  |  |  |  |  |  |  |  |  |  |
			// | 2|  |  |  |  |  | x|  |  |  |  |  |  |  |  |  |  |  |  |  |  |
			// | 1|  |  |  | x| x|  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
			// | 0| x| x| x|  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
			//    | 0| 1| 2| 3| 4| 5| 6| 7| 8| 9|10|11|12|13|14|15|16|17|18|19|
		};
	}

	public static double interpolate(double old, double now, double delta) {
		return old + delta * (now - old);
	}

	public static int getMin2PowerOf(int cap) {
		int n = -1 >>> Integer.numberOfLeadingZeros(cap - 1);
		return (n < 0) ? 1 : (n >= 1073741824) ? 1073741824 : n + 1;
	}

	public static long getMin2PowerOf(long cap) {
		long n = -1L >>> Long.numberOfLeadingZeros(cap - 1);
		return (n < 0) ? 1 : (n >= 4611686018427387904L) ? 4611686018427387904L : n + 1;
	}

	public static double cos(double value) {return sin(HALF_PI + value);}
	/** 精度1e-6 */
	public static double sin(double value) {
		if (value >= 0) {
			if (value <= HALF_PI) {
				return ((((((-0.000960664 * value + 0.0102697866) * value - 0.00198601997) * value - 0.1656067221) * value - 0.0002715666) * value + 1.000026227) * value);
			} else {
				if (value >= TWO_PI) {
					value %= TWO_PI;
				}

				if (value >= Math.PI) {
					return -sin(value - Math.PI);
				}

				if (value > HALF_PI) {
					value = sin(value - HALF_PI);
					value *= value;
					return value > 1 ? 0 : Math.sqrt(1 - value);
				}

				return sin(value);
			}
		} else {
			return -sin(-value);
		}
	}

	public static int average(int[] values) {
		if (values == null || values.length == 0) return 0;
		int sum = 0;
		for (int v : values) sum += v;
		return sum / values.length;
	}

	public static long average(long[] values) {
		if (values == null || values.length == 0) return 0L;
		long sum = 0L;
		for (long v : values) sum += v;
		return sum / values.length;
	}

	public static double[] pdf2cdf(double[] pdf) {
		double[] cdf = new double[pdf.length];
		cdf[0] = pdf[0];
		for (int i = 1; i < cdf.length-1; i++) cdf[i] = pdf[i] + cdf[i-1];
		// Force set last cdf to 1, preventing floating-point summing error in the loop.
		cdf[cdf.length-1] = 1;
		return cdf;
	}
	public static int cdfRandom(Random rand, double[] cdf) {
		double x = rand.nextDouble();
		int pos = Arrays.binarySearch(cdf, x);
		return pos >= 0 ? pos : -pos - 2;
	}
	public static int randomRange(Random rand, int min, int max) {return min + rand.nextInt(max - min + 1);}

	public static int pow(int base, int exponent) {return RtUtil.pow(base, exponent);}

	/**
	 * 计算值离float_num最近的分数，分母不大于max_denominator.
	 * @return {分子,分母}
	 */
	public static int[] closestFraction(double float_num, @Range(from = 2, to = Integer.MAX_VALUE) int max_denominator) {
		double remainder = float_num % 1;
		int[] result;
		if (remainder < 0) {
			result = closestFraction1(-remainder, max_denominator);
			result[0] = -result[0];
		} else {
			result = closestFraction1(remainder, max_denominator);
		}
		result[0] += result[1] * (float_num-remainder);
		return result;
	}

	public static int[] closestFraction1(@Range(from = 0, to = 1) double float_num, @Range(from = 2, to = Integer.MAX_VALUE) int max_denominator) {
		double remainder = float_num;
		int p0 = 1, q0 = 0;
		int p1 = 0, q1 = 1;

		while (Math.abs(remainder) >= 1E-8) {
			remainder = 1 / remainder;
			int ai = (int) remainder;
			int p = ai * p1 + p0;
			int q = ai * q1 + q0;

			if (q > max_denominator) break;

			q0 = q1;p0 = p1;
			q1 = q ;p1 = p ;

			remainder -= ai;
		}

		var error0 = Math.abs(float_num - (double) p0 / q0);
		var error1 = Math.abs(float_num - (double) p1 / q1);
		return error0 < error1 ? new int[]{p0, q0} : new int[]{p1, q1};
	}
}