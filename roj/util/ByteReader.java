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

import javax.annotation.Nonnull;
import java.io.DataInput;
import java.io.UTFDataFormatException;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/30 23:3
 */
public final class ByteReader implements DataInput {
    private ByteList bytes;
    public int index;

    public ByteReader() {}

    public ByteReader(byte[] bytes) {
        refresh(bytes);
    }

    public ByteReader(ByteList bytes) {
        refresh(bytes);
    }

    public final void refresh(ByteList bytes) {
        this.bytes = bytes;
        this.index = 0;
    }

    public final void refresh(byte[] bytes) {
        this.bytes = new ByteList(bytes);
        this.index = 0;
    }

    @Override
    public void readFully(@Nonnull byte[] bytes) {
        readBytes(bytes);
    }

    @Override
    public void readFully(@Nonnull byte[] bytes, int off, int len) {
        readBytes(bytes, off, len);
    }

    @Override
    public int skipBytes(int i) {
        int skipped = Math.min(bytes.limit() - index, i);
        index += skipped;
        return skipped;
    }

    @Override
    public final boolean readBoolean() {
        return readByte() == 1;
    }

    public ByteList getBytes() {
        return bytes;
    }

    @Override
    public final byte readByte() {
        //checkLength(1);
        return bytes.get(index++);
    }

    @Override
    public int readUnsignedByte() {
        return bytes.getU(index++);
    }

