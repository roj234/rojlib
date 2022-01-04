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
import roj.text.TextUtil;

import javax.annotation.Nonnull;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * @author Roj234
 * @version 0.1
 * @since 2021/5/29 20:45
 */
public class ByteList implements DataInput {
    public byte[] list;
    protected int wIndex;

    public ByteList() {
        this.list = EmptyArrays.BYTES;
    }

    public ByteList(int len) {
        list = new byte[len];
    }

    public ByteList(byte[] array) {
        list = array;
        wIndex = array.length;
    }

    public int wIndex() {
        return wIndex;
    }

    public ByteList put(byte e) {
        ensureCapacity(wIndex + 1);
        list[wIndex++] = e;
        return this;
    }

    public void ensureCapacity(int required) {
        if (required > list.length) {
            byte[] newList = new byte[list.length == 0 ? Math.max(required, 256) : ((required * 3) >>> 1) + 1];

            if (wIndex > 0)
                System.arraycopy(list, 0, newList, 0, wIndex);
            list = newList;
        }
    }

    public ByteList put(byte[] b) {
        return put(b, 0, b.length);
    }

    public ByteList put(byte[] b, int off, int len) {
        if (len < 0 || off < 0 || len > b.length - off)
            throw new ArrayIndexOutOfBoundsException();
        if (len > 0) {
            ensureCapacity(wIndex + len);
            System.arraycopy(b, off, list, wIndex, len);
            wIndex += len;
        }
        return this;
    }

    public ByteList put(ByteList b) {
        if (b.getClass() == WriteOnly.class)
            return this;
        put(b.list, b.arrayOffset(), b.wIndex - b.arrayOffset());
        return this;
    }

    public ByteList put(int i, byte e) {
        list[i] = e;
        return this;
    }

    public int getU(int i) {
        if (i > wIndex) throw new ArrayIndexOutOfBoundsException();
        return list[i] & 0xFF;
    }

    public byte get(int i) {
        if (i > wIndex) throw new ArrayIndexOutOfBoundsException();
        return list[i];
    }

    byte get0(int i) {
        return i >= list.length ? 0 : list[i];
    }

    public void wIndex(int id) {
        ensureCapacity(id);
        this.wIndex = id;
    }

    public byte[] toByteArray() {
        byte[] result = new byte[wIndex - arrayOffset()];
        System.arraycopy(list, arrayOffset(), result, 0, result.length);
        return result;
    }

    @Override
    public String toString() {
        return "ByteList{" + TextUtil.dumpBytes(list, arrayOffset(), wIndex) + '}';
    }

    public final ByteList slice(int off, int len) {
        return new Slice(list, off + arrayOffset(), len);
    }

    public void clear() {
        wIndex = rIndex = 0;
    }

    public String getString() {
        return new String(list, arrayOffset(), wIndex, StandardCharsets.UTF_8);
    }

    public final int limit() {
        return wIndex - arrayOffset();
    }

    public int arrayOffset() {
        return 0;
    }

    public final ByteList readStreamFully(InputStream stream) throws IOException {
        if (getClass() != ByteList.class)
            throw new IllegalStateException();

        int i = stream.available();
        if(i <= 0)
            i = 127;
        ensureCapacity(wIndex + i + 1);

        int real;
        do {
            real = stream.read(this.list, wIndex, list.length - wIndex);
            if (real <= 0)
                break;
            wIndex += real;
            ensureCapacity(wIndex + 1);
        } while (true);
        stream.close();
        return this;
    }

    public final int readStream(InputStream stream, int max) throws IOException {
        if (getClass() != ByteList.class)
            throw new IllegalStateException();

        ensureCapacity(wIndex + max);
        int real = stream.read(this.list, wIndex, max);
        if (real > 0)
            wIndex += real;
        return real;
    }

    /**
     * 注: 没有单独的R/W指针
     * Read: 0 - ptr-1
     * Write: ptr - Infinity
     */
    public final InputStream asInputStream() {
        return new AsInputStream();
    }

