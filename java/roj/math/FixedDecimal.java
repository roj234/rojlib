package roj.math;

/**
 * @author Roj234
 * @since 2025/2/11 0011 14:56
 */
public class FixedDecimal implements Comparable<FixedDecimal> {
	public long integral;
	public long fractional;
	private static final long DECIMAL_PLACES = 1000000L;
	public static final FixedDecimal ZERO = new FixedDecimal(0);

	// 构造函数
	public FixedDecimal(long integral, long fractional) {
		this.integral = integral;
		this.fractional = fractional;
		normalize();
	}
	public FixedDecimal(long integral) {
		this.integral = integral;
	}

	// 规范化小数部分，确保在合理范围内
	private void normalize() {
		if (fractional >= DECIMAL_PLACES) {
			integral += fractional / DECIMAL_PLACES;
			fractional %= DECIMAL_PLACES;
		} else if (fractional < 0) {
			long borrow = (-fractional - 1) / DECIMAL_PLACES + 1;
			integral -= borrow;
			fractional += borrow * DECIMAL_PLACES;
		}
	}

	// 加法
	public FixedDecimal add(FixedDecimal other) {
		if (isZero()) return other;
		if (other.isZero()) return this;

		long newIntegral = integral + other.integral;
		long newFractional = fractional + other.fractional;
		return new FixedDecimal(newIntegral, newFractional);
	}

	// 减法
	public FixedDecimal subtract(FixedDecimal other) {
		if (isZero()) return other;
		if (other.isZero()) return this;

		long newIntegral = integral - other.integral;
		long newFractional = fractional - other.fractional;
		return new FixedDecimal(newIntegral, newFractional);
	}

	// 乘法
	public FixedDecimal multiply(FixedDecimal other) {
		if (other.isZero()) return ZERO;
		if (isZero()) return ZERO;

		long thisValue = integral * DECIMAL_PLACES + fractional;
		long otherValue = other.integral * DECIMAL_PLACES + other.fractional;
		long product = thisValue * otherValue;
		long newIntegral = product / (DECIMAL_PLACES * DECIMAL_PLACES);
		long newFractional = (product % (DECIMAL_PLACES * DECIMAL_PLACES)) / DECIMAL_PLACES;
		return new FixedDecimal(newIntegral, newFractional);
	}
	public FixedDecimal multiply(long scalar) {
		if (scalar == 0) return ZERO;
		if (isZero()) return ZERO;
		return new FixedDecimal(integral * scalar, fractional * scalar);
	}

	// 除法
	public FixedDecimal divide(FixedDecimal other) {
		if (other.isZero()) throw new ArithmeticException("Division by zero");
		if (isZero()) return ZERO;

		long thisValue = integral * DECIMAL_PLACES + fractional;
		long otherValue = other.integral * DECIMAL_PLACES + other.fractional;
		long quotient = (thisValue * DECIMAL_PLACES) / otherValue;
		long newIntegral = quotient / DECIMAL_PLACES;
		long newFractional = quotient % DECIMAL_PLACES;
		return new FixedDecimal(newIntegral, newFractional);
	}
	public FixedDecimal divide(long scalar) {
		if (scalar == 0) throw new ArithmeticException("Division by zero");
		if (isZero()) return ZERO;

		long thisValue = integral * DECIMAL_PLACES + fractional;
		long quotient = thisValue / scalar;
		long newIntegral = quotient / DECIMAL_PLACES;
		long newFractional = quotient % DECIMAL_PLACES;
		return new FixedDecimal(newIntegral, newFractional);
	}

	public boolean isZero() {return (integral|fractional) == 0;}

	// 比较大小
	public int compareTo(FixedDecimal other) {
		if (integral != other.integral) {
			return Long.compare(integral, other.integral);
		}
		return Long.compare(fractional, other.fractional);
	}

	// 转换为整数
	public long toLong() {return integral;}
	// 转换为浮点数
	public double toDouble() {return integral + (double) fractional / DECIMAL_PLACES;}

	@Override public String toString() {return String.format("%d.%06d", integral, fractional);}

	public static void main(String[] args) {
		FixedDecimal num1 = new FixedDecimal(3, 500000L); // 3.5
		FixedDecimal num2 = new FixedDecimal(1, 200000L); // 1.2

		// 加法
		FixedDecimal sum = num1.add(num2);
		System.out.println("加法结果: " + sum);

		// 减法
		FixedDecimal diff = num1.subtract(num2);
		System.out.println("减法结果: " + diff);

		// 乘法
		FixedDecimal product = num1.multiply(num2);
		System.out.println("乘法结果: " + product);

		// 除法
		FixedDecimal quotient = num1.divide(num2);
		System.out.println("除法结果: " + quotient);

		// 比较
		int comparison = num1.compareTo(num2);
		System.out.println("比较结果: " + comparison);

		// 转换为整数
		long longValue = num1.toLong();
		System.out.println("转换为整数: " + longValue);

		// 转换为浮点数
		double doubleValue = num1.toDouble();
		System.out.println("转换为浮点数: " + doubleValue);
	}
}