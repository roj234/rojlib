/*
 * This file is a part of MI
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 Roj234
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package roj.math;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
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
