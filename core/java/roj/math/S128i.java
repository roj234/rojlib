package roj.math;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Range;
import roj.annotation.MayMutate;
import roj.text.CharList;
import roj.util.ArrayCache;

/**
 * 有符号128位整数
 * 除法写的不好，很慢
 * @author Roj234
 * @since 2025/3/17 4:26
 */
public class S128i implements Comparable<S128i> {
	public static final S128i MIN_VALUE = new S128i(Long.MIN_VALUE, 0);
	public static final S128i MAX_VALUE = new S128i(Long.MAX_VALUE, -1);

	private long high, low;

	public static S128i valueOf(long low) {return new S128i(low);}
	public static S128i valueOf(String str) {return valueOf(str, 10);}
	public static S128i valueOf(String str, int radix) {
		int i = 0;
		int len = str.length();

		char c = str.charAt(i);
		if (c == '+' || c == '-') i++;

		var v = new S128i();
		while (i < len) {
			var c1 = str.charAt(i);
			int digit = Character.digit(c1, radix);
			if (digit < 0) throw new NumberFormatException("s["+i+"]='"+c1+"' 不是合法的数字: "+str);
			i++;

			v.multiply(radix, v).sub(digit, v);
			if (v.high > 0) throw new NumberFormatException("S128I out of range: "+str);
		}

		if (c != '-') {
			v.negate(v);
			if (v.high < 0) throw new NumberFormatException("S128I out of range: "+str);
		}
		return v;
	}

	public S128i() {}
	public S128i(long low) {this.high = low < 0 ? -1 : 0;this.low = low;}
	public S128i(long high, long low) {this.high = high;this.low = low;}
	public S128i(S128i s128i) {high = s128i.high;low = s128i.low;}

	public long getHigh() {return high;}
	public long longValue() {return low;}
	public long longValueExact() {
		if ((high == 0 && low >= 0) || (high == -1 && low < 0)) return low;
		throw new ArithmeticException("S128i out of long range");
	}

	public S128i set(long num) {high = num < 0 ? -1 : 0;low = num;return this;}
	public S128i setUnsigned(long num) {high = 0;low = num;return this;}
	public S128i set(long high, long low) {this.high = high;this.low = low;return this;}
	public S128i set(S128i s128i) {high = s128i.high;low = s128i.low;return this;}

	public boolean isZero() {return (high|low) == 0;}
	public boolean isNegative() {return high < 0;}
	public S128i abs() {return isNegative() ? negate() : this;}
	public S128i withSign(boolean negative) {return negative == isNegative() ? this : negate();}
	private S128i setSign(boolean negative) {return negative == isNegative() ? this : negate(this);}

	@Contract(" -> new")
	public final S128i negate() {return negate(new S128i());}
	@Contract("_ -> param1")
	public S128i negate(S128i storage) {
		long newHigh = ~high;
		long newLow = ~low + 1;
		if (newLow == 0) newHigh += 1;
		storage.high = newHigh;
		storage.low = newLow;
		return storage;
	}

	public final S128i absExact() {return isNegative() ? negateExact() : this;}
	@Contract(" -> new")
	public final S128i negateExact() {return negateExact(new S128i());}
	@Contract("_ -> param1")
	public final S128i negateExact(S128i storage) {
		if (equals(MIN_VALUE)) throw new ArithmeticException("numeric overflow");
		return negate(storage);
	}

	public int compareTo(S128i other) {
		if (this.isNegative() != other.isNegative()) {
			// 符号不同，正数更大
			return this.isNegative() ? -1 : 1;
		} else {
			// 符号相同，比较高位的有符号值，再比较低位无符号值
			int cmp = Long.compare(this.high, other.high);
			return cmp != 0 ? cmp : Long.compareUnsigned(this.low, other.low);
		}
	}

	public int compareUnsigned(S128i other) {
		int cmp = Long.compareUnsigned(this.high, other.high);
		return cmp != 0 ? cmp : Long.compareUnsigned(this.low, other.low);
	}

	@Contract("_ -> new")
	public final S128i add(S128i other) {return add(other, new S128i());}
	@Contract("_, _ -> param2")
	public S128i add(S128i other, S128i storage) {
		long aLow = this.low;
		long bLow = other.low;
		long sumLow = aLow + bLow;
		// 计算低位进位
		int carry = (Long.compareUnsigned(sumLow, aLow) < 0) ? 1 : 0;
		storage.high = this.high + other.high + carry;
		storage.low = sumLow;
		return storage;
	}
	@Contract("_ -> new")
	public final S128i add(long bLow) {return add(bLow, new S128i());}
	@Contract("_, _ -> param2")
	public S128i add(long bLow, S128i storage) {
		long aLow = this.low;
		long sumLow = aLow + bLow;
		// 计算低位进位
		int carry = (Long.compareUnsigned(sumLow, aLow) < 0) ? 1 : 0;
		storage.high = this.high + carry;
		storage.low = sumLow;
		return storage;
	}

