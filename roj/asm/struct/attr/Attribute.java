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

package roj.asm.struct.attr;

import roj.asm.util.ConstantWriter;
import roj.util.ByteList;
import roj.util.ByteWriter;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/26 23:26
 */
public abstract class Attribute {
    protected Attribute(String name) {
        this.name = name;
    }

    public final String name;

    public final ByteWriter toByteArray(ConstantWriter pool, ByteWriter w) {
        w.writeShort(pool.getUtfId(name)).writeInt(-1);
        ByteList list = w.list;

        int lenIdx = list.pos();
        toByteArray1(pool, w);
        int cp = list.pos();

        list.pos(lenIdx - 4);
        w.writeInt(cp - lenIdx);
        list.pos(cp);

        return w;
    }

    protected void toByteArray1(ConstantWriter pool, ByteWriter w) {
        throw new InternalError("Subclasses should rewrite this: " + this.getClass().getName());
    }

    public String toString() {
        throw new InternalError("Subclasses should rewrite this: " + this.getClass().getName());
    }

    public ByteList getRawData() {
        return null;
    }

    public void setRawData(ByteList data) {
        throw new UnsupportedOperationException();
    }
}