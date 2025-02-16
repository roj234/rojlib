package roj.math;

/**
 * 定点数
 * @author Roj234
 * @since 2025/2/11 0011 14:56
 */
public class FixedDecimal implements Comparable<FixedDecimal> {
	public long integral;
	public long fractional;
	private static final int DECIMAL_PLACES = 100000;
	private static final S128i DECIMAL_S128I = new S128i(DECIMAL_PLACES);
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
			integral = Math.addExact(integral, fractional / DECIMAL_PLACES);
			fractional %= DECIMAL_PLACES;
		} else if (fractional < 0) {
			long borrow = (-fractional - 1) / DECIMAL_PLACES + 1;
			integral = Math.subtractExact(integral, borrow);
			fractional += borrow * DECIMAL_PLACES;
		}
	}

	// 加法
	public FixedDecimal add(FixedDecimal other) {
		if (isZero()) return other;
		if (other.isZero()) return this;

		long newIntegral = Math.addExact(integral, other.integral);
		long newFractional = fractional + other.fractional;
		return new FixedDecimal(newIntegral, newFractional);
	}

	// 减法
	public FixedDecimal subtract(FixedDecimal other) {
		if (isZero()) return other;
		if (other.isZero()) return this;

		long newIntegral = Math.subtractExact(integral, other.integral);
		long newFractional = fractional - other.fractional;
		return new FixedDecimal(newIntegral, newFractional);
	}

	// 乘法
	public FixedDecimal multiply(FixedDecimal other) {
		if (other.isZero() || isZero()) return ZERO;

		long thisValue1 = integral * DECIMAL_PLACES + fractional;
		long otherValue1 = other.integral * DECIMAL_PLACES + other.fractional;
		long product1 = thisValue1 * otherValue1;

		if (((Math.abs(integral)|Math.abs(other.integral)|Math.abs(thisValue1)|Math.abs(otherValue1)) >>> 31) != 0) {
			if (product1/thisValue1 != otherValue1 || thisValue1/DECIMAL_PLACES != integral || otherValue1/DECIMAL_PLACES != otherValue1) {

				S128i thisValue = this.toS128i();
				S128i otherValue = other.toS128i();
				S128i product = thisValue.multiply(otherValue);

				S128i divisor = new S128i(DECIMAL_PLACES).multiply(DECIMAL_PLACES);
				S128i[] divideResult = product.divideAndRemainder(divisor);
				S128i remainder = divideResult[1];

				long newIntegral = divideResult[0].longValueExact();
				long newFractional = remainder.divide(new S128i(DECIMAL_PLACES)).longValueExact();

				return new FixedDecimal(newIntegral, newFractional);
			}
		}

		long newIntegral = product1 / ((long) DECIMAL_PLACES * DECIMAL_PLACES);
		long newFractional = (product1 % ((long) DECIMAL_PLACES * DECIMAL_PLACES)) / DECIMAL_PLACES;
		return new FixedDecimal(newIntegral, newFractional);
	}
	public FixedDecimal multiply(long scalar) {
		if (scalar == 0) return ZERO;
		if (isZero()) return ZERO;

		if (((Math.abs(integral)|fractional|DECIMAL_PLACES) >>> 31) != 0) {
			if ((integral * DECIMAL_PLACES)/DECIMAL_PLACES != integral || fractional * scalar / scalar != fractional) {
				S128i[] divideAndRemainder = toS128i().multiply(scalar).divideAndRemainder(DECIMAL_S128I);
				return new FixedDecimal(divideAndRemainder[0].longValueExact(), divideAndRemainder[1].longValue());
			}
		}

		return new FixedDecimal(integral * scalar, fractional * scalar);
	}

	// 除法
	public FixedDecimal divide(FixedDecimal other) {
		if (other.isZero()) throw new ArithmeticException("Division by zero");
		if (isZero()) return ZERO;

		long thisValue = integral * DECIMAL_PLACES + fractional;
		long otherValue = other.integral * DECIMAL_PLACES + other.fractional;
		long product = thisValue * DECIMAL_PLACES;

		if (((Math.abs(integral)|Math.abs(other.integral)|DECIMAL_PLACES|Math.abs(thisValue)) >>> 31) != 0) {
			if (thisValue/DECIMAL_PLACES != integral || otherValue/DECIMAL_PLACES != other.integral || product/DECIMAL_PLACES != thisValue) {
				return divS128(other.toS128i());
			}
		}

		long quotient = product / otherValue;
		long newIntegral = quotient / DECIMAL_PLACES;
		long newFractional = quotient % DECIMAL_PLACES;
		return new FixedDecimal(newIntegral, newFractional);
	}
	public FixedDecimal divide(long scalar) {
		if (scalar == 0) throw new ArithmeticException("Division by zero");
		if (isZero()) return ZERO;

		long thisValue = integral * DECIMAL_PLACES + fractional;

		if (((Math.abs(integral)|DECIMAL_PLACES) >>> 31) != 0) {
			if (thisValue/DECIMAL_PLACES != integral) {
				return divS128(new S128i(scalar));
			}
		}

		long quotient = thisValue / scalar;
		long newIntegral = quotient / DECIMAL_PLACES;
		long newFractional = quotient % DECIMAL_PLACES;
		return new FixedDecimal(newIntegral, newFractional);
	}
	private FixedDecimal divS128(S128i other) {
		S128i newValue = this.toS128i();
		newValue = newValue.multiply(DECIMAL_PLACES, newValue).divide(other);

		S128i[] divideResult = newValue.divideAndRemainder(DECIMAL_S128I);
		long newIntegral = divideResult[0].longValueExact();
		long newFractional = divideResult[1].longValue();

		return new FixedDecimal(newIntegral, newFractional);
	}

	// 转换为S128i表示 (integral * DECIMAL_PLACES + fractional)
	private S128i toS128i() {
		S128i self = new S128i(integral);
		return self.multiply(DECIMAL_PLACES, self).add(fractional, self);
	}

	public boolean isZero() {return (integral|fractional) == 0;}

	// 比较大小
	public int compareTo(FixedDecimal other) {
		if (integral != other.integral) {
			return Long.compare(integral, other.integral);
		}
		return Long.compare(fractional, other.fractional);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		FixedDecimal that = (FixedDecimal) o;

		if (integral != that.integral) return false;
        return fractional == that.fractional;
    }

	@Override
	public int hashCode() {
		int result = (int) (integral ^ (integral >>> 32));
		result = 31 * result + (int) (fractional ^ (fractional >>> 32));
		return result;
	}

	// 转换为整数
	public long toLong() {return integral;}
	// 转换为浮点数
	public double toDouble() {return integral + (double) fractional / DECIMAL_PLACES;}

	@Override public String toString() {return String.format("%d.%0"+(Integer.toString(DECIMAL_PLACES).length()-1)+"d", integral, fractional);}
}