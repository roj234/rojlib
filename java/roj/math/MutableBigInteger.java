package roj.math;


import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;
import roj.asm.Opcodes;
import roj.asm.type.TypeHelper;
import roj.reflect.Bypass;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Mutable Big Integer Accessor
 *
 * @author Roj233
 * @since 2021/7/8 0:35
 */
public final class MutableBigInteger implements Comparable<MutableBigInteger> {
	public MutableBigInteger() {ptr = o._n1();}
	public MutableBigInteger(int val) {ptr = o._n2(val);}
	public MutableBigInteger(int... val) {ptr = o._n3(val);}
	public MutableBigInteger(BigInteger b) {ptr = o._n4(b);}
	public MutableBigInteger(MutableBigInteger val) {ptr = o._n5(val.ptr);}
	private MutableBigInteger(Object o) {ptr = o;}
	@Nullable
	private static MutableBigInteger fromPtr(Object o) {return o == null ? null : new MutableBigInteger(o);}

	private interface Opr {
		void add(Object ptr, Object I);
		Object toString(Object ptr);
		int getInt(Object ptr, int I);
		long getLong(Object ptr, int I);
		void clear(Object ptr);
		int compare(Object ptr, Object I);
		void setValue(Object ptr, int[] I, int II);
		void setInt(Object ptr, int I, int II);
		void normalize(Object ptr);
		void ensureCapacity(Object ptr, int I);
		void reset(Object ptr);
		Object divide(Object ptr, Object I, Object II);
		Object divide(Object ptr, Object I, Object II, boolean III);
		long divide(Object ptr, long I, Object II);
		Object divideAndRemainderBurnikelZiegler(Object ptr, Object I, Object II);
		int mulsub(Object ptr, int[] I, int[] II, int III, int IV, int V);
		boolean unsignedLongCompare(Object ptr, long I, long II);
		Object getLower(Object ptr, int I);
		int getLowestSetBit(Object ptr);
		long inverseMod64(long I);
		Object modInverse(Object ptr, Object I);
		Object mutableModInverse(Object ptr, Object I);
		void primitiveLeftShift(Object ptr, int I);
		void primitiveRightShift(Object ptr, int I);
		Object binaryGCD(Object ptr, Object I);
		int binaryGcd(int I, int II);
		int compareShifted(Object ptr, Object I, int II);
		void copyAndShift(int[] I, int II, int III, int[] IV, int V, int VI);
		int difference(Object ptr, Object I);
		int divadd(Object ptr, int[] I, int[] II, int III);
		int divaddLong(Object ptr, int I, int II, int[] III, int IV);
		Object divide2n1n(Object ptr, Object I, Object II);
		Object divide3n2n(Object ptr, Object I, Object II);
		Object divideLongMagnitude(Object ptr, long I, Object II);
		Object divideMagnitude(Object ptr, Object I, Object II, boolean III);
		Object euclidModInverse(Object ptr, int I);
		Object fixup(Object I, Object II, int III);
		Object getBlock(Object ptr, int I, int II, int III);
		int[] getMagnitudeArray(Object ptr);
		int inverseMod32(int I);
		void keepLower(Object ptr, int I);
		Object modInverseBP2(Object I, int II);
		Object modInverseMP2(Object ptr, int I);
		int mulsubBorrow(Object ptr, int[] I, int[] II, int III, int IV, int V);
		int mulsubLong(Object ptr, int[] I, int II, int III, int IV, int V);
		void ones(Object ptr, int I);
		long toLong(Object ptr);
		Object toBigDecimal(Object ptr, int I, int II);
		void safeRightShift(Object ptr, int I);
		void addLower(Object ptr, Object I, int II);
		int compareHalf(Object ptr, Object I);
		void mul(Object ptr, int I, Object II);
		void copyValue(Object ptr, Object I);
		void copyValue(Object ptr, int[] I);
		long bitLength(Object ptr);
		Object toBigInteger(Object ptr, int I);
		Object toBigInteger(Object ptr);
		int divideOneWord(Object ptr, int I, Object II);
		long divWord(long I, int II);
		boolean isOdd(Object ptr);
		boolean isEven(Object ptr);
		void rightShift(Object ptr, int I);
		void leftShift(Object ptr, int I);
		Object divideKnuth(Object ptr, Object I, Object II);
		Object divideKnuth(Object ptr, Object I, Object II, boolean III);
		int subtract(Object ptr, Object I);
		boolean isZero(Object ptr);
		void multiply(Object ptr, Object I, Object II);
		void addDisjoint(Object ptr, Object I, int II);
		boolean isNormal(Object ptr);
		void addShifted(Object ptr, Object I, int II);
		boolean isOne(Object ptr);
		int[] toIntArray(Object ptr);
		void safeLeftShift(Object ptr, int I);
		long toCompactValue(Object ptr, int I);
		Object hybridGCD(Object ptr, Object I);
		int[] _nArrG(Object ptr);
		void _nArrS(Object ptr, int[] arr);
		int _nArrLen(Object ptr);
		Object _n1();
		Object _n2(int val);
		Object _n3(int[] val);
		Object _n4(BigInteger val);
		Object _n5(Object val);
	}

