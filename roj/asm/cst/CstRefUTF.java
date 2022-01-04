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

package roj.asm.cst;

import roj.util.ByteList;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/29 17:16
 */
public abstract class CstRefUTF extends Constant {
    private char valueIndex;
    private CstUTF value;

    CstRefUTF(int valueIndex) {
        this.valueIndex = (char) valueIndex;
    }

    CstRefUTF() {}

    public CstUTF getValue() {
        return value;
    }

    public final void setValue(CstUTF value) {
        if (value == null) {
            throw new NullPointerException("value");
        }
        this.value = value;
        this.valueIndex = value.index;
    }

    @Override
    public final void write(ByteList w) {
        w.put(type())
         .putShort(getValueIndex());
    }

    public final String toString() {
        return super.toString() + " : " + (value == null ? (int)valueIndex : value.getString() + " (" + value.getIndex() + ")");
    }

    public final int hashCode() {
        return (value == null ? 0 : value.hashCode()) ^ type();
    }

    public final boolean equals(Object o) {
        return o instanceof CstRefUTF && equals0((CstRefUTF) o);
    }

    public final boolean equals0(CstRefUTF o) {
        if (o == this)
            return true;
        if (o.getClass() != getClass())
            return false;
        return getValueIndex() == o.getValueIndex();
    }

    public final int getValueIndex() {
        return value == null ? valueIndex : value.getIndex();
    }

    @Override
    public final CstRefUTF clone() {
        CstRefUTF slf = (CstRefUTF) super.clone();
        if (value != null)
            slf.value = (CstUTF) value.clone();
        return slf;
    }
}