    /**
     * 注: 没有单独的R/W指针
     * Read: 0 - ptr-1
     * Write: ptr - Infinity
     */
    public final AsOutputStream asOutputStream() {
        return new AsOutputStream();
    }

    public final void writeToStream(OutputStream os) throws IOException {
        if (wIndex() > 0) {
            os.write(list, arrayOffset(), wIndex());
        }
    }

    public final ByteList setValue(byte[] array) {
        if (getClass() != ByteList.class)
            throw new IllegalStateException();
        if (array == null)
            array = EmptyArrays.BYTES;

        list = array;
        wIndex = array.length;
        rIndex = 0;
        return this;
    }

    public final byte[] getByteArray() {
        return list == null ? null : (wIndex == list.length && arrayOffset() == 0 ? list : toByteArray());
    }

    public void trim() {
        if (list != null && wIndex < list.length) {
            if (wIndex > 0) {
                byte[] newList = new byte[wIndex];
                System.arraycopy(list, 0, newList, 0, wIndex);
                list = newList;
            } else {
                list = EmptyArrays.BYTES;
            }
        }
    }

    public int lastIndexOf(byte[] bytes) {
        return lastIndexOf(list, arrayOffset(), limit(), bytes, 0, bytes.length, limit());
    }

    static int lastIndexOf(byte[] self, int slfOff, int slfLen,
                           byte[] find, int finOff, int finLen,
                           int idx) {
        /*
         * Check arguments; return immediately where possible. For
         * consistency, don't check for null str.
         */
        int rightIndex = slfLen - finLen;
        if (idx < 0) {
            return -1;
        }
        if (idx > rightIndex) {
            idx = rightIndex;
        }
        /* Empty string always matches. */
        if (finLen == 0) {
            return idx;
        }

        int strLastIndex = finOff + finLen - 1;
        byte strLastChar = find[strLastIndex];
        int min = slfOff + finLen - 1;
        int i = min + idx;

        last:
        while (true) {
            while (i >= min && self[i] != strLastChar) {
                i--;
            }
            if (i < min) {
                return -1;
            }
            int j = i - 1;
            int start = j - (finLen - 1);
            int k = strLastIndex - 1;

            while (j > start) {
                if (self[j--] != find[k--]) {
                    i--;
                    continue last;
                }
            }
            return start - slfOff + 1;
        }
    }

    public static int zig(int i) {
        return (i & Integer.MIN_VALUE) == 0 ? i << 1 : ((-i << 1) - 1);
    }

    public static long zig(long i) {
        return (i & Long.MIN_VALUE) == 0 ? i << 1 : ((-i << 1) - 1);
    }

    public static int zag(int i) {
        return (i >> 1) & ~(1 << 31) ^ -(i & 1);
    }

    public static long zag(long i) {
        return (i >> 1) & ~(1L << 63) ^ -(i & 1);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ByteList ot = (ByteList) o;
        return ArrayUtil.rangedEquals(list, arrayOffset(), wIndex(), ot.list, ot.arrayOffset(), ot.wIndex());
    }

    @Override
    public int hashCode() {
        return ArrayUtil.rangedHashCode(list, arrayOffset(), wIndex());
    }

    // region Relative bulk PUT methods from original ByteWriter
    public ByteList putBool(boolean b) {
        put((byte) (b ? 1 : 0));
        return this;
    }

    public ByteList putVarInt(int i) {
        return putVarLong(i, true);
    }

    public ByteList putVarInt(int i, boolean canBeNegative) {
        return putVarLong(i, canBeNegative);
    }

    public ByteList putVarLong(long i) {
        return putVarLong(i, true);
    }

    public ByteList putVarLong(long i, boolean canBeNegative) {
        putVarLong(this, canBeNegative ? zig(i) : i);
        return this;
    }