	private final Object ptr;

	private static final Opr o;

	static {
		Class<?> mb;
		try {
			mb = Class.forName("java.math.MutableBigInteger");
		} catch (ClassNotFoundException e) {
			throw new Error();
		}

		Bypass<Opr> dab = Bypass
			.custom(Opr.class).inline().unchecked()
			.construct(mb, "_n1", "_n2", "_n3", "_n4")
			.construct(mb, "_n5", mb)
			.access(mb, new String[] {"value", "intLen"}, new String[] {"_nArrG", "_nArrLen"}, new String[] {"_nArrS", null});

		Comparator<Method> MC = (o1, o2) -> {
			int i = o1.getName().compareTo(o2.getName());
			return i == 0 ? TypeHelper.class2asm(o1.getParameterTypes(), o1.getReturnType()).compareTo(TypeHelper.class2asm(o2.getParameterTypes(), o2.getReturnType())) : i;
		};

		// Do not except it to have ANY order
		Method[] myMethods = Opr.class.getDeclaredMethods();
		Arrays.sort(myMethods, MC);
		Method[] itMethods = mb.getDeclaredMethods();
		Arrays.sort(itMethods, MC);
		String target = "java/math/MutableBigInteger";

		int i = 0, j = 0;
		while (i < myMethods.length) {
			Method m = myMethods[i++];
			if (m.getName().startsWith("_")) continue;
			Method im;
			do {
				im = itMethods[j++];
			} while (!m.getName().equals(im.getName()));
			dab.i_delegate(target, m.getName(), TypeHelper.class2asm(im.getParameterTypes(), im.getReturnType()), m,
						   (im.getModifiers() & Opcodes.ACC_STATIC) != 0 ? Bypass.INVOKE_STATIC : Bypass.INVOKE_SPECIAL);
		}

		o = dab.build();
	}

