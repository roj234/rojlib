/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: ByteReader.java
 */
package roj.util;

import roj.text.CharList;

import javax.annotation.Nonnull;
import java.io.DataInput;
import java.io.IOException;
import java.io.UTFDataFormatException;

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

    public final int readVarInt(boolean zag) {
        int value = 0;
        int i = 0;

        while (i <= 28) {
            int chunk = this.readByte();
            value |= (chunk & 0x7F) << i;
            i += 7;
            if ((chunk & 0x80) == 0) {
                return zag ? zag(value) : value;
            }
        }

        throw new RuntimeException("VarInt end tag!");
    }

    public static int zag(int i) {
        return (i >> 1) & ~(1 << 31) ^ -(i & 1);
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
        final ByteList bytes = readBytesDelegated(count);
        try {
            return decodeUTF(count, bytes);
        } catch (UTFDataFormatException e) {
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
        final ByteList bytes = readBytesDelegated(count);
        try {
            return decodeUTF(count, bytes);
        } catch (UTFDataFormatException e) {
            return bytes.getString();
        }
    }

    public String readVString() {
        int count = readVarInt(false);
        if (count < 0)
            throw new NegativeArraySizeException(String.valueOf(count));
        if (count == 0)
            return "";
        final ByteList bytes = readBytesDelegated(count);
        try {
            return decodeUTF(count, bytes);
        } catch (UTFDataFormatException e) {
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

    public final long readLongReversed() {
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
    public String readLine() throws IOException {
        throw new IOException("懒得做");
    }

    @Override
    public String readUTF() throws UTFDataFormatException {
        return readUTF0(true);
    }

    public String readVUTF() {
        try {
            return readUTF0(false);
        } catch (UTFDataFormatException e) {
            throw new RuntimeException(e);
        }
    }

    private String readUTF0(boolean javaUTF) throws UTFDataFormatException {
        int len = javaUTF ? readUnsignedShort() : readVarInt(false);

        ByteList bytes = readBytesDelegated(len);

        return decodeUTF(len, bytes);
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

        decode0(max, out, in, 0, 0);
    }

    public static int decodeUTFPartial(int inputOff, int max, CharList out, ByteList in) throws UTFDataFormatException {
        if (max < 0)
            max = in.pos();

        return decode0(max, out, in, inputOff, 1) - inputOff;
    }

    public static void decodeUTFExternal(int max, CharList out, ByteList in) throws UTFDataFormatException {
        if (max < 0)
            max = in.pos();

        out.ensureCapacity(max);

        decode0(max, out, in, 0, 2);
    }

    public static int decodeUTFPartialExternal(int inputOff, int max, CharList out, ByteList in) throws UTFDataFormatException {
        if (max < 0)
            max = in.pos();

        return decode0(max, out, in, inputOff, 3) - inputOff;
    }

    private static int decode0(int max, CharList out, ByteList in, int i, int flag) throws UTFDataFormatException {
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
            out.append(k);
    }

    private CharList cc;
    private String decodeUTF(int len, ByteList bytes) throws UTFDataFormatException {
        if (this.cc == null) {
            this.cc = new CharList(len);
        }
        decodeUTF(len, cc, bytes);
        String s = cc.toString();
        cc.clear();
        return s;
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

    private byte bitIndex, numBits, numBytes;
    private int bitMask;

    public final int readBit1() {
        byte bi = this.bitIndex;
        int bit = ((this.bytes.get(index) << (bi & 0x7)) >>> 7) & 0x1;

        this.index += (++bi) >> 3;
        this.bitIndex = (byte) (bi & 0x7);
        return bit;
    }

    public final int readBit2() {
        final int i = index;
        final int b = this.bytes.get(i) << 8 | this.bytes.get(i + 1);

        int bi = this.bitIndex;
        int result = (b >>> (bi & 0x7)) & this.bitMask;
        this.index += (bi += this.numBits) >> 3;
        this.bitIndex = (byte) (bi & 0x7);
        return result;
    }

    public final int readBit3() {
        final int i = index;
        final int b = this.bytes.get(i) << 16 | this.bytes.get(i + 1) << 8 | this.bytes.get(i + 2);

        int bi = this.bitIndex;
        int result = (b >>> (bi & 0x7)) & this.bitMask;
        this.index += (bi += this.numBits) >> 3;
        this.bitIndex = (byte) (bi & 0x7);
        return result;
    }

    public final int readBit() {
        switch (numBytes) {
            case 1:
                return readBit1();
            case 2:
                return readBit2();
            case 3:
                return readBit3();
        }
        return 0;
    }

    public final void setNumBits(int numBits) {
        if (numBits < 1 || numBits > 17)
            throw new IllegalArgumentException("get bit count must in [1,17]");
        this.numBits = (byte) numBits;
        switch (numBits) {
            case 1:
                numBytes = 1;
                break;
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
            case 9:
                numBytes = 2;
                break;
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
            case 16:
            case 17:
                numBytes = 3;
                break;
        }
        this.bitMask = (1 << numBits) - 1;
    }

    public static boolean isBitTrue(int i, int bit) {
        return (i & (1 << bit)) != 0;
    }

    public static int readBits(int n, int fromInclude, int toInclude) {
        int k = 0;
        for (int i = fromInclude; i <= toInclude; i++) {
            k |= (1 << i);
        }
        return (n & k) >>> fromInclude;
    }

    public ByteList readBytesDelegated(int length) {
        if (length == 0)
            return new ByteList.EmptyByteList();
        ByteList list = this.bytes.subList(index, length);
        index += length;
        return list;
    }

    public int remainSize() {
        return this.bytes.limit() - this.bytes.pos();
    }

    public int length() {
        return this.bytes.limit();
    }
}