	@Contract("_ -> new")
	public final S128i sub(S128i other) {return sub(other, new S128i());}
	@Contract("_, _ -> param2")
	public S128i sub(S128i other, S128i storage) {
		long aLow = this.low;
		long bLow = other.low;
		long diffLow = aLow - bLow;
		int borrow = (Long.compareUnsigned(aLow, bLow) < 0) ? 1 : 0;
		storage.high = this.high - other.high - borrow;
		storage.low = diffLow;
		return storage;
	}
	@Contract("_ -> new")
	public final S128i sub(long bLow) {return sub(bLow, new S128i());}
	@Contract("_, _ -> param2")
	public S128i sub(long bLow, S128i storage) {
		long aLow = this.low;
		long diffLow = aLow - bLow;
		int borrow = (Long.compareUnsigned(aLow, bLow) < 0) ? 1 : 0;
		storage.high = this.high - borrow;
		storage.low = diffLow;
		return storage;
	}

	public static S128i addExact(S128i x, S128i y) {return addExact(x, y, new S128i());}
	public static S128i addExact(S128i x, S128i y, S128i storage) {
		x.add(y, storage);
		checkOverflow(x, y, storage);
		return storage;
	}

	public static S128i subExact(S128i x, S128i y) {return subExact(x, y, new S128i());}
	public static S128i subExact(S128i x, S128i y, S128i storage) {
		x.sub(y, storage);
		checkOverflow(x, y, storage);
		return storage;
	}

	private static void checkOverflow(S128i x, S128i y, S128i r) {
		if (((x.high ^ r.high) & (y.high ^ r.high)) < 0) {
			throw new ArithmeticException("integer overflow");
		}
	}

	@Contract("_ -> new")
	public final S128i multiply(S128i other) {return multiply(other, new S128i());}
	@Contract("_, _ -> param2")
	public S128i multiply(S128i other, S128i storage) {
		long aHigh = this.high;
		long aLow = this.low;
		long bHigh = other.high;
		long bLow = other.low;

		// 计算aLow * bLow 的低位和高位
		long llLow = aLow * bLow;
		long llHigh = MathUtils.unsignedMultiplyHigh(aLow, bLow);

		// 计算aHigh * bLow 和 aLow * bHigh
		long h1 = aHigh * bLow;
		long h2 = aLow * bHigh;

		// 累加高位部分
		storage.high = llHigh + h1 + h2;
		storage.low = llLow;
		return storage;
	}
	@Contract("_ -> new")
	public final S128i multiply(long bLow) {return multiply(bLow, new S128i());}
	@Contract("_, _ -> param2")
	public S128i multiply(long bLow, S128i storage) {
		long aHigh = this.high;
		long aLow = this.low;

		long llLow = aLow * bLow;
		long llHigh = MathUtils.unsignedMultiplyHigh(aLow, bLow);

		long h1 = aHigh * bLow;

		// 累加高位部分
		storage.high = llHigh + h1;
		storage.low = llLow;
		return storage;
	}

	@Contract("_ -> new")
	public S128i divide(S128i other) {
		if (other.isZero()) throw new ArithmeticException("Division by zero");

		boolean quotientSign = (this.isNegative() != other.isNegative());
		S128i absDividend = new S128i(this);
		if (absDividend.isNegative()) absDividend.negate(absDividend);
		S128i absDivisor = other.abs();

		if (absDividend.compareUnsigned(absDivisor) < 0) return absDividend.setUnsigned(0);

		divideAndRemainderUnsigned(absDividend, absDivisor);
		return absDividend.setSign(quotientSign);
	}
	@Contract("_ -> new")
	public S128i remainder(S128i other) {
		if (other.isZero()) throw new ArithmeticException("Division by zero");

		S128i absDividend = new S128i(this);
		if (absDividend.isNegative()) absDividend.negate(absDividend);
		S128i absDivisor = other.abs();

		if (absDividend.compareUnsigned(absDivisor) < 0) return absDividend;

		return divideAndRemainderUnsigned(absDividend, absDivisor).setSign(this.isNegative());
	}
	@Contract("_ -> new")
	public S128i[] divideAndRemainder(S128i other) {
		if (other.isZero()) throw new ArithmeticException("Division by zero");

		boolean quotientSign = (this.isNegative() != other.isNegative());
		S128i absDividend = new S128i(this);
		if (absDividend.isNegative()) absDividend.negate(absDividend);
		S128i absDivisor = other.abs();

		var remainder = divideAndRemainderUnsigned(absDividend, absDivisor);
		absDividend.setSign(quotientSign);
		remainder.setSign(this.isNegative());
		return new S128i[] {absDividend, remainder};
	}

