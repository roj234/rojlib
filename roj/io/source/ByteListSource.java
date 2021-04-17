/*
 * This file is a part of MoreItems
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
package roj.io.source;

import roj.util.ByteList;

import java.io.IOException;

/**
 * Your description here
 *
 * @author Roj233
 * @version 0.1
 * @since 2021/8/18 13:36
 */
public class ByteListSource implements Source {
    private byte[] array;
    private int offset, length, index;
    private boolean dedicated;

    public ByteListSource(byte[] arr) {
        this.array = arr;
        this.offset = 0;
        this.length = arr.length;
    }

    public ByteListSource(ByteList bytes) {
        this.array = bytes.list;
        this.offset = bytes.offset();
        this.length = bytes.pos();
    }

    public void setDedicated(boolean dedicated) {
        this.dedicated = dedicated;
    }

    @Override
    public void seek(long source) {
        this.index = (int) source;
    }

    @Override
    public int read(byte[] array, int offset, int length) {
        if (length < 0 || array.length - offset < length || offset < 0) throw new ArrayIndexOutOfBoundsException();
        if (index > this.offset + this.length)
            return -1;
        length = Math.min(length, this.length - index - this.offset);
        if (length == 0) return 0;
        System.arraycopy(this.array, index + this.offset, array, offset, length);
        index += length;
        return length;
    }

    @Override
    public void write(byte[] array, int offset, int length) throws IOException {
        if (length < 0 || array.length - offset < length || offset < 0) throw new ArrayIndexOutOfBoundsException();
        if (length == 0) return;

        if (!dedicated) {
            byte[] newArray = new byte[length + this.length];
            System.arraycopy(this.array, this.offset, newArray, 0, this.length);
            this.offset = 0;
            this.array = newArray;
            this.dedicated = true;
        } else if (this.array.length - index < length) {
            byte[] newArray = new byte[this.array.length + length + 512];
            System.arraycopy(this.array, 0, newArray, 0, this.length);
            this.array = newArray;
        }
        System.arraycopy(array, offset, this.array, index, length);
        this.index += length;
        this.length += length;
    }

    @Override
    public long position() {
        return index;
    }

    @Override
    public void setLength(long length) throws IOException {
        if (length < 0 || length > Integer.MAX_VALUE - 16) throw new IOException();
        if (this.array.length < length) {
            byte[] newArray = new byte[this.array.length + (int)length];
            System.arraycopy(this.array, offset, newArray, 0, this.length);
            this.array = newArray;
            this.offset = 0;
            this.dedicated = true;
        }
    }

    @Override
    public long length() {
        return length;
    }

    @Override
    public void close() {}

    public int getOffset() {
        return offset;
    }

    public int getIndex() {
        return index;
    }

    public int getLength() {
        return length;
    }

    public byte[] getArray() {
        return array;
    }
}
