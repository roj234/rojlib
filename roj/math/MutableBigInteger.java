package roj.math;


import roj.reflect.DirectAccessor;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;

/**
 * Mutable Big Integer Accessor
 *
 * @author Roj233
 * @since 2021/7/8 0:35
 */
public final class MutableBigInteger {
    public MutableBigInteger() {
        ptr = o.init1();
    }

    public MutableBigInteger(int val) {
        ptr = o.init2(val);
    }

    public MutableBigInteger(int[] val) {
        ptr = o.init3(val);
    }

    public MutableBigInteger(BigInteger b) {
        ptr = o.init4(b);
    }

    public MutableBigInteger(MutableBigInteger val) {
        ptr = o.init5(val.ptr);
    }

    private MutableBigInteger(Object o) {
        ptr = o;
    }

    private static MutableBigInteger fromPtr(Object o) {
        return o == null ? null : new MutableBigInteger(o);
    }

    private interface Opr {
        Object init1();
        Object init2(int val);
        Object init3(int[] val);
        Object init4(BigInteger val);
        Object init5(Object val);

        BigInteger toBigInteger(Object ptr, int sign);
        BigInteger toBigInteger(Object ptr);
        BigDecimal toBigDecimal(Object ptr, int sign, int scale);
        long toCompactValue(Object ptr, int sign);
        void clear(Object ptr);
        void reset(Object ptr);
        int compare(Object ptr, Object b);
        int compareHalf(Object ptr, Object b);
        void normalize(Object ptr);
        int[] toIntArray(Object ptr);
        void setInt(Object ptr, int index, int val);
        void setValue(Object ptr, int[] val, int length);
        void copyValue(Object ptr, Object src);
        void copyValue(Object ptr, int[] val);
        boolean isOne(Object ptr);
        boolean isZero(Object ptr);
        boolean isEven(Object ptr);
        boolean isOdd(Object ptr);
        boolean isNormal(Object ptr);
        void safeRightShift(Object ptr, int n);
        void rightShift(Object ptr, int n);
        void safeLeftShift(Object ptr, int n);
        void leftShift(Object ptr, int n);
        void add(Object ptr, Object addend);
        void addShifted(Object ptr, Object addend, int n);
        void addDisjoint(Object ptr, Object addend, int n);
        void addLower(Object ptr, Object addend, int n);
        int subtract(Object ptr, Object b);
        void multiply(Object ptr, Object y, Object z);
        void mul(Object ptr, int y, Object z);
        int divideOneWord(Object ptr, int divisor, Object quotient);
        Object divide(Object ptr, Object b, Object quotient);
        Object divide(Object ptr, Object b, Object quotient, boolean needRemainder);
        Object divideKnuth(Object ptr, Object b, Object quotient);
        Object divideKnuth(Object ptr, Object b, Object quotient, boolean needRemainder);
        Object divideAndRemainderBurnikelZiegler(Object ptr, Object b, Object quotient);
        long bitLength(Object ptr);
        long divide(Object ptr, long v, Object quotient);
        long divWord(long n, int d);
        Object hybridGCD(Object ptr, Object b);
        int binaryGcd(int a, int b);
        Object mutableModInverse(Object ptr, Object p);
        Object modInverseMP2(Object ptr, int k);
        int inverseMod32(int val);
        long inverseMod64(long val);
        Object modInverseBP2(Object mod, int k);
        Object fixup(Object c, Object p, int k);
        Object euclidModInverse(Object ptr, int k);
    }

    private final Object ptr;

