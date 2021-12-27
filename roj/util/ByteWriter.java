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

package roj.util;

import roj.concurrent.Ref;

import java.io.DataOutput;
import java.io.IOException;
import java.io.OutputStream;

/**
 * @author Roj234
 * @version 0.1
 * @since 2021/5/30 23:4
 * @deprecated 它现在所能做的并不比一个 {@link Ref}&lt;{@link ByteList}&gt;更好
 */
@Deprecated
public class ByteWriter {
    public ByteList list;

    public ByteWriter() {
        list = new ByteList();
    }

    public ByteWriter(int len) {
        list = new ByteList(len);
    }

    public ByteWriter(ByteList list) {
        this.list = list;
    }

    public DataOutput asDataOutput() {
        return list.asOutputStream();
    }

    public ByteWriter putBool(boolean b) {
        list.put((byte) (b ? 1 : 0));
        return this;
    }

    public ByteWriter put(byte b) {
        list.put(b);
        return this;
    }

    public ByteWriter putVarInt(int i) {
        list.putVarLong(i);
        return this;
    }

    public ByteWriter putVarInt(int i, boolean canBeNegative) {
        list.putVarLong(i, canBeNegative);
        return this;
    }

    public ByteWriter putIntUTF(CharSequence s) {
        list.putIntUTF(s);
        return this;
    }

    public ByteWriter putVarIntUTF(CharSequence s) {
        list.putVarIntUTF(s);
        return this;
    }

    public ByteWriter put(byte[] bs) {
        list.put(bs);
        return this;
    }

    public ByteWriter put(ByteList list) {
        this.list.put(list);
        return this;
    }

    public ByteWriter put(ByteWriter w) {
        return put(w.list);
    }

    public ByteWriter putInt(int i) {
        list.putInt(i);
        return this;
    }

    public ByteWriter putLong(long l) {
        list.putLong(l);
        return this;
    }

    public ByteWriter putFloat(float f) {
        putInt(Float.floatToIntBits(f));
        return this;
    }

    public ByteWriter putDouble(double d) {
        putLong(Double.doubleToLongBits(d));
        return this;
    }

    public ByteWriter putShort(int s) {
        list.put((byte) (s >>> 8));
        list.put((byte) (s));
        return this;
    }

    public ByteWriter putShortLE(int s) {
        list.put((byte) (s));
        list.put((byte) (s >>> 8));
        return this;
    }

    public byte[] toByteArray() {
        return list.toByteArray();
    }

    public void writeToStream(OutputStream os) throws IOException {
        list.writeToStream(os);
    }

    public void put(byte[] array, int off, int len) {
        list.put(array, off, len);
    }

    public void putJavaUTF(CharSequence s) {
        ByteList.writeUTF(list, s, 0);
    }
}