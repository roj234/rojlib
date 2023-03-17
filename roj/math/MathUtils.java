package roj.math;

import roj.collect.Int2IntMap;
import roj.collect.IntList;
import roj.util.Helpers;

import java.util.*;
import java.util.function.ToIntFunction;

/**
 * Math utilities.
 */
public abstract class MathUtils {
	public static final double HALF_PI = Math.PI / 2;
	public static final double TWO_PI = Math.PI * 2;
	public static final double EPS_2 = 1e-14;

	public static int sig(int num) {
		return num == 0 ? 0 : num < 0 ? -1 : 1;
	}

	public static <T> IntList discretization(Iterable<T> list, ToIntFunction<T> retriver) {
		if (retriver == null) retriver = Helpers.cast((ToIntFunction<Number>) Number::intValue);

		IntList out = new IntList(list instanceof Collection ? ((Collection<T>) list).size() : 10);
		Int2IntMap map = new Int2IntMap();
		for (T t : list) out.add(map.putIntIfAbsent(retriver.applyAsInt(t), map.size()));
		return out;
	}

	public static int clamp(int val, int min, int max) {
		return val < min ? min : val > max ? max : val;
	}
	public static long clamp(long val, long min, long max) {
		return val < min ? min : val > max ? max : val;
	}
	public static double clamp(double val, double min, double max) {
		return val < min ? min : val > max ? max : val;
	}

	/**
	 * 投影点
	 *
	 * @param v 还是容器
	 */
	public static Vec3d project(Vec3d point, Vec3d line, Vec3d v) {
		v.set(point)
		 // 1. Point和Line所在的平面的法线
		 .cross(line)
		 // 2. 与Line垂直和(法线垂直 => 平面内)的向量
		 .cross(line);

		double B = (line.x * point.y / line.y - point.x) / (v.x - line.x * v.y / line.y);
		return (Vec3d) v.mul(B).add(point);
	}

	public static boolean nearEps(double d1, double d2, double diff) {
		return Math.abs(d1 - d2) <= diff;
	}

	/**
	 * @see #interpolate(double, double, double, double, double)
	 */
	public static float interpolate(float in, float inMin, float inMax, float outMin, float outMax) {
		if (inMin > inMax) { // reverse
			float t = inMin;
			inMin = inMax;
			inMax = t;
			t = outMin;
			outMin = outMax;
			outMax = t;
		}

		if (in <= inMin) return outMin;
		if (in >= inMax) return outMax;

		float xFrac = (in - inMin) / (inMax - inMin);
		return outMin + xFrac * (outMax - outMin);
	}

	/**
	 * <p>
	 * linearly interpolate for y between [inMin, outMin] to [inMax, outMax] using in
	 * </p>
	 * y = outMin + (outMax - outMin) * (in - inMin) / (inMax - inMin) <br>
	 * For example: <br>
	 * if [inMin, outMin] is [0, 100], and [inMax,outMax] is [1, 200], <br>
	 * then as in increases from 0 to 1, this function will increase from 100 to 200 <br>
	 *
	 * @return linearly interpolated value.  If in is outside the range, clip it to the nearest end
	 */
	public static double interpolate(double in, double inMin, double inMax, double outMin, double outMax) {
		if (inMin > inMax) { // reverse
			double t = inMin;
			inMin = inMax;
			inMax = t;
			t = outMin;
			outMin = outMax;
			outMax = t;
		}

		if (in <= inMin) return outMin;
		if (in >= inMax) return outMax;

		double xFrac = (in - inMin) / (inMax - inMin);
		return outMin + xFrac * (outMax - outMin);
	}

	/**
	 * @see #interpolate(double, double, double, double, double)
	 */
	public static float interpolate(float old, float now, float delta) {
		return old + delta * (now - old);
	}
	/**
	 * @see #interpolate(double, double, double, double, double)
	 */
	public static double interpolate(double old, double now, double delta) {
		return old + delta * (now - old);
	}

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

	public static int getMin2PowerOf(int n) {
		if (n >= 1073741824) return 1073741824;
		n--;
		n |= n >>> 1;
		n |= n >>> 2;
		n |= n >>> 4;
		n |= n >>> 8;
		n |= n >>> 16;
		return (n < 0) ? 1 : n + 1;
	}

	public static float cos(float value) {
		return (float) sin(HALF_PI + value);
	}
	public static double cos(double value) {
		return sin(HALF_PI + value);
	}
	public static float sin(float value) {
		return (float) sin((double) value);
	}
	/**
	 * 使用切比雪夫多项式快速计算sin
	 * <br>
	 * 精度1e-6
	 *
	 * @param value radian
	 *
	 * @return sin value
	 */
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

	public static int floor(float value) {
		int i = (int) value;
		return value < i ? i - 1 : i;
	}

	public static int ceil(float value) {
		int i = (int) value;
		return value > i ? i + 1 : i;
	}

	public static int floor(double value) {
		int i = (int) value;
		return value < i ? i - 1 : i;
	}

    public static float invSqrt(float x) {
        float halfX = 0.5f * x;

        int i = Float.floatToRawIntBits(x); // get bits for floating VALUE
        i = 0x5f375a86 - (i >> 1); // gives initial guess y0
        x = Float.intBitsToFloat(i); // convert bits BACK to float

        x = x * (1.5f - halfX * x * x); // Newton step, repeating increases accuracy
        x = x * (1.5f - halfX * x * x);
        x = x * (1.5f - halfX * x * x);

        return x;
    }

    /*public static double invSqrt(double x) {
        double halfX = 0.5f * x;

        long i = Double.doubleToRawLongBits(x);
        i = 6910469410427058090L - (i >> 1);
        x = Double.longBitsToDouble(i);

        x = x * (1.5f - halfX * x * x);
        x = x * (1.5f - halfX * x * x);
        x = x * (1.5f - halfX * x * x);
        x = x * (1.5f - halfX * x * x);

        return x;
    }*/

	public static float sqrt(float x) {
		if (x < 0) throw new IllegalArgumentException("Must be non-negative");
		if (x == 0) return 0;
		return 1 / invSqrt(x);
	}

	public static int average(int[] values) {
		if (values == null || values.length == 0) return 0;
		int sum = 0;
		for (int v : values)
			sum += v;
		return sum / values.length;
	}

	public static long average(long[] values) {
		if (values == null || values.length == 0) return 0L;
		long sum = 0L;
		for (long v : values)
			sum += v;
		return sum / values.length;
	}

	public static double[] pdf2cdf(double[] pdf) {
		double[] cdf = pdf.clone();
		for (int i = 1; i < cdf.length-1; i++) cdf[i] += cdf[i - 1];
		// Force set last cdf to 1, preventing floating-point summing error in the loop.
		cdf[cdf.length-1] = 1;
		return cdf;
	}

	public static int cdfRandom(Random rand, double[] targetCdf) {
		double x = rand.nextDouble();

		for (int i = 0; i < targetCdf.length; i++) {
			if (x < targetCdf[i]) return i;
		}
		throw new IllegalArgumentException("targetCdf");
	}

	public static int randomRange(Random rand, int min, int max) {
		return min + rand.nextInt(max - min + 1);
	}

}