    private static void putVarLong(ByteList list, long i) {
        do {
            list.put((byte) ((i & 0x7F) | 0x80));
        } while ((i >>>= 7) != 0);
        list.list[list.wIndex - 1] &= 0x7F;
    }

    public ByteList putIntLE(int i) {
        return this.put((byte) i).put((byte) (i >>> 8))
                   .put((byte) (i >>> 16)).put((byte) (i >>> 24));
    }

    public ByteList putInt(int i) {
        return this.put((byte) (i >>> 24)).put((byte) (i >>> 16))
                   .put((byte) (i >>> 8)).put((byte) i);
    }

    public ByteList putLong(long l) {
        return this.put((byte) (l >>> 56)).put((byte) (l >>> 48))
                   .put((byte) (l >>> 40)).put((byte) (l >>> 32))
                   .put((byte) (l >>> 24)).put((byte) (l >>> 16))
                   .put((byte) (l >>> 8)).put((byte) l);
    }

    public ByteList putLongLE(long l) {
        return this.put((byte) l).put((byte) (l >>> 8))
                   .put((byte) (l >>> 16)).put((byte) (l >>> 24))
                   .put((byte) (l >>> 32)).put((byte) (l >>> 40))
                   .put((byte) (l >>> 48)).put((byte) (l >>> 56));
    }

    public ByteList putFloat(float f) {
        return putInt(Float.floatToIntBits(f));
    }

    public ByteList putDouble(double d) {
        return putLong(Double.doubleToLongBits(d));
    }

    public ByteList putShort(int s) {
        return put((byte) (s >>> 8)).put((byte) s);
    }

    public ByteList putShortLE(int s) {
        return put((byte) s).put((byte) (s >>> 8));
    }

    public ByteList putChars(CharSequence s) {
        for (int i = 0; i < s.length(); i++) {
            putShort(s.charAt(i));
        }
        return this;
    }

    public ByteList putIntUTF(CharSequence s) {
        if (s == null) {
            putInt(-1);
            return this;
        }
        putInt(0);

        int opos = wIndex;
        writeUTF(this, s, -1);
        int ptr = wIndex;
        wIndex = opos - 4;
        putInt(ptr - opos);
        wIndex = ptr;

        return this;
    }

    public ByteList putVarIntUTF(CharSequence s) {
        if (s == null) {
            throw new NullPointerException("s");
        }
        writeUTF(this, s, 1);
        return this;
    }

    public ByteList putUTFData(CharSequence s) {
        writeUTF(this, s, -1);
        return this;
    }

    public ByteList put(ByteBuffer buf) {
        int rem = buf.remaining();
        ensureCapacity(wIndex + rem);
        buf.get(list, wIndex, rem);
        wIndex += rem;
        return this;
    }

    public void putJavaUTF(CharSequence s) {
        writeUTF(this, s, 0);
    }

    public static ByteList encodeUTF(CharSequence s) {
        ByteList bl = new ByteList(s.length() > 1000 ? (s.length() / 3) << 1 : byteCountUTF8(s));
        writeUTF(bl, s, -1);
        return bl;
    }

    public static void writeUTF(ByteList list, CharSequence str, int type) {
        int len = str.length();

        if(type == 0 || type == 1) {
            if (type == 0 && len > 65535)
                throw new ArrayIndexOutOfBoundsException("String too long: > 65535 bytes");

            int utfLen = byteCountUTF8(str);

            if (type == 0 && utfLen > 65535) throw new ArrayIndexOutOfBoundsException("Encoded string too long: " + utfLen + " bytes");

            list.ensureCapacity(utfLen + 4);

            switch (type) {
                case 1:
                    putVarLong(list, utfLen);
                    break;
                case 0:
                    list.put((byte) (utfLen >> 8));
                    list.put((byte) utfLen);
                    break;
            }
        }

        int c;
        for (int j = 0; j < len; j++) {
            c = str.charAt(j);
            if ((c >= 0x0001) && (c <= 0x007F)) {
                list.put((byte) c);
            } else if (c > 0x07FF) {
                list.put((byte) (0xE0 | ((c >> 12) & 0x0F)));
                list.put((byte) (0x80 | ((c >> 6) & 0x3F)));
                list.put((byte) (0x80 | (c & 0x3F)));
            } else {
                list.put((byte) (0xC0 | ((c >> 6) & 0x1F)));
                list.put((byte) (0x80 | (c & 0x3F)));
            }
        }
    }

