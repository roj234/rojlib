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
package roj.config.data;

import roj.util.ByteList;

import javax.annotation.Nonnull;

/**
 * @author Roj234
 * @version 0.1
 * @since 2021/5/31 21:17
 */
public final class CDouble extends CEntry {
    public double value;

    public CDouble(double number) {
        this.value = number;
    }

    public static CDouble valueOf(double number) {
        return new CDouble(number);
    }

    public static CDouble valueOf(String number) {
        return valueOf(Double.parseDouble(number));
    }

    @Override
    public double asDouble() {
        return value;
    }

    @Override
    public int asInteger() {
        return (int) value;
    }

    @Override
    public long asLong() {
        return (long) value;
    }

    @Nonnull
    @Override
    public Type getType() {
        return Type.DOUBLE;
    }

    @Nonnull
    @Override
    public String asString() {
        return String.valueOf(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CDouble that = (CDouble) o;
        return Double.compare(that.value, value) == 0;
    }

    @Override
    public boolean equalsTo(CEntry entry) {
        return entry.getType().fits(Type.DOUBLE) && entry.asDouble() == value;
    }

    @Override
    public int hashCode() {
        return Float.floatToIntBits((float) value);
    }

    @Override
    public StringBuilder toYAML(StringBuilder sb, int depth) {
        return sb.append(value);
    }

    @Override
    public StringBuilder toJSON(StringBuilder sb, int depth) {
        return sb.append(value);
    }

    @Override
    public StringBuilder toINI(StringBuilder sb, int depth) {
        return sb.append(value);
    }

    @Override
    public StringBuilder toTOML(StringBuilder sb, int depth, CharSequence chain) {
        return sb.append(value);
    }

    @Override
    public Object unwrap() {
        return value;
    }

    @Override
    public void toBinary(ByteList w) {
        w.put((byte) Type.DOUBLE.ordinal()).putDouble(value);
    }

    @Override
    protected boolean isSimilar(CEntry value) {
        return value.getType().isNumber();
    }
}