    static final Opr o;
    static {
        Class<?> mb = null;
        try {
            mb = Class.forName("java.math.MutableBigInteger");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        ArrayList<String> list = new ArrayList<>(20);
        for (Method method : Opr.class.getDeclaredMethods()) {
            if(!method.getName().startsWith("init")) {
                list.add(method.getName());
            }
        }
        o = DirectAccessor.builder(Opr.class).unchecked()
                          .constructFuzzy(mb, "init1", "init2", "init3")
                          .construct(mb, "init4", BigInteger.class)
                          .construct(mb, "init5", mb)
                          .delegate_o(mb, list.toArray(new String[list.size()]))
                          .build();
    }

    /**
     * Convert this MutableBigInteger to a BigInteger object.
     */
    public BigInteger toBigInteger(int sign) {
        return o.toBigInteger(ptr, sign);
    }

    /**
     * Converts this number to a nonnegative {@code BigInteger}.
     */
    public BigInteger toBigInteger() {
        return o.toBigInteger(ptr);
    }

    /**
     * Convert this MutableBigInteger to BigDecimal object with the specified sign
     * and scale.
     */
    public BigDecimal toBigDecimal(int sign, int scale) {
        return o.toBigDecimal(ptr, sign, scale);
    }

    /**
     * This is for internal use in converting from a MutableBigInteger
     * object into a long value given a specified sign.
     * returns INFLATED if value is not fit into long
     */
    public long toCompactValue(int sign) {
        return o.toCompactValue(ptr, sign);
    }

    /**
     * Clear out a MutableBigInteger for reuse.
     */
    public void clear() {
        o.clear(ptr);
    }

    /**
     * Set a MutableBigInteger to zero, removing its offset.
     */
    public void reset() {
        o.reset(ptr);
    }

    /**
     * Compare the magnitude of two MutableBigIntegers. Returns -1, 0 or 1
     * as this MutableBigInteger is numerically less than, equal to, or
     * greater than <tt>b</tt>.
     */
    public int compare(MutableBigInteger b) {
        return o.compare(ptr, b.ptr);
    }

    /**
     * Compare this against half of a MutableBigInteger object (Needed for
     * remainder tests).
     * Assumes no leading unnecessary zeros, which holds for results
     * from divide().
     */
    public int compareHalf(MutableBigInteger b) {
        return o.compareHalf(ptr, b.ptr);
    }

    /**
     * Ensure that the MutableBigInteger is in normal form, specifically
     * making sure that there are no leading zeros, and that if the
     * magnitude is zero, then intLen is zero.
     */
    public void normalize() {
        o.normalize(ptr);
    }


    /**
     * Convert this MutableBigInteger into an int array with no leading
     * zeros, of a length that is equal to this MutableBigInteger's intLen.
     */
    public int[] toIntArray() {
        return o.toIntArray(ptr);
    }

    /**
     * Sets the int at index+offset in this MutableBigInteger to val.
     * This does not get inlined on all platforms so it is not used
     * as often as originally intended.
     */
    public void setInt(int index, int val) {
        o.setInt(ptr, index, val);
    }

    /**
     * Sets this MutableBigInteger's value array to the specified array.
     * The intLen is set to the specified length.
     */
    public void setValue(int[] val, int length) {
        o.setValue(ptr, val, length);
    }

    /**
     * Sets this MutableBigInteger's value array to a copy of the specified
     * array. The intLen is set to the length of the new array.
     */
    public void copyValue(MutableBigInteger src) {
        o.copyValue(ptr, src.ptr);
    }

    /**
     * Sets this MutableBigInteger's value array to a copy of the specified
     * array. The intLen is set to the length of the specified array.
     */
    public void copyValue(int[] val) {
        o.copyValue(ptr, val);
    }

    /**
     * Returns true iff this MutableBigInteger has a value of one.
     */
    public boolean isOne() {
        return o.isOne(ptr);
    }

    /**
     * Returns true iff this MutableBigInteger has a value of zero.
     */
    public boolean isZero() {
        return o.isZero(ptr);
    }

    /**
     * Returns true iff this MutableBigInteger is even.
     */
    public boolean isEven() {
        return o.isEven(ptr);
    }

    /**
     * Returns true iff this MutableBigInteger is odd.
     */
    public boolean isOdd() {
        return o.isOdd(ptr);
    }

    /**
     * Returns true iff this MutableBigInteger is in normal form. A
     * MutableBigInteger is in normal form if it has no leading zeros
     * after the offset, and intLen + offset <= value.length.
     */
    public boolean isNormal() {
        return o.isNormal(ptr);
    }

    /**
     * Returns a String representation of this MutableBigInteger in radix 10.
     */
    public String toString() {
        return ptr.toString();
    }

    /**
     * Like {@link #rightShift(int)} but {@code n} can be greater than the length of the number.
     */
    public void safeRightShift(int n) {
        o.safeRightShift(ptr, n);
    }

    /**
     * Right shift this MutableBigInteger n bits. The MutableBigInteger is left
     * in normal form.
     */
    public void rightShift(int n) {
        o.rightShift(ptr, n);
    }

    /**
     * Like {@link #leftShift(int)} but {@code n} can be zero.
     */
    public void safeLeftShift(int n) {
        o.safeLeftShift(ptr, n);
    }

    /**
     * Left shift this MutableBigInteger n bits.
     */
    public void leftShift(int n) {
        o.leftShift(ptr, n);
    }

    /**
     * Adds the contents of two MutableBigInteger objects.The result
     * is placed within this MutableBigInteger.
     * The contents of the addend are not changed.
     */
    public void add(MutableBigInteger addend) {
        o.add(ptr, addend.ptr);
    }

    /**
     * Adds the value of {@code addend} shifted {@code n} ints to the left.
     * Has the same effect as {@code addend.leftShift(32*ints); add(addend);}
     * but doesn't change the value of {@code addend}.
     */
    public void addShifted(MutableBigInteger addend, int n) {
        o.addShifted(ptr, addend.ptr, n);
    }

    /**
     * Like {@link #addShifted(MutableBigInteger, int)} but {@code this.intLen} must
     * not be greater than {@code n}. In other words, concatenates {@code this}
     * and {@code addend}.
     */
    public void addDisjoint(MutableBigInteger addend, int n) {
        o.addDisjoint(ptr, addend.ptr, n);
    }

    /**
     * Adds the low {@code n} ints of {@code addend}.
     */
    public void addLower(MutableBigInteger addend, int n) {
        o.addLower(ptr, addend.ptr, n);
    }

    /**
     * Subtracts the smaller of this and b from the larger and places the
     * result into this MutableBigInteger.
     */
    public int subtract(MutableBigInteger b) {
        return o.subtract(ptr, b.ptr);
    }

    /**
     * Multiply the contents of two MutableBigInteger objects. The result is
     * placed into MutableBigInteger z. The contents of y are not changed.
     */
    public void multiply(MutableBigInteger y, MutableBigInteger z) {
        o.multiply(ptr, y.ptr, z.ptr);
    }

    /**
     * Multiply the contents of this MutableBigInteger by the word y. The
     * result is placed into z.
     */
    public void mul(int y, MutableBigInteger z) {
        o.mul(ptr, y, z.ptr);
    }

    /**
     * This method is used for division of an n word dividend by a one word
     * divisor. The quotient is placed into quotient. The one word divisor is
     * specified by divisor.
     *
     * @return the remainder of the division is returned.
     *
     */
    public int divideOneWord(int divisor, MutableBigInteger quotient) {
        return o.divideOneWord(ptr, divisor, quotient.ptr);
    }

    /**
     * Calculates the quotient of this div b and places the quotient in the
     * provided MutableBigInteger objects and the remainder object is returned.
     *
     */
    public MutableBigInteger divide(MutableBigInteger b, MutableBigInteger quotient) {
        return fromPtr(o.divide(ptr, b.ptr, quotient.ptr));
    }

    public MutableBigInteger divide(MutableBigInteger b, MutableBigInteger quotient, boolean needRemainder) {
        return fromPtr(o.divide(ptr, b.ptr, quotient.ptr, needRemainder));
    }

    /**
     * @see #divideKnuth(MutableBigInteger, MutableBigInteger, boolean)
     */
    public MutableBigInteger divideKnuth(MutableBigInteger b, MutableBigInteger quotient) {
        return fromPtr(o.divideKnuth(ptr, b.ptr, quotient.ptr));
    }

    /**
     * Calculates the quotient of this div b and places the quotient in the
     * provided MutableBigInteger objects and the remainder object is returned.
     *
     * Uses Algorithm D in Knuth section 4.3.1.
     * Many optimizations to that algorithm have been adapted from the Colin
     * Plumb C library.
     * It special cases one word divisors for speed. The content of b is not
     * changed.
     *
     */
    public MutableBigInteger divideKnuth(MutableBigInteger b, MutableBigInteger quotient, boolean needRemainder) {
        return fromPtr(o.divideKnuth(ptr, b.ptr, quotient.ptr, needRemainder));
    }

    /**
     * Computes {@code this/b} and {@code this%b} using the
     * <a href="http://cr.yp.to/bib/1998/burnikel.ps"> Burnikel-Ziegler algorithm</a>.
     * This method implements algorithm 3 from pg. 9 of the Burnikel-Ziegler paper.
     * The parameter beta was chosen to b 2<sup>32</sup> so almost all shifts are
     * multiples of 32 bits.<br/>
     * {@code this} and {@code b} must be nonnegative.
     * @param b the divisor
     * @param quotient output parameter for {@code this/b}
     * @return the remainder
     */
    public MutableBigInteger divideAndRemainderBurnikelZiegler(MutableBigInteger b, MutableBigInteger quotient) {
        return fromPtr(o.divideAndRemainderBurnikelZiegler(ptr, b.ptr, quotient.ptr));
    }

    /** @see BigInteger#bitLength() */
    public long bitLength() {
        return o.bitLength(ptr);
    }

    /**
     * Internally used  to calculate the quotient of this div v and places the
     * quotient in the provided MutableBigInteger object and the remainder is
     * returned.
     *
     * @return the remainder of the division will be returned.
     */
    public long divide(long v, MutableBigInteger quotient) {
        return o.divide(ptr, v, quotient.ptr);
    }

    /**
     * This method divides a long quantity by an int to estimate
     * qhat for two multi precision numbers. It is used when
     * the signed value of n is less than zero.
     * Returns long value where high 32 bits contain remainder value and
     * low 32 bits contain quotient value.
     */
    public static long divWord(long n, int d) {
        return o.divWord(n, d);
    }

    /**
     * Calculate GCD of this and b. This and b are changed by the computation.
     */
    public MutableBigInteger hybridGCD(MutableBigInteger b) {
        return fromPtr(o.hybridGCD(ptr, b.ptr));
    }

    /**
     * Calculate GCD of a and b interpreted as unsigned integers.
     */
    public static int binaryGcd(int a, int b) {
        return o.binaryGcd(a, b);
    }

    /**
     * Returns the modInverse of this mod p. This and p are not affected by
     * the operation.
     */
    public MutableBigInteger mutableModInverse(MutableBigInteger p) {
        return fromPtr(o.mutableModInverse(ptr, p.ptr));
    }

    /*
     * Calculate the multiplicative inverse of this mod 2^k.
     */
    public MutableBigInteger modInverseMP2(int k) {
        return fromPtr(o.modInverseMP2(ptr, k));
    }

    /**
     * Returns the multiplicative inverse of val mod 2^32.  Assumes val is odd.
     */
    public static int inverseMod32(int val) {
        return o.inverseMod32(val);
    }

    /**
     * Returns the multiplicative inverse of val mod 2^64.  Assumes val is odd.
     */
    public static long inverseMod64(long val) {
        return o.inverseMod64(val);
    }

    /**
     * Calculate the multiplicative inverse of 2^k mod mod, where mod is odd.
     */
    public static MutableBigInteger modInverseBP2(MutableBigInteger mod, int k) {
        return fromPtr(o.modInverseBP2(mod.ptr, k));
    }

    /**
     * The Fixup Algorithm
     * Calculates X such that X = C * 2^(-k) (mod P)
     * Assumes C<P and P is odd.
     */
    public static MutableBigInteger fixup(MutableBigInteger c, MutableBigInteger p, int k) {
        o.fixup(c.ptr, p.ptr, k);
        return c;
    }

    /**
     * Uses the extended Euclidean algorithm to compute the modInverse of base
     * mod a modulus that is a power of 2. The modulus is 2^k.
     */
    public MutableBigInteger euclidModInverse(int k) {
        return fromPtr(o.euclidModInverse(ptr, k));
    }
}