    public static int byteCountUTF8(char c) {
        if ((c >= 0x0001) && (c <= 0x007F)) {
            return 1;
        } else if (c > 0x07FF) {
            return 3;
        } else {
            return 2;
        }
    }

    public static int byteCountUTF8(CharSequence str) {
        int len = str.length();
        int utfLen = 0;

        /* use charAt instead of copying String to char array */
        for (int i = 0; i < len; i++) {
            int c = str.charAt(i);
            if ((c >= 0x0001) && (c <= 0x007F)) {
                utfLen++;
            } else if (c > 0x07FF) {
                utfLen += 3;
            } else {
                utfLen += 2;
            }
        }

        return utfLen;
    }
    // endregion
    // region Relative bulk GET methods from original ByteReader
    public int rIndex;

    public final int rIndex() {
        return rIndex - arrayOffset();
    }

    public final ByteList rIndex(int rIndex) {
        this.rIndex = rIndex + arrayOffset();
        return this;
    }

    public final boolean hasRemaining() {
        return rIndex >= wIndex;
    }

    public final int remaining() {
        return wIndex - rIndex;
    }

    // check rIndex
    private void ci(int v) {
        if (rIndex + v > wIndex) throw new ArrayIndexOutOfBoundsException();
    }

    @Override
    public final void readFully(@Nonnull byte[] b) {
        readBytes(b, 0, b.length);
    }

    @Override
    public final void readFully(@Nonnull byte[] b, int off, int len) {
        readBytes(b, off, len);
    }

    public final byte[] readBytes(int len) {
        byte[] result = new byte[len];
        readBytes(result, 0, len);
        return result;
    }

    public final void readBytes(byte[] b) {
        readBytes(b, 0, b.length);
    }

    public final void readBytes(byte[] b, int off, int len) {
        if (len < 0 || off < 0 || len > b.length - off)
            throw new ArrayIndexOutOfBoundsException();
        if (len > 0) {
            ci(len);
            System.arraycopy(list, rIndex, b, off, len);
            rIndex += len;
        }
    }

    @Override
    public final boolean readBoolean() {
        ci(1);
        return list[rIndex++] == 1;
    }

    @Override
    public final byte readByte() {
        ci(1);
        return list[rIndex++];
    }

    @Override
    public final int readUnsignedByte() {
        ci(1);
        return list[rIndex++] & 0xFF;
    }

    public final short readUByte() {
        ci(1);
        return (short) (list[rIndex++] & 0xFF);
    }

    @Override
    public final int readUnsignedShort() {
        ci(2);
        int i = rIndex;
        rIndex += 2;
        byte[] l = this.list;
        return (l[i++] & 0xFF) << 8 | (l[i] & 0xFF);
    }

    public final int readUShortLE() {
        ci(2);
        int i = rIndex;
        rIndex += 2;
        byte[] l = this.list;
        return (l[i++] & 0xFF) | (l[i] & 0xFF) << 8;
    }

    @Override
    public final short readShort() {
        return (short) readUnsignedShort();
    }

    @Override
    public char readChar() {
        return (char) readUnsignedShort();
    }

    public final int readVarInt() {
        return readVarInt(true);
    }

    public final int readVarInt(boolean mayNeg) {
        int value = 0;
        int i = 0;

        while (i <= 28) {
            int chunk = readByte();
            value |= (chunk & 0x7F) << i;
            i += 7;
            if ((chunk & 0x80) == 0) {
                return mayNeg ? zag(value) : value;
            }
        }

        throw new RuntimeException("VarInt end tag!");
    }

