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

import org.jetbrains.annotations.ApiStatus.Internal;
import roj.util.ByteList;

/**
 * @author Roj234
 * @version 0.1
 * @since 2021/5/29 17:16
 */
public abstract class Constant implements Cloneable {
    char index;

    Constant() {}

    @Internal
    public abstract void write(ByteList w);

    @Override
    public abstract boolean equals(Object o);

    @Override
    public abstract int hashCode();

    public String toString() {
        return CstType.toString(type()) + "#" + (int)index;
    }

    @Internal
    public void setIndex(int index) {
        this.index = (char) index;
    }

    public int getIndex() {
        return index;
    }

    public abstract byte type();

    @Override
    public Constant clone() {
        try {
            return (Constant) super.clone();
        } catch (CloneNotSupportedException unable) {}
        return null;
    }
}