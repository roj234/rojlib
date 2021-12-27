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
 * @version 0.1
 * @since 2021/5/30 23:3
 */
public final class ByteReader {
    public ByteList bytes;
    public int      rIndex;

    public ByteReader() {}

    public ByteReader(byte[] bytes) {
        refresh(bytes);
    }

    public ByteReader(ByteList bytes) {
        refresh(bytes);
    }

    public final void refresh(ByteList bytes) {
        this.bytes = bytes;
        this.rIndex = 0;
    }

    public final void refresh(byte[] bytes) {
        this.bytes = new ByteList(bytes);
        this.rIndex = 0;
    }

    public final boolean readBoolean() {
        return readByte() == 1;
    }

    public ByteList bytes() {
        return bytes;
    }

    public final byte readByte() {
        //checkLength(1);
        return bytes.get(rIndex++);
    }

    public int readUnsignedByte() {
        return bytes.getU(rIndex++);
    }

    public final short readUByte() {
        //checkLength(1);
        return (short) bytes.getU(rIndex++);
    }

    public final int readVarInt() {
        return readVarInt(true);
    }

    public final int readVarInt(boolean canBeNegative) {
        int value = 0;
        int i = 0;

        while (i <= 28) {
            int chunk = this.readByte();
            value |= (chunk & 0x7F) << i;
            i += 7;
            if ((chunk & 0x80) == 0) {
                return canBeNegative ? ByteList.zag(value) : value;
            }
        }

        throw new RuntimeException("VarInt end tag!");
    }

    public final boolean hasRemaining() {
        return rIndex >= bytes.limit();
    }

    public final String readIntUTF() {
        int count = readInt();
        if (count < 0)
            return null;
        if (count == 0)
            return "";
        try {
            return DUC(count, bytes, rIndex);
        } catch (UTFDataFormatException e) {
            rIndex += count;
            return bytes.getString();
        }
    }

    public String readVarIntUTF() {
        int count = readVarInt(false);
        if (count < 0)
            throw new NegativeArraySizeException(String.valueOf(count));
        if (count == 0)
            return "";
        try {
            return DUC(count, bytes, rIndex);
        } catch (UTFDataFormatException e) {
            rIndex += count;
            return bytes.getString();
        }
    }

    // &0xff将byte值无差异转成int,避免Java自动类型提升后,会保留高位的符号位
    public final int readInt() {
        //checkLength(4);
        int i = rIndex;
        rIndex = i + 4;
        final ByteList bytes = this.bytes;
        return (bytes.getU(i++)) << 24 | (bytes.getU(i++)) << 16 | (bytes.getU(i++)) << 8 | (bytes.getU(i));
    }

    public final long readLong() {
        //checkLength(8);
        int i = rIndex;
        rIndex = i + 8;
        final ByteList bytes = this.bytes;
        return (long) (bytes.getU(i++)) << 56 |
                (long) (bytes.getU(i++)) << 48 |
                (long) (bytes.getU(i++)) << 40 |
                (long) (bytes.getU(i++)) << 32 |
                (long) (bytes.getU(i++)) << 24 |
                (long) (bytes.getU(i++)) << 16 |
                (long) (bytes.getU(i++)) << 8 |
                (long) bytes.getU(i);

    }

    public final float readFloat() {
        return Float.intBitsToFloat(readInt());
    }

    public final double readDouble() {
        return Double.longBitsToDouble(readLong());
    }

    public String readUTF() throws UTFDataFormatException {
        return DUC(readUnsignedShort(), bytes, rIndex);
    }

    public String readUTF(int len) throws UTFDataFormatException {
        return DUC(len, bytes, rIndex);
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
        //checkLength(2);
        int i = rIndex;
        rIndex += 2;
        return (bytes.getU(i++)) << 8 | (bytes.getU(i));
    }

    public final int readUShortLE() {
        //checkLength(2);
        int i = rIndex;
        rIndex += 2;
        return (bytes.getU(i++)) | (bytes.getU(i)) << 8;
    }

    public final short readShort() {
        //checkLength(2);
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