    public final long readVarLong() {
        return readVarLong(true);
    }

    public final long readVarLong(boolean mayNeg) {
        long value = 0;
        int i = 0;

        while (i <= 63) {
            int chunk = readByte();
            value |= (chunk & 0x7F) << i;
            i += 7;
            if ((chunk & 0x80) == 0) {
                return mayNeg ? zag(value) : value;
            }
        }

        throw new RuntimeException("VarLong end tag!");
    }

    @Override
    public final int readInt() {
        ci(4);
        int i = rIndex;
        rIndex = i + 4;
        byte[] l = this.list;
        return (l[i++] & 0xFF) << 24 | (l[i++] & 0xFF) << 16 |
                (l[i++] & 0xFF) << 8 | (l[i] & 0xFF);
    }

    public final int readIntLE() {
        ci(4);
        int i = rIndex;
        rIndex = i + 4;
        byte[] l = this.list;
        return (l[i++] & 0xFF) | (l[i++] & 0xFF) << 8 |
                (l[i++] & 0xFF) << 16 | (l[i] & 0xFF) << 24;
    }

    public final long readUInt() {
        //checkLength(4);
        int i = rIndex;
        rIndex = i + 4;
        byte[] l = this.list;
        return  (l[i++] & 0xFFL) << 24 |
                (l[i++] & 0xFFL) << 16 |
                (l[i++] & 0xFF) << 8 |
                (l[i] & 0xFF);
    }

    public final long readUIntLE() {
        ci(4);
        int i = rIndex;
        rIndex = i + 4;
        byte[] l = this.list;
        return  (l[i++] & 0xFF) |
                (l[i++] & 0xFF) << 8 |
                (l[i++] & 0xFFL) << 16 |
                (l[i] & 0xFFL) << 24;
    }

    public final long readLongLE() {
        ci(8);
        int i = rIndex;
        rIndex = i + 8;
        byte[] l = this.list;
        return  (l[i++] & 0xFFL) | (l[i++] & 0xFFL) << 8 |
                (l[i++] & 0xFFL) << 16 | (l[i++] & 0xFFL) << 24 |
                (l[i++] & 0xFFL) << 32 | (l[i++] & 0xFFL) << 40 |
                (l[i++] & 0xFFL) << 48 | (l[i] & 0xFFL) << 56;
    }

    public final long readLong() {
        ci(8);
        int i = rIndex;
        rIndex = i + 8;
        byte[] l = this.list;
        return  (l[i++] & 0xFFL) << 56 | (l[i++] & 0xFFL) << 48 |
                (l[i++] & 0xFFL) << 40 | (l[i++] & 0xFFL) << 32 |
                (l[i++] & 0xFFL) << 24 | (l[i++] & 0xFFL) << 16 |
                (l[i++] & 0xFFL) << 8 | l[i] & 0xFFL;
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
    public final int skipBytes(int i) {
        int skipped = Math.min(wIndex - rIndex, i);
        rIndex += skipped;
        return skipped;
    }

    public final String readIntUTF() {
        return readIntUTF(Integer.MAX_VALUE);
    }

    public final String readIntUTF(int max) {
        int l = readInt();
        if (l < 0)
            throw new IllegalArgumentException("Invalid string length " + l);
        if (l == 0)
            return "";
        if (l > max) {
            throw new IllegalArgumentException("Maximum allowed " + max + " got " + l);
        }
        try {
            return _utf(l);
        } catch (UTFDataFormatException e) {
            Helpers.athrow(e);
            return "";
        }
    }

    public final String readVarIntUTF() {
        int l = readVarInt(false);
        if (l < 0)
            throw new IllegalArgumentException("Invalid string length " + l);
        if (l == 0)
            return "";
        try {
            return _utf(l);
        } catch (UTFDataFormatException e) {
            Helpers.athrow(e);
            return "";
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public final String readLine() {
        int i = rIndex;
        byte[] l = list;
        while (true) {
            if (i >= wIndex)
                throw new ArrayIndexOutOfBoundsException();
            byte b = l[i++];
            if(b == '\r' || b == '\n') {
                if(b == '\r' && i < wIndex && l[i] == '\n') i++;
                break;
            }
        }
        String s = new String(l, 0, rIndex, i - rIndex);
        rIndex = i;
        return s;
    }

    public final ByteList readZeroEnd(int max) {
        int i = rIndex;
        byte[] l = list;
        while (max-- > 0) {
            if(l[i++] == 0) {
                return slice(i - rIndex);
            }
        }
        return null;
    }

    public ByteList slice(int length) {
        if (length == 0)
            return new WriteOnly();
        ByteList list = slice(rIndex, length);
        rIndex += length;
        return list;
    }

    @Nonnull
    @Override
    public final String readUTF() throws UTFDataFormatException {
        return _utf(readUnsignedShort());
    }

    public final String readUTF(int len) throws UTFDataFormatException {
        return _utf(len);
    }

    private CharList rct;
    private String _utf(int len) throws UTFDataFormatException {
        if (this.rct == null) {
            this.rct = new CharList(len);
        }
        decodeUTF0(len + rIndex, rct, this, rIndex, 0);
        rIndex += len;
        String s = rct.toString();
        rct.clear();
        return s;
    }

    public static String readUTF(ByteList list) throws UTFDataFormatException {
        CharList cl = new CharList(list.wIndex());
        decodeUTF(-1, cl, list);
        return cl.toString();
    }

    public static void decodeUTF(int max, CharList out, ByteList in) throws UTFDataFormatException {
        if (max < 0)
            max = in.wIndex();

        // The number of chars produced may be less than length
        out.ensureCapacity(max);

        decodeUTF0(max, out, in, 0, 0);
    }

    public static int decodeUTFPartialExternal(int inputOff, int max, CharList out, ByteList in) throws UTFDataFormatException {
        if (max < 0)
            max = in.wIndex();

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


    @Deprecated
    public int length() {
        return wIndex - arrayOffset();
    }
    // endregion
    // Relative bit get method from original BitReader
    public byte bitIndex;

    public final int readBit1() {
        byte bi = this.bitIndex;
        int bit = ((list[rIndex] << (bi & 0x7)) >>> 7) & 0x1;

        rIndex += (++bi) >> 3;
        this.bitIndex = (byte) (bi & 0x7);
        return bit;
    }

    public final int readBit(int numBits) {
        int d;
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
            case 9:
                d = (((((list[rIndex] & 0xFF) << 8) |
                        (get0(rIndex + 1) & 0xFF)) << bitIndex)
                        & 0xFFFF) >> 16 - numBits;
            break;
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
            case 16:
            case 17:
                d = (((((get(rIndex) & 0xFF) << 16) |
                        ((get0(rIndex + 1) & 0xFF) << 8) |
                        (get0(rIndex + 2) & 0xFF)) << bitIndex)
                        & 0xFFFFFF) >> 24 - numBits;
            break;
            default:
                throw new IllegalArgumentException("get bit count must in [1,17]");
        }
        rIndex += (bitIndex += numBits) >> 3;
        bitIndex &= 0x7;
        return d;
    }

    public void skipBits(int i) {
        bitIndex += i;
        this.rIndex += bitIndex >> 3;
        this.bitIndex = (byte) (bitIndex & 0x7);
    }
    // endregion

    /**
     * 只写，加快速度
     */
    public static final class WriteOnly extends ByteList {
        public WriteOnly() {
            super(EmptyArrays.BYTES);
        }

        @Override
        public void ensureCapacity(int required) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void wIndex(int pos) {
            this.wIndex = pos;
        }

        @Override
        public final ByteList put(byte e) {
            wIndex++;
            return this;
        }

        public ByteList put(byte[] b, int off, int len) {
            wIndex += len;
            return this;
        }

        @Override
        public void trim() {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * 只读
     */
    public static final class Slice extends ByteList {
        private final int offset;
        private final int length;

        public Slice(byte[] array, int start, int length) {
            super(array);
            this.wIndex = start + length;
            this.length = start + length;
            this.offset = start;
        }

        @Override
        public void ensureCapacity(int required) {
            if (required > length) {
                throw new ArrayIndexOutOfBoundsException("Required " + required + " Current " + length);
            }
        }

        @Override
        public int arrayOffset() {
            return offset;
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException("Readonly");
        }

        @Override
        public String getString() {
            return new String(list, offset, length - offset, StandardCharsets.UTF_8);
        }

        @Override
        public int wIndex() {
            return wIndex - offset;
        }

        @Override
        public final ByteList put(byte e) {
            throw new UnsupportedOperationException();
        }

        @Override
        public final ByteList put(int i, byte e) {
            return super.put(i + offset, e);
        }

        @Override
        public byte get(int i) {
            return super.get(i + offset);
        }

        @Override
        public int getU(int i) {
            return super.getU(i + offset);
        }

        @Override
        public ByteList put(byte[] b, int off, int len) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void trim() {
            throw new UnsupportedOperationException();
        }

        @Override
        public byte[] toByteArray() {
            byte[] result = new byte[length - offset];
            System.arraycopy(list, offset, result, 0, result.length);
            return result;
        }
    }

    private final class AsInputStream extends InputStream {
        private int pos;

        AsInputStream() {}

        @Override
        public int read() {
            return pos >= wIndex ? -1 : (list[pos++] & 0xFF);
        }

        @Override
        public int read(@Nonnull byte[] arr, int off, int len) {
            if(len == 0)
                return 0;

            int r = Math.min(wIndex - pos, len);
            if (r <= 0)
                return -1;

            System.arraycopy(list, pos, arr, off, r);
            pos += r;
            return r;
        }

        @Override
        public long skip(long len) {
            long skipped = Math.min(available(), len);
            pos += skipped;
            return skipped;
        }

        @Override
        public int available() {
            return Math.max(0, wIndex - pos);
        }
    }

    public final class AsOutputStream extends OutputStream implements DataOutput {
        AsOutputStream() {}

        @Override
        public void write(int b) {
            put((byte) b);
        }

        @Override
        public void write(@Nonnull byte[] b) {
            put(b, 0, b.length);
        }

        @Override
        public void write(@Nonnull byte[] b, int off, int len) {
            put(b, off, len);
        }

        @Override
        public void writeBoolean(boolean v) {
            put((byte) (v ? 1 : 0));
        }

        @Override
        public void writeByte(int v) {
            put((byte) v);
        }

        @Override
        public void writeShort(int s) {
            put((byte) (s >>> 8)).put((byte) s);
        }

        @Override
        public void writeChar(int c) {
            writeShort(c);
        }

        @Override
        public void writeInt(int i) {
            putInt(i);
        }

        @Override
        public void writeLong(long l) {
            putLong(l);
        }

        @Override
        public void writeFloat(float v) {
            putInt(Float.floatToIntBits(v));
        }

        @Override
        public void writeDouble(double v) {
            putLong(Double.doubleToLongBits(v));
        }

        @Override
        public void writeBytes(@Nonnull String s) {
            int len = s.length();
            for (int i = 0; i < len; i++) {
                put((byte) s.charAt(i));
            }
        }

        @Override
        public void writeChars(@Nonnull String s) {
            putChars(s);
        }

        @Override
        public void writeUTF(@Nonnull String str) throws UTFDataFormatException {
            try {
                ByteList.writeUTF(ByteList.this, str, 0);
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new UTFDataFormatException(e.getMessage());
            }
        }
    }
}