	@Contract("_ -> new")
	public final S128i mod(S128i modulus) {
		if (modulus.isNegative() || modulus.isZero()) throw new ArithmeticException("Modulus must be positive");
		S128i r = remainder(modulus);
		return r.isNegative() ? r.add(modulus, r) : r;
	}

	// 最大公约数 (GCD)
	public S128i gcd(S128i other) {
		S128i a = this.abs();
		S128i b = other.abs();

		while (!b.isZero()) {
			S128i temp = b;
			b = a.remainder(b);
			a = temp;
		}
		return a;
	}

	@Contract("_ -> new") public S128i divideUnsigned(S128i other) {
		S128i quotient = new S128i(this);
		divideAndRemainderUnsigned(quotient, other);
		return quotient;
	}
	@Contract("_ -> new") public S128i remainderUnsigned(S128i other) {return divideAndRemainderUnsigned(new S128i(this), other);}
	@Contract("_ -> new") public S128i[] divideAndRemainderUnsigned(S128i divisor) {
		S128i quotient = new S128i(this);
		S128i remainder = divideAndRemainderUnsigned(quotient, divisor);
		return new S128i[] {quotient, remainder};
	}

	private static S128i divideAndRemainderUnsigned(S128i dividend, S128i divisor) {return divideAndRemainderUnsigned(dividend, divisor, new S128i());}
	@Contract("_,_,_ -> param3")
	private static S128i divideAndRemainderUnsigned(@MayMutate("quotient") S128i dividend, S128i divisor, @MayMutate S128i remainder) {
		if (divisor.isZero()) throw new ArithmeticException("Division by zero");

		// Fast path: dividend is small
		if (dividend.high == 0) {
			if (dividend.low != 0 && divisor.high == 0) { // Both 64-bit
				long q = Long.divideUnsigned(dividend.low, divisor.low);
				long r = Long.remainderUnsigned(dividend.low, divisor.low);
				remainder.setUnsigned(r);
				dividend.setUnsigned(q);
			} else { // dividend == 0 || dividend < divisor
				remainder.set(dividend); // might be zero
				dividend.setUnsigned(0);
			}

			return remainder;
		}

		if (divisor.high == 0) {
			if (divisor.low == 1) {
				return remainder.setUnsigned(0);
			}
		}

		int divisorLeadingZeros = divisor.numberOfLeadingZeros();
		int dividendLeadingZeros = dividend.numberOfLeadingZeros();

		// 被除数的最高有效位位置
		int shift = divisorLeadingZeros - dividendLeadingZeros;

		// 被除数 < 除数
		if (shift < 0 || dividend.compareUnsigned(divisor) < 0) {
			// must be here, because dividend might == remainder[0]
			remainder.set(dividend);
			dividend.setUnsigned(0);
			return remainder;
		}
		if (shift == 0) shift = 1;

		remainder.set(dividend);
		S128i quotient = dividend.setUnsigned(0);

		S128i current = new S128i(divisor);

		current.shiftLeft(shift);
		int multiple = shift;

		// 检查current是否超过remainder
		if (current.compareUnsigned(remainder) > 0) {
			current.shiftRight(1);
			multiple--;
		}

		while (multiple >= 0) {
			if (remainder.compareUnsigned(current) >= 0) {
				remainder.sub(current, remainder);
				quotient.setBit(multiple);
			}

			current.moveRight();
			multiple--;
		}

		return remainder;
	}
	private void moveRight() {
		low = (low >>> 1) | (high << (64 - 1));
		high = high >>> 1;
	}
	private void setBit(int n) {
		if (n < 64) {
			low |= 1L << n;
		} else {
			high |= 1L << (n - 64);
		}
	}

	public int numberOfLeadingZeros() {return high != 0 ? Long.numberOfLeadingZeros(high) : 64+Long.numberOfLeadingZeros(low);}
	public int numberOfTrailingZeros() {return low != 0 ? Long.numberOfTrailingZeros(low) : 64+Long.numberOfTrailingZeros(high);}
	public void shiftLeft(int n) {
		if (n == 0) return;
		if (n >= 128) {
			high = low = 0;
		} else if (n >= 64) {
			high = low << (n - 64);
			low = 0;
		} else {
			high = (high << n) | (low >>> (64 - n));
			low = low << n;
		}
	}
	public void shiftRight(int n) {
		if (n == 0) return;
		if (n >= 128) {
			low = high = 0;
		} else if (n >= 64) {
			low = high >>> (n - 64);
			high = 0;
		} else {
			low = (low >>> n) | (high << (64 - n));
			high = high >>> n;
		}
	}