	public int[] getArray0() {return o._nArrG(ptr);}
	public void setArray0(int[] arr) {o._nArrS(ptr, arr);}
	public int getIntLen() {return o._nArrLen(ptr);}
	/**
	 * Internal helper method to return the magnitude array. The caller is not
	 * supposed to modify the returned array.
	 */
	public int[] getMagnitudeArray() {return o.getMagnitudeArray(ptr);}
	/**
	 * Convert this MutableBigInteger to a BigInteger object.
	 */
	public BigInteger toBigInteger(int sign) {return (BigInteger) o.toBigInteger(ptr, sign);}
	/**
	 * Converts this number to a nonnegative {@code BigInteger}.
	 */
	public BigInteger toBigInteger() {return (BigInteger) o.toBigInteger(ptr);}
	/**
	 * Convert this MutableBigInteger to BigDecimal object with the specified sign
	 * and scale.
	 */
	public BigDecimal toBigDecimal(int sign, int scale) {return (BigDecimal) o.toBigDecimal(ptr, sign, scale);}
	/**
	 * This is for internal use in converting from a MutableBigInteger
	 * object into a long value given a specified sign.
	 * returns INFLATED if value is not fit into long
	 */
	public long toCompactValue(int sign) {return o.toCompactValue(ptr, sign);}
	/**
	 * Clear out a MutableBigInteger for reuse.
	 */
	public void clear() {o.clear(ptr);}
	/**
	 * Set a MutableBigInteger to zero, removing its offset.
	 */
	public void reset() {o.reset(ptr);}
	/**
	 * Compare the magnitude of two MutableBigIntegers. Returns -1, 0 or 1
	 * as this MutableBigInteger is numerically less than, equal to, or
	 * greater than <tt>b</tt>.
	 */
	public int compare(MutableBigInteger b) {return o.compare(ptr, b.ptr);}
	/**
	 * Compare this against half of a MutableBigInteger object (Needed for
	 * remainder tests).
	 * Assumes no leading unnecessary zeros, which holds for results
	 * from divide().
	 */
	public int compareHalf(MutableBigInteger b) {return o.compareHalf(ptr, b.ptr);}
	/**
	 * Ensure that the MutableBigInteger is in normal form, specifically
	 * making sure that there are no leading zeros, and that if the
	 * magnitude is zero, then intLen is zero.
	 */
	public void normalize() {o.normalize(ptr);}
	/**
	 * Convert this MutableBigInteger into an int array with no leading
	 * zeros, of a length that is equal to this MutableBigInteger's intLen.
	 */
	public int[] toIntArray() {return o.toIntArray(ptr);}
	/**
	 * Sets the int at index+offset in this MutableBigInteger to val.
	 * This does not get inlined on all platforms so it is not used
	 * as often as originally intended.
	 */
	public void setInt(int index, int val) {o.setInt(ptr, index, val);}
	/**
	 * Sets this MutableBigInteger's value array to the specified array.
	 * The intLen is set to the specified length.
	 */
	public void setValue(int[] val, int length) {o.setValue(ptr, val, length);}
	/**
	 * Sets this MutableBigInteger's value array to a copy of the specified
	 * array. The intLen is set to the length of the new array.
	 */
	public void copyValue(MutableBigInteger src) {o.copyValue(ptr, src.ptr);}
	/**
	 * Sets this MutableBigInteger's value array to a copy of the specified
	 * array. The intLen is set to the length of the specified array.
	 */
	public void copyValue(int[] val) {o.copyValue(ptr, val);}
	/**
	 * Returns true iff this MutableBigInteger has a value of one.
	 */
	public boolean isOne() {return o.isOne(ptr);}
	/**
	 * Returns true iff this MutableBigInteger has a value of zero.
	 */
	public boolean isZero() {return o.isZero(ptr);}
	/**
	 * Returns true iff this MutableBigInteger is even.
	 */
	public boolean isEven() {return o.isEven(ptr);}
	/**
	 * Returns true iff this MutableBigInteger is odd.
	 */
	public boolean isOdd() {return o.isOdd(ptr);}
	/**
	 * Returns true iff this MutableBigInteger is in normal form. A
	 * MutableBigInteger is in normal form if it has no leading zeros
	 * after the offset, and intLen + offset <= value.length.
	 */
	public boolean isNormal() {return o.isNormal(ptr);}
	/**
	 * Returns a String representation of this MutableBigInteger in radix 10.
	 */
	public String toString() {return ptr.toString();}
	/**
	 * Like {@link #rightShift(int)} but {@code n} can be greater than the length of the number.
	 */
	public void safeRightShift(int n) {o.safeRightShift(ptr, n);}
	/**
	 * Right shift this MutableBigInteger n bits. The MutableBigInteger is left
	 * in normal form.
	 */
	public void rightShift(int n) {o.rightShift(ptr, n);}
	/**
	 * Like {@link #leftShift(int)} but {@code n} can be zero.
	 */
	public void safeLeftShift(int n) {o.safeLeftShift(ptr, n);}
	/**
	 * Left shift this MutableBigInteger n bits.
	 */
	public void leftShift(int n) {o.leftShift(ptr, n);}
	/**
	 * Adds the contents of two MutableBigInteger objects.The result
	 * is placed within this MutableBigInteger.
	 * The contents of the addend are not changed.
	 */
	public void add(MutableBigInteger addend) {o.add(ptr, addend.ptr);}
	/**
	 * Adds the value of {@code addend} shifted {@code n} ints to the left.
	 * Has the same effect as {@code addend.leftShift(32*ints); add(addend);}
	 * but doesn't change the value of {@code addend}.
	 */
	public void addShifted(MutableBigInteger addend, int n) {o.addShifted(ptr, addend.ptr, n);}
	/**
	 * Like {@link #addShifted(MutableBigInteger, int)} but {@code this.intLen} must
	 * not be greater than {@code n}. In other words, concatenates {@code this}
	 * and {@code addend}.
	 */
	public void addDisjoint(MutableBigInteger addend, int n) {o.addDisjoint(ptr, addend.ptr, n);}
	/**
	 * Adds the low {@code n} ints of {@code addend}.
	 */
	public void addLower(MutableBigInteger addend, int n) {o.addLower(ptr, addend.ptr, n);}
	/**
	 * Subtracts the smaller of this and b from the larger and places the
	 * result into this MutableBigInteger.
	 */
	public int subtract(MutableBigInteger b) {return o.subtract(ptr, b.ptr);}
	/**
	 * Multiply the contents of two MutableBigInteger objects. The result is
	 * placed into MutableBigInteger z. The contents of y are not changed.
	 */
	public void multiply(MutableBigInteger y, MutableBigInteger z) {o.multiply(ptr, y.ptr, z.ptr);}
	/**
	 * Multiply the contents of this MutableBigInteger by the word y. The
	 * result is placed into z.
	 */
	public void mul(int y, MutableBigInteger z) {o.mul(ptr, y, z.ptr);}
	/**
	 * This method is used for division of an n word dividend by a one word
	 * divisor. The quotient is placed into quotient. The one word divisor is
	 * specified by divisor.
	 *
	 * @return the remainder of the division is returned.
	 */
	public int divideOneWord(int divisor, MutableBigInteger quotient) {return o.divideOneWord(ptr, divisor, quotient.ptr);}
	/**
	 * Calculates the quotient of this div b and places the quotient in the
	 * provided MutableBigInteger objects and the remainder object is returned.
	 */
	public MutableBigInteger divide(MutableBigInteger b, MutableBigInteger quotient) {return fromPtr(o.divide(ptr, b.ptr, quotient.ptr));}
	@Contract("_,_,true -> !null ; _,_,false -> null")
	public MutableBigInteger divide(MutableBigInteger b, MutableBigInteger quotient, boolean needRemainder) {return fromPtr(o.divide(ptr, b.ptr, quotient.ptr, needRemainder));}
	/**
	 * @see #divideKnuth(MutableBigInteger, MutableBigInteger, boolean)
	 */
	public MutableBigInteger divideKnuth(MutableBigInteger b, MutableBigInteger quotient) {return fromPtr(o.divideKnuth(ptr, b.ptr, quotient.ptr));}
	/**
	 * Calculates the quotient of this div b and places the quotient in the
	 * provided MutableBigInteger objects and the remainder object is returned.
	 * <p>
	 * Uses Algorithm D in Knuth section 4.3.1.
	 * Many optimizations to that algorithm have been adapted from the Colin
	 * Plumb C library.
	 * It special cases one word divisors for speed. The content of b is not
	 * changed.
	 */
	@Contract("_,_,true -> !null ; _,_,false -> null")
	public MutableBigInteger divideKnuth(MutableBigInteger b, MutableBigInteger quotient, boolean needRemainder) {return fromPtr(o.divideKnuth(ptr, b.ptr, quotient.ptr, needRemainder));}
	/**
	 * Computes {@code this/b} and {@code this%b} using the
	 * <a href="http://cr.yp.to/bib/1998/burnikel.ps"> Burnikel-Ziegler algorithm</a>.
	 * This method implements algorithm 3 from pg. 9 of the Burnikel-Ziegler paper.
	 * The parameter beta was chosen to b 2<sup>32</sup> so almost all shifts are
	 * multiples of 32 bits.<br/>
	 * {@code this} and {@code b} must be nonnegative.
	 *
	 * @param b the divisor
	 * @param quotient output parameter for {@code this/b}
	 *
	 * @return the remainder
	 */
	public MutableBigInteger divideAndRemainderBurnikelZiegler(MutableBigInteger b, MutableBigInteger quotient) {return fromPtr(o.divideAndRemainderBurnikelZiegler(ptr, b.ptr, quotient.ptr));}
	/** @see BigInteger#bitLength() */
	public long bitLength() {return o.bitLength(ptr);}
	/**
	 * Internally used  to calculate the quotient of this div v and places the
	 * quotient in the provided MutableBigInteger object and the remainder is
	 * returned.
	 *
	 * @return the remainder of the division will be returned.
	 */
	public long divide(long v, MutableBigInteger quotient) {return o.divide(ptr, v, quotient.ptr);}
	/**
	 * This method divides a long quantity by an int to estimate
	 * qhat for two multi precision numbers. It is used when
	 * the signed value of n is less than zero.
	 * Returns long value where high 32 bits contain remainder value and
	 * low 32 bits contain quotient value.
	 */
	public static long divWord(long n, int d) {return o.divWord(n, d);}
	/**
	 * Calculate GCD of this and b. This and b are changed by the computation.
	 */
	public MutableBigInteger hybridGCD(MutableBigInteger b) {return fromPtr(o.hybridGCD(ptr, b.ptr));}
	/**
	 * Calculate GCD of a and b interpreted as unsigned integers.
	 */
	public static int binaryGcd(int a, int b) {return o.binaryGcd(a, b);}
	/**
	 * Returns the modInverse of this mod p. This and p are not affected by
	 * the operation.
	 */
	public MutableBigInteger mutableModInverse(MutableBigInteger p) {return fromPtr(o.mutableModInverse(ptr, p.ptr));}
	/**
	 * Calculate the multiplicative inverse of this mod 2^k.
	 */
	public MutableBigInteger modInverseMP2(int k) {return fromPtr(o.modInverseMP2(ptr, k));}
	/**
	 * Returns the multiplicative inverse of val mod 2^32.  Assumes val is odd.
	 */
	public static int inverseMod32(int val) {return o.inverseMod32(val);}
	/**
	 * Returns the multiplicative inverse of val mod 2^64.  Assumes val is odd.
	 */
	public static long inverseMod64(long val) {return o.inverseMod64(val);}
	/**
	 * Calculate the multiplicative inverse of 2^k mod mod, where mod is odd.
	 */
	public static MutableBigInteger modInverseBP2(MutableBigInteger mod, int k) {return fromPtr(o.modInverseBP2(mod.ptr, k));}
	/**
	 * The Fixup Algorithm
	 * Calculates X such that X = C * 2^(-k) (mod P)
	 * Assumes C&lt;P and P is odd.
	 */
	public static MutableBigInteger fixup(MutableBigInteger c, MutableBigInteger p, int k) {o.fixup(c.ptr, p.ptr, k);return c;}

	/**
	 * Uses the extended Euclidean algorithm to compute the modInverse of base
	 * mod a modulus that is a power of 2. The modulus is 2^k.
	 */
	public MutableBigInteger euclidModInverse(int k) {return fromPtr(o.euclidModInverse(ptr, k));}

	@Override
	public boolean equals(Object o1) {
		if (this == o1) return true;
		if (o1 == null || getClass() != o1.getClass()) return false;

		return o.compare(ptr, ((MutableBigInteger) o1).ptr) == 0;
	}
	@Override
	public int hashCode() {return Arrays.hashCode(o.getMagnitudeArray(ptr));}
	@Override
	public int compareTo(MutableBigInteger o1) {return o.compare(ptr, o1.ptr);}
}