    public final short readUByte() {
        //checkLength(1);
        return (short) bytes.getU(index++);
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
                return canBeNegative ? zag(value) : value;
            }
        }

        throw new RuntimeException("VarInt end tag!");
    }

    public static int zag(int i) {
        return (i >> 1) & ~(1 << 31) ^ -(i & 1);
    }

    public final long readVarLong() {
        return readVarInt(true);
    }

    public final long readVarLong(boolean canBeNegative) {
        long value = 0;
        int i = 0;

        while (i <= 63) {
            int chunk = this.readByte();
            value |= (chunk & 0x7F) << i;
            i += 7;
            if ((chunk & 0x80) == 0) {
                return canBeNegative ? zag(value) : value;
            }
        }

        throw new RuntimeException("VarLong end tag!");
    }

    public static long zag(long i) {
        return (i >> 1) & ~(1L << 63) ^ -(i & 1);
    }

    public final boolean isFinished() {
        return index >= bytes.limit();
    }

    public final String readString() {
        int count = readInt();
        if (count < 0)
            return null;
        if (count == 0)
            return "";
        try {
            return DUC(count, bytes, index);
        } catch (UTFDataFormatException e) {
            index += count;
            return bytes.getString();
        }
    }


    public final String readString(int max) {
        int count = readInt();
        if (count < 0)
            return null;
        if (count == 0)
            return "";
        if (count > max) {
            throw new RuntimeException("The string received is larger than maximum allowed " + max);
        }
        try {
            return DUC(count, bytes, index);
        } catch (UTFDataFormatException e) {
            index += count;
            return bytes.getString();
        }
    }

    public String readVString() {
        int count = readVarInt(false);
        if (count < 0)
            throw new NegativeArraySizeException(String.valueOf(count));
        if (count == 0)
            return "";
        try {
            return DUC(count, bytes, index);
        } catch (UTFDataFormatException e) {
            index += count;
            return bytes.getString();
        }
    }


    public final void readBytes(byte[] array, int start, int length) {
        //checkLength(length);
        if (length == 0)
            return;
        System.arraycopy(bytes.list, index + bytes.offset(), array, start, length);
        index += length;
    }


    public final byte[] readBytes(int length) {
        //checkLength(length);
        byte[] result = new byte[length];
        readBytes(result, 0, length);
        return result;
    }

    public final void readBytes(byte[] array) {
        readBytes(array, 0, array.length);
    }

    // &0xff将byte值无差异转成int,避免Java自动类型提升后,会保留高位的符号位
    @Override
    public final int readInt() {
        //checkLength(4);
        int i = index;
        index = i + 4;
        final ByteList bytes = this.bytes;
        return (bytes.getU(i++)) << 24 | (bytes.getU(i++)) << 16 | (bytes.getU(i++)) << 8 | (bytes.getU(i));
    }

    public final int readIntR() {
        //checkLength(4);
        int i = index;
        index = i + 4;
        final ByteList bytes = this.bytes;
        return (bytes.getU(i++)) | (bytes.getU(i++)) << 8 | (bytes.getU(i++)) << 16 | (bytes.getU(i)) << 24;
    }

    public final long readUInt() {
        //checkLength(4);
        int i = index;
        index = i + 4;
        final ByteList bytes = this.bytes;
        return (long) (bytes.getU(i++)) << 24 |
                (long) (bytes.getU(i++)) << 16 |
                (bytes.getU(i++)) << 8 |
                (bytes.getU(i));
    }

    public final long readUIntReversed() {
        //checkLength(4);
        int i = index;
        index = i + 4;
        final ByteList bytes = this.bytes;
        return (long) (bytes.get(i++) & 0xff) |
                (long) (bytes.get(i++) & 0xff) << 8 |
                (long) (bytes.get(i++) & 0xff) << 16 |
                (long) (bytes.get(i) & 0xff) << 24;
    }

    public final long readLongR() {
        //checkLength(8);
        int i = index;
        index = i + 8;
        final ByteList bytes = this.bytes;
        return (long) (bytes.getU(i++)) |
                (long) (bytes.getU(i++)) << 8 |
                (long) (bytes.getU(i++)) << 16 |
                (long) (bytes.getU(i++)) << 24 |
                (long) (bytes.getU(i++)) << 32 |
                (long) (bytes.getU(i++)) << 40 |
                (long) (bytes.getU(i++)) << 48 |
                (long) (bytes.getU(i)) << 56;

    }

    public final long readLong() {
        //checkLength(8);
        int i = index;
        index = i + 8;
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

    @Override
    public final float readFloat() {
        return Float.intBitsToFloat(readInt());
    }

    @Override
    public final double readDouble() {
        return Double.longBitsToDouble(readLong());
    }

    @Override
    public String readLine() {
        CharList cl = new CharList();
        int start = index;
        ByteList bytes = this.bytes;
        while (start < bytes.pos()) {
            byte b = bytes.get(start++);
            if(b == '\r' || b == '\n') {
                if(b == '\r' && start < bytes.pos() && bytes.get(start) == '\n')
                    start++;
                break;
            }
            cl.append((char) b);
        }
        index = start;
        return cl.toString();
    }

    @Override
    public String readUTF() throws UTFDataFormatException {
        return DUC(readUnsignedShort(), bytes, index);
    }

    public String readUTF0(int len) throws UTFDataFormatException {
        return DUC(len, bytes, index);
    }

    private CharList cache;
    private String DUC(int len, ByteList bytes, int srcOffset) throws UTFDataFormatException {
        if (this.cache == null) {
            this.cache = new CharList(len);
        }
        decodeUTF0(len + srcOffset, cache, bytes, srcOffset, 0);
        index += len;
        String s = cache.toString();
        cache.clear();
        return s;
    }

    public ByteList readZeroEnd(int max) {
        int i = index;
        while (max-- > 0) {
            if(bytes.get(i++) == 0) {
                ByteList sub = bytes.subList(index, i - index);
                index = i;
                return sub;
            }
        }
        return null;
    }

    public static String readUTF(ByteList list) throws UTFDataFormatException {
        CharList cl = new CharList(list.pos());
        decodeUTF(-1, cl, list);
        return cl.toString();
    }

    public static void decodeUTF(int max, CharList out, ByteList in) throws UTFDataFormatException {
        if (max < 0)
            max = in.pos();

        // The number of chars produced may be less than length
        out.ensureCapacity(max);

        decodeUTF0(max, out, in, 0, 0);
    }

    public static int decodeUTFPartialExternal(int inputOff, int max, CharList out, ByteList in) throws UTFDataFormatException {
        if (max < 0)
            max = in.pos();

        return decodeUTF0(max, out, in, inputOff, FLAG_EXTERNAL | FLAG_PARTIAL) - inputOff;
    }

    public static final int FLAG_PARTIAL = 1, FLAG_EXTERNAL = 2;
    @SuppressWarnings("fallthrough")
    public static int decodeUTF0(int max, CharList out, ByteList in, int i, int flag) throws UTFDataFormatException {
        int c;
        while (i < max) {
            c = in.getU(i);
            if (c > 127) break;
            i++;
            out.append((char) c);
        }

        int c2, c3;
        cyl:
        while (i < max) {
            c = in.getU(i);
            sw:
            switch (c >> 4) {
                case 0:
                case 1:
                case 2:
                case 3:
                case 4:
                case 5:
                case 6:
                case 7:
                    /* 0xxxxxxx*/
                    i++;
                    out.append((char) c);
                    break;
                case 12:
                case 13:
                    /* 110x xxxx   10xx xxxx*/
                    i += 2;
                    if (i > max) {
                        if((flag & 1) != 0) {
                            i -= 2;
                            break cyl;
                        } else {
                            throw new UTFDataFormatException("malformed input: partial character at end");
                        }
                    }

                    c2 = in.get(i - 1);
                    if ((c2 & 0xC0) != 0x80) {
                        throw new UTFDataFormatException("malformed input around byte " + (i - 1));
                    }
                    out.append((char) (((c & 0x1F) << 6) |
                            (c2 & 0x3F)));
                    break;
                case 14:
                    /* 1110 xxxx  10xx xxxx  10xx xxxx */
                    i += 3;
                    if (i > max) {
                        if((flag & 1) != 0) {
                            i -= 3;
                            break cyl;
                        } else {
                            throw new UTFDataFormatException("malformed input: partial character at end");
                        }
                    }

                    c2 = in.get(i - 2);
                    c3 = in.get(i - 1);
                    if (((c2 & 0xC0) != 0x80) || ((c3 & 0xC0) != 0x80)) {
                        throw new UTFDataFormatException("malformed input around byte " + i);
                    }

                    out.append((char) ((
                            (c & 0x0F) << 12) |
                            ((c2 & 0x3F) << 6) |
                            c3 & 0x3F));
                    break;
                case 15:
                    if((flag & 2) != 0)
                        switch (c & 14) {
                            case 2:
                            case 4:
                            case 6:  /* 1111 0xxx [10xx xxxx] * 3  注意：非java标准编码 */
                                if (i + 4 > max)
                                    if((flag & 1) != 0) {
                                        i -= 4;
                                        break cyl;
                                    } else {
                                        throw new UTFDataFormatException("malformed input: partial character at end");
                                    }
                                read0(in, i, out, 3);
                                i += 4;
                                break sw;
                            case 8:
                            case 10:  /* 1111 0xxx [10xx xxxx] * 4  注意：非UTF-8标准编码 */
                                if (i + 5 > max)
                                    if((flag & 1) != 0) {
                                        i -= 5;
                                        break cyl;
                                    } else {
                                        throw new UTFDataFormatException("malformed input: partial character at end");
                                    }
                                read0(in, i, out, 4);
                                i += 5;
                                break sw;
                            case 12:  /* 1111 0xxx [10xx xxxx] * 5  注意：非UTF-8标准编码 */
                                if (i + 6 > max)
                                    if((flag & 1) != 0) {
                                        i -= 6;
                                        break cyl;
                                    } else {
                                        throw new UTFDataFormatException("malformed input: partial character at end");
                                    }
                                read0(in, i, out, 5);
                                i += 6;
                                break sw;
                            case 14:
                                // error
                        }
                default:
                    /* 10xx xxxx,  1111 xxxx */
                    if((flag & 1) != 0) {
                        i --;
                        break cyl;
                    }
                    throw new UTFDataFormatException("malformed input around byte " + i);
            }
        }

        return i;
    }

    private static void read0(ByteList in, int i, CharList out, int len) throws UTFDataFormatException {
        int k = in.get(i++) & 0x0F;
        while (i < len) {
            k <<= 6;
            int code = in.get(i++);
            if ((code & 0xC0) != 0x80)
                throw new UTFDataFormatException("malformed input around byte " + (i - 1));
            k |= code;
        }

        if(k > 65535)
            out.appendCodePoint(k);
        else
            out.append((char) k);
    }

    @Override
    public final int readUnsignedShort() {
        //checkLength(2);
        int i = index;
        index += 2;
        return (bytes.getU(i++)) << 8 | (bytes.getU(i));
    }

    public final int readUShortR() {
        //checkLength(2);
        int i = index;
        index += 2;
        return (bytes.getU(i++)) | (bytes.getU(i)) << 8;
    }

    @Override
    public final short readShort() {
        //checkLength(2);
        int i = index;
        index += 2;
        return (short) ((bytes.getU(i++)) << 8 | (bytes.getU(i)));
    }

    @Override
    public char readChar() {
        return (char) readUnsignedShort();
    }

    public byte bitIndex;

    public final int readBit1() {
        byte bi = this.bitIndex;
        int bit = ((this.bytes.get(index) << (bi & 0x7)) >>> 7) & 0x1;

        this.index += (++bi) >> 3;
        this.bitIndex = (byte) (bi & 0x7);
        return bit;
    }

    public final int readBit(int numBits) {
        switch (numBits) {
            case 1:
                return readBit1();
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
            case 9: {
                int d = (((((bytes.get(index) & 0xFF) << 8) | (bytes.get0(
                        index + 1) & 0xFF)) << bitIndex) & 0xFFFF) >> 16 - numBits;
                index += (bitIndex += numBits) >> 3;
                bitIndex &= 0x7;
                return d;
            }
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
            case 16:
            case 17: {
                int d = (((((bytes.get(index) & 0xFF) << 16) | ((bytes.get0(index + 1) & 0xFF) << 8) | (bytes.get0(
                        index + 2) & 0xFF)) << bitIndex) & 0xFFFFFF) >> 24 - numBits;
                index += (bitIndex += numBits) >> 3;
                bitIndex &= 0x7;
                return d;
            }
            default:
                throw new IllegalArgumentException("get bit count must in [1,17]");
        }
    }

    public void skipBits(int i) {
        bitIndex += i;
        this.index += bitIndex >> 3;
        this.bitIndex = (byte) (bitIndex & 0x7);
    }

    public ByteList readBytesDelegated(int length) {
        if (length == 0)
            return new ByteList.EmptyByteList();
        ByteList list = this.bytes.subList(index, length);
        index += length;
        return list;
    }

    public int remain() {
        return this.bytes.limit() - index;
    }

    public int length() {
        return this.bytes.limit();
    }
}