	public S128i pow(int exponent) {
		if (exponent < 0) throw new IllegalArgumentException("Exponent must be non-negative");
		var result = new S128i(1);
		if (exponent == 0) {
			if (this.isZero()) throw new ArithmeticException("Zero to the power of zero is undefined");
			return result;
		}

		S128i base = new S128i(this);

		int n = exponent;
		while (n > 0) {
			if ((n & 1) != 0) {
				result.multiply(base, result);
			}
			base.multiply(base, base);
			n >>>= 1;
		}

		return result;
	}

	@Override
	public String toString() {return toString(10);}
	public String toString(@Range(from = 2, to = 36) int radix) {
		if (isZero()) return "0";

		var sb = ArrayCache.getCharArray(256, false);
		var absNum = new S128i(this);
		var negative = absNum.isNegative();
		if (negative) absNum.negate(absNum);

		var pos = fillChars(radix, absNum, sb);
		if (negative) sb[--pos] = '-';

		String str = new String(sb, pos, 256 - pos);
		ArrayCache.putArray(sb);
		return str;
	}
	public String toUnsignedString() {return toUnsignedString(10);}
	public String toUnsignedString(@Range(from = 2, to = 36) int radix) {
		if (isZero()) return "0";

		var sb = ArrayCache.getCharArray(256, false);
		var num = new S128i(this);
		var pos = fillChars(radix, num, sb);

		String str = new String(sb, pos, 256 - pos);
		ArrayCache.putArray(sb);
		return str;
	}
	private static int fillChars(int radix, S128i num, char[] sb) {
		var base = digitBase[radix -2];
		var remainder = new S128i();

		int pos = 255;
		while (true) {
			divideAndRemainderUnsigned(num, base, remainder);
			int newPos = CharList.getChars(remainder.low, radix, pos, sb);
			if (num.isZero()) {pos = newPos;break;}

			pos -= digitSize[radix -2];
			for (int i = pos; i < newPos; i++) sb[i] = '0';
		}
		return pos;
	}
	private static final byte[] digitSize = {
			62, 39, 31, 27, 24, 22, 20, 19, 18, 18, 17, 17, 16, 16, 15, 15, 15, 14,
			14, 14, 14, 13, 13, 13, 13, 13, 13, 12, 12, 12, 12, 12, 12, 12, 12
	};
	private static final S128i[] digitBase = {
			valueOf(0x4000000000000000L), valueOf(0x383d9170b85ff80bL),
			valueOf(0x4000000000000000L), valueOf(0x6765c793fa10079dL),
			valueOf(0x41c21cb8e1000000L), valueOf(0x3642798750226111L),
			valueOf(0x1000000000000000L), valueOf(0x12bf307ae81ffd59L),
			valueOf( 0xde0b6b3a7640000L), valueOf(0x4d28cb56c33fa539L),
			valueOf(0x1eca170c00000000L), valueOf(0x780c7372621bd74dL),
			valueOf(0x1e39a5057d810000L), valueOf(0x5b27ac993df97701L),
			valueOf(0x1000000000000000L), valueOf(0x27b95e997e21d9f1L),
			valueOf(0x5da0e1e53c5c8000L), valueOf( 0xb16a458ef403f19L),
			valueOf(0x16bcc41e90000000L), valueOf(0x2d04b7fdd9c0ef49L),
			valueOf(0x5658597bcaa24000L), valueOf( 0x6feb266931a75b7L),
			valueOf( 0xc29e98000000000L), valueOf(0x14adf4b7320334b9L),
			valueOf(0x226ed36478bfa000L), valueOf(0x383d9170b85ff80bL),
			valueOf(0x5a3c23e39c000000L), valueOf( 0x4e900abb53e6b71L),
			valueOf( 0x7600ec618141000L), valueOf( 0xaee5720ee830681L),
			valueOf(0x1000000000000000L), valueOf(0x172588ad4f5f0981L),
			valueOf(0x211e44f7d02c1000L), valueOf(0x2ee56725f06e5c71L),
			valueOf(0x41c21cb8e1000000L)
	};

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		S128i s128i = (S128i) o;

		if (high != s128i.high) return false;
		return low == s128i.low;
	}

	@Override
	public int hashCode() {
		int result = (int) (high ^ (high >>> 32));
		result = 31 * result + (int) (low ^ (low >>> 32));
		return result;
	}
}
