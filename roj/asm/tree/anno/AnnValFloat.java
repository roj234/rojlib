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

package roj.asm.tree.anno;

import roj.asm.util.ConstantPool;
import roj.util.ByteList;

/**
 * @author Roj234
 * @since 2021/2/7 20:39
 */
public final class AnnValFloat extends AnnVal {
    public AnnValFloat(float value) {
        this.value = value;
    }

    public float value;

    @Override
    public int asInt() {
        return (int) value;
    }

    @Override
    public double asDouble() {
        return value;
    }

    @Override
    public float asFloat() {
        return value;
    }

    @Override
    public long asLong() {
        return (long) value;
    }

    public void toByteArray(ConstantPool pool, ByteList w) {
        w.put((byte) FLOAT).putShort(pool.getFloatId(value));
    }

    public String toString() {
        return String.valueOf(value);
    }

    @Override
    public byte type() {
        return FLOAT;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AnnValFloat aFloat = (AnnValFloat) o;

        return Float.compare(aFloat.value, value) == 0;
    }

    @Override
    public int hashCode() {
        return (value != +0.0f ? Float.floatToIntBits(value) : 0);
    }
}