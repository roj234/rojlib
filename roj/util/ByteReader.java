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

import roj.text.CharList;
import roj.util.ByteList.WriteOnly;

import java.io.UTFDataFormatException;

/**
 * 这东西还能有点P用，相当于独立的ReaderIndex
 * @author Roj234
 * @since 2021/5/30 23:3
 */
public final class ByteReader {
    public ByteList bytes;
    public int      rIndex;

    public ByteReader() {}

    public ByteReader(byte[] bytes) {
        this.bytes = new ByteList(bytes);
    }

    public ByteReader(ByteList bytes) {
        refresh(bytes);
    }

    public final void refresh(ByteList bytes) {
        this.bytes = bytes;
        this.rIndex = bytes.rIndex;
    }

    public ByteList bytes() {
        return bytes;
    }

    public final byte readByte() {
        return bytes.get(rIndex++);
    }

    public final short readUByte() {
        return (short) bytes.getU(rIndex++);
    }

    public final boolean hasRemaining() {
        return rIndex < bytes.limit();
    }

    public final int readInt() {
        int i = rIndex;
        rIndex = i + 4;
        final ByteList bytes = this.bytes;
        return (bytes.getU(i++)) << 24 | (bytes.getU(i++)) << 16 | (bytes.getU(i++)) << 8 | (bytes.getU(i));
    }

    public String readUTF() throws UTFDataFormatException {
        return DUC(readUnsignedShort(), bytes, rIndex);
    }

    private CharList cache;
    private String DUC(int len, ByteList bytes, int srcOffset) throws UTFDataFormatException {
        if (this.cache == null) {
            this.cache = new CharList(len);
        }
        ByteList.decodeUTF0(len + srcOffset, cache, bytes, srcOffset, 0);
        rIndex += len;
        String s = cache.toString();
        cache.clear();
        return s;
    }

    public final int readUnsignedShort() {
        int i = rIndex;
        rIndex += 2;
        return (bytes.getU(i++)) << 8 | (bytes.getU(i));
    }

    public final short readShort() {
        int i = rIndex;
        rIndex += 2;
        return (short) ((bytes.getU(i++)) << 8 | (bytes.getU(i)));
    }

    public char readChar() {
        return (char) readUnsignedShort();
    }

    public ByteList slice(int length) {
        if (length == 0)
            return new WriteOnly();
        ByteList list = this.bytes.slice(rIndex, length);
        rIndex += length;
        return list;
    }

    public int remaining() {
        return this.bytes.limit() - rIndex;
    }

    public int length() {
        return this.bytes.limit();
    }
}