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

import roj.math.MathUtils;

import javax.annotation.Nonnull;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/31 21:17
 */
public final class CInteger extends CEntry {
    public int value;

    public CInteger(int number) {
        this.value = number;
    }

    public static CInteger valueOf(int number) {
        return new CInteger(number);
    }

    public static CInteger valueOf(String number) {
        return valueOf(MathUtils.parseInt(number));
    }

    @Override
    public double asDouble() {
        return value;
    }

    @Override
    public int asInteger() {
        return value;
    }

    @Override
    public long asLong() {
        return value;
    }

    @Nonnull
    @Override
    public Type getType() {
        return Type.INTEGER;
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
        CInteger that = (CInteger) o;
        return that.value == value;
    }

    @Override
    public boolean equalsTo(CEntry entry) {
        return entry.getType().fits(Type.INTEGER) && entry.asInteger() == value;
    }

    @Override
    public int hashCode() {
        return value;
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
    public Object toNudeObject() {
        return value;
    }

    @Override
    protected boolean isSimilar(CEntry value) {
        return value.getType().isNumber();
    }
}
