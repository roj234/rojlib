package roj.math;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: MutableInt.java
 */
public class MutableInt extends Number implements Comparable<MutableInt> {
    private static final long serialVersionUID = 512176391864L;
    private int value;

    public MutableInt() {
    }

    public MutableInt(int value) {
        this.value = value;
    }

    public MutableInt(Number value) {
        this.value = value.intValue();
    }

    public MutableInt(String value) throws NumberFormatException {
        this.value = Integer.parseInt(value);
    }

    public int getValue() {
        return this.value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public void setValue(Number value) {
        this.value = value.intValue();
    }

    public void increment() {
        ++this.value;
    }

    public int getAndIncrement() {
        return this.value++;
    }

    public int incrementAndGet() {
        ++this.value;
        return this.value;
    }

    public void decrement() {
        --this.value;
    }

    public int getAndDecrement() {
        return this.value--;
    }

    public int decrementAndGet() {
        --this.value;
        return this.value;
    }

    public void add(int i) {
        this.value += i;
    }

    public void add(Number i) {
        this.value += i.intValue();
    }

    public void subtract(int i) {
        this.value -= i;
    }

    public void subtract(Number i) {
        this.value -= i.intValue();
    }

    public int addAndGet(int i) {
        this.value += i;
        return this.value;
    }

    public int addAndGet(Number i) {
        this.value += i.intValue();
        return this.value;
    }

    public int getAndAdd(int i) {
        int last = this.value;
        this.value += i;
        return last;
    }

    public int getAndAdd(Number i) {
        int last = this.value;
        this.value += i.intValue();
        return last;
    }

    @Override
    public int intValue() {
        return this.value;
    }

    @Override
    public long longValue() {
        return this.value;
    }

    @Override
    public float floatValue() {
        return (float) this.value;
    }

    @Override
    public double doubleValue() {
        return this.value;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MutableInt) {
            return this.value == ((MutableInt) obj).value;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return this.value;
    }

    @Override
    public int compareTo(MutableInt other) {
        return Integer.compare(this.value, other.value);
    }

    @Override
    public String toString() {
        return String.valueOf(this.value);
    }

    public int xor(int v) {
        return this.value ^ v;
    }

    public int and(int v) {
        return this.value & v;
    }

    public int or(int v) {
        return this.value | v;
    }

    public void xorEq(int v) {
        this.value ^= v;
    }

    public void andEq(int v) {
        this.value &= v;
    }

    public void orEq(int v) {
        this.value |= v;
    }
}
