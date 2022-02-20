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

import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.TextUtil;

import javax.annotation.Nonnull;
import java.io.*;
import java.nio.ByteBuffer;

/**
 * @author Roj234
 * @since 2021/5/29 20:45
 */
public class ByteList extends OutputStream implements DataInput, DataOutput, CharSequence {
    public byte[] list;
    int wIndex;
    public int rIndex;

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

    public void wIndex(int id) {
        ensureCapacity(id);
        this.wIndex = id;
    }

    public void ensureCapacity(int required) {
        if (required > list.length) {
            byte[] newList = new byte[list.length == 0 ? Math.max(required, 256) : ((required * 3) >>> 1) + 1];

            if (wIndex > 0)
                System.arraycopy(list, 0, newList, 0, wIndex);
            list = newList;
        }
    }

    public byte[] toByteArray() {
        byte[] b = new byte[wIndex];
        System.arraycopy(list, 0, b, 0, b.length);
        return b;
    }

    public void clear() {
        wIndex = rIndex = 0;
    }

    public int arrayOffset() {
        return 0;
    }

    /**
     * 注: 读取指针与rIndex独立，历史原因
     */
    public final InputStream asInputStream() {
        return new AsInputStream();
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

    public final int readStream(InputStream in, int max) throws IOException {
        if (getClass() != ByteList.class)
            throw new IllegalStateException();

        ensureCapacity(wIndex + max);
        int real = in.read(list, wIndex, max);
        if (real > 0)
            wIndex += real;
        return real;
    }

    public final void writeToStream(OutputStream os) throws IOException {
        if (wIndex() > 0) {
            os.write(list, arrayOffset(), wIndex());
        }
    }

    public final ByteList setArray(byte[] array) {
        if (getClass() != ByteList.class)
            throw new IllegalStateException();
        if (array == null)
            array = EmptyArrays.BYTES;

        list = array;
        wIndex = array.length;
        rIndex = 0;
        return this;
    }

    public void trim() {
        if (wIndex < list.length) {
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

    public final int limit() {
        return wIndex - arrayOffset();
    }
    public final boolean hasRemaining() {
        return remaining() > 0;
    }
    public final int remaining() {
        return wIndex - rIndex - arrayOffset();
    }

    private int putIndex(int i) {
        int wi = wIndex;
        ensureCapacity(wi + i);
        wi = wIndex;
        wIndex = wi + i;
        return wi;
    }
    private int readIndex(int i) {
        int ri = rIndex;
        if (ri + i + arrayOffset() > wIndex) throw new ArrayIndexOutOfBoundsException();
        rIndex = ri + i;
        return ri;
    }
    private int ci(int i) {
        if ((i += arrayOffset()) > wIndex) throw new ArrayIndexOutOfBoundsException();
        return i;
    }

    // region DataOutput (non-chainable) PUT methods
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
            ByteList.writeUTF(this, str, 0);
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new UTFDataFormatException(e.getMessage());
        }
    }
    // endregion
    // region Relative and Absolute bulk PUT methods from original ByteWriter

    public final ByteList putBool(boolean b) {
        int i = putIndex(1);
        list[i] = (byte) (b ? 1 : 0);
        return this;
    }

    public final ByteList put(byte e) {
        int i = putIndex(1);
        list[i] = e;
        return this;
    }
    public final ByteList put(int i, byte e) {
        ensureCapacity(i + 1);
        list[i] = e;
        return this;
    }

    public final ByteList put(byte[] b) {
        return put(b, 0, b.length);
    }

    public final ByteList put(byte[] b, int off, int len) {
        if (len < 0 || off < 0 || len > b.length - off)
            throw new ArrayIndexOutOfBoundsException();
        if (len > 0) {
            int off1 = putIndex(len);
            System.arraycopy(b, off, list, off1, len);
        }
        return this;
    }

    public ByteList put(ByteList b) {
        if (b.getClass() == Streamed.class) {
            int i = ((Streamed) b).fakeWriteIndex;
            if (i > 0) throw new IllegalArgumentException("Unable to put STREAMED byte list");
        } else
        put(b.list, b.arrayOffset(), b.wIndex - b.arrayOffset());
        return this;
    }

    public static int zig(int i) {
        return (i & Integer.MIN_VALUE) == 0 ? i << 1 : ((-i << 1) - 1);
    }
    public static long zig(long i) {
        return (i & Long.MIN_VALUE) == 0 ? i << 1 : ((-i << 1) - 1);
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

    public ByteList putVLUI(int i) {
        long v = i & 0xFFFFFFFFL;
        byte[] l;
        int wi = wIndex;

        if (v >= 0x200000) {
            if (v >= 0x10000000) { // 5 bytes, 29-32 bits
                ensureCapacity(wi + 5);
                wIndex = wi + 5;
                l = list;
                l[wi++] = ((byte) 0b00001000);
                l[wi++] = ((byte) (v >>> 24));
            } else { // 4 bytes, 22-28 bits
                ensureCapacity(wi + 4);
                wIndex = wi + 4;
                l = list;
                l[wi++] = ((byte) (0b00010000 | ((v >> 24) & 0xF)));
            }
            l[wi++] = ((byte) (v >>> 16));
            l[wi++] = ((byte) (v >>> 8));
            l[wi  ] = ((byte) v);
        } else {
            if (v >= 0x4000) { // 3 bytes, 15-21 bits
                ensureCapacity(wi + 3);
                wIndex = wi + 3;
                l = list;
                l[wi++] = ((byte) (0b00100000 | ((v >>> 16) & 0x1F)));
                l[wi++] = ((byte) (v >>> 8));
                l[wi  ] = ((byte) v);
            } else if (v >= 0x80) { // 2 bytes, 8-14bits
                ensureCapacity(wi + 2);
                wIndex = wi + 2;
                l = list;
                l[wi++] = ((byte) (0b01000000 | ((v >>> 8) & 0x3F)));
                l[wi  ] = ((byte) v);
            } else { // 1byte, 0-7 bits
                return put((byte) (0b10000000 | v));
            }
        }
        return this;
    }

    public ByteList putIntLE(int i) {
        return putIntLE(putIndex(4), i);
    }
    public ByteList putIntLE(int wi, int i) {
        byte[] list = this.list;
        list[wi++] = (byte) i;
        list[wi++] = (byte) (i >>> 8);
        list[wi++] = (byte) (i >>> 16);
        list[wi  ] = (byte) (i >>> 24);
        return this;
    }

    public ByteList putInt(int i) {
        return putInt(putIndex(4), i);
    }
    public ByteList putInt(int wi, int i) {
        byte[] list = this.list;
        list[wi++] = (byte) (i >>> 24);
        list[wi++] = (byte) (i >>> 16);
        list[wi++] = (byte) (i >>> 8);
        list[wi  ] = (byte) i;
        return this;
    }

    public ByteList putLongLE(long l) {
        return putLongLE(putIndex(8), l);
    }
    public ByteList putLongLE(int wi, long l) {
        byte[] list = this.list;
        list[wi++] = (byte) l;
        list[wi++] = (byte) (l >>> 8);
        list[wi++] = (byte) (l >>> 16);
        list[wi++] = (byte) (l >>> 24);
        list[wi++] = (byte) (l >>> 32);
        list[wi++] = (byte) (l >>> 40);
        list[wi++] = (byte) (l >>> 48);
        list[wi  ] = (byte) (l >>> 56);
        return this;
    }

    public ByteList putLong(long l) {
        return putLong(putIndex(8), l);
    }
    public ByteList putLong(int wi, long l) {
        byte[] list = this.list;
        list[wi++] = (byte) (l >>> 56);
        list[wi++] = (byte) (l >>> 48);
        list[wi++] = (byte) (l >>> 40);
        list[wi++] = (byte) (l >>> 32);
        list[wi++] = (byte) (l >>> 24);
        list[wi++] = (byte) (l >>> 16);
        list[wi++] = (byte) (l >>> 8);
        list[wi  ] = (byte) l;
        return this;
    }

    public ByteList putFloat(float f) {
        return putFloat(putIndex(4), f);
    }
    public ByteList putFloat(int wi, float f) {
        return putInt(wi, Float.floatToIntBits(f));
    }

    public ByteList putDouble(double d) {
        return putDouble(putIndex(8), d);
    }
    public ByteList putDouble(int wi, double d) {
        return putLong(wi, Double.doubleToLongBits(d));
    }

    public ByteList putShort(int s) {
        return putShort(putIndex(2), s);
    }
    public ByteList putShort(int wi, int s) {
        byte[] list = this.list;
        list[wi++] = (byte) (s >>> 8);
        list[wi  ] = (byte) s;
        return this;
    }

    public ByteList putShortLE(int s) {
        return putShortLE(putIndex(2), s);
    }
    public ByteList putShortLE(int wi, int s) {
        byte[] list = this.list;
        list[wi++] = (byte) s;
        list[wi  ] = (byte) (s >>> 8);
        return this;
    }

    public ByteList putMedium(int m) {
        return putShort(putIndex(3), m);
    }
    public ByteList putMedium(int wi, int m) {
        byte[] list = this.list;
        list[wi++] = (byte) (m >>> 16);
        list[wi++] = (byte) (m >>> 8);
        list[wi  ] = (byte) m;
        return this;
    }

    public ByteList putChars(CharSequence s) {
        return putChars(putIndex(s.length() << 1), s);
    }
    public ByteList putChars(int wi, CharSequence s) {
        byte[] list = this.list;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            list[wi++] = (byte) (c >>> 8);
            list[wi++] = (byte) c;
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

    public ByteList putAscii(CharSequence s) {
        return putAscii(putIndex(s.length()), s);
    }
    @SuppressWarnings("deprecation")
    public ByteList putAscii(int wi, CharSequence s) {
        if (s.getClass() == String.class) {
            s.toString().getBytes(0, s.length(), list, wi);
        } else {
            byte[] list = this.list;
            for (int i = 0; i < s.length(); i++) {
                list[wi++] = (byte) s.charAt(i);
            }
        }
        return this;
    }

    public ByteList put(ByteBuffer buf) {
        return put(putIndex(buf.remaining()), buf);
    }
    public ByteList put(int wi, ByteBuffer buf) {
        int rem = buf.remaining();
        buf.get(list, wi, rem);
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

            if (type == 1) {
                int utfLen = byteCountUTF8(str);
                list.ensureCapacity(utfLen + 4);
                putVarLong(list, utfLen);
            } else {
                list.putShort(0);
            }
        }
        int pos = list.wIndex;

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

        len = list.wIndex - pos;
        if (type == 0) {
            if (len > 65535) {
                list.wIndex = pos;
                throw new ArrayIndexOutOfBoundsException("Encoded string too long: " + len + " bytes");
            }
            list.putShort(pos - 2, len);
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

    @Override
    public final void readFully(@Nonnull byte[] b) {
        read(b, 0, b.length);
    }

    @Override
    public final void readFully(@Nonnull byte[] b, int off, int len) {
        read(b, off, len);
    }

    public final byte[] readBytes(int len) {
        byte[] result = new byte[len];
        read(result, 0, len);
        return result;
    }

    public final void read(byte[] b) {
        read(b, 0, b.length);
    }

    public final void read(byte[] b, int off, int len) {
        if (len < 0 || off < 0 || len > b.length - off)
            throw new ArrayIndexOutOfBoundsException();
        if (len > 0) {
            System.arraycopy(list, readIndex(len) + arrayOffset(), b, off, len);
        }
    }

    public final boolean readBoolean(int i) {
        return list[ci(i)] == 1;
    }
    @Override
    public final boolean readBoolean() {
        return list[readIndex(1) + arrayOffset()] == 1;
    }

    public final byte get(int i) {
        return list[ci(i)];
    }
    @Override
    public final byte readByte() {
        return list[readIndex(1) + arrayOffset()];
    }

    public final int getU(int i) {
        return list[ci(i)] & 0xFF;
    }
    @Override
    public final int readUnsignedByte() {
        return list[readIndex(1) + arrayOffset()] & 0xFF;
    }

    public final int readUnsignedShort(int i) {
        i = ci(i);
        byte[] l = this.list;
        return (l[i++] & 0xFF) << 8 | (l[i] & 0xFF);
    }
    @Override
    public final int readUnsignedShort() {
        return readUnsignedShort(readIndex(2));
    }

    public final int readUShortLE(int i) {
        i = ci(i);
        byte[] l = this.list;
        return (l[i++] & 0xFF) | (l[i] & 0xFF) << 8;
    }
    public final int readUShortLE() {
        return readUShortLE(readIndex(2));
    }

    @Override
    public final short readShort() {
        return (short) readUnsignedShort();
    }

    @Override
    public final char readChar() {
        return (char) readUnsignedShort();
    }

    public final int readMedium() {
        return readInt(readIndex(3));
    }
    public final int readMedium(int i) {
        i = ci(i);
        byte[] l = this.list;
        return (l[i++] & 0xFF) << 16 | (l[i++] & 0xFF) << 8 | (l[i] & 0xFF);
    }

    public static int zag(int i) {
        return (i >> 1) & ~(1 << 31) ^ -(i & 1);
    }
    public static long zag(long i) {
        return (i >> 1) & ~(1L << 63) ^ -(i & 1);
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

    // Variable length unsigned int
    public final int readVLUI() {
        int v = readUnsignedByte();

        int len = 7;
        while (len >= 3) {
            if ((v & (1 << len)) != 0) {
                // strip out marker
                v ^= 1 << len;
                break;
            }
            len--;
        }
        if (len == 2) throw new RuntimeException("VLUI width");
        len = 8 - len;
        while (--len > 0) {
            v = (v << 8) | readUnsignedByte();
        }

        return v;
    }

    @Override
    public final int readInt() {
        return readInt(readIndex(4));
    }
    public final int readInt(int i) {
        i = ci(i);
        byte[] l = this.list;
        return (l[i++] & 0xFF) << 24 | (l[i++] & 0xFF) << 16 |
                (l[i++] & 0xFF) << 8 | (l[i] & 0xFF);
    }

    public final int readIntLE() {
        return readIntLE(readIndex(4));
    }
    public final int readIntLE(int i) {
        i = ci(i);
        byte[] l = this.list;
        return (l[i++] & 0xFF) | (l[i++] & 0xFF) << 8 |
                (l[i++] & 0xFF) << 16 | (l[i] & 0xFF) << 24;
    }

    public final long readUInt() {
        return readInt() & 0xFFFFFFFFL;
    }
    public final long readUIntLE() {
        return readIntLE() & 0xFFFFFFFFL;
    }

    public final long readLongLE() {
        return readLongLE(readIndex(8));
    }
    public final long readLongLE(int i) {
        i = ci(i);
        byte[] l = this.list;
        return  (l[i++] & 0xFFL) | (l[i++] & 0xFFL) << 8 |
                (l[i++] & 0xFFL) << 16 | (l[i++] & 0xFFL) << 24 |
                (l[i++] & 0xFFL) << 32 | (l[i++] & 0xFFL) << 40 |
                (l[i++] & 0xFFL) << 48 | (l[i] & 0xFFL) << 56;
    }

    @Override
    public final long readLong() {
        return readLong(readIndex(8));
    }
    public final long readLong(int i) {
        i = ci(i);
        byte[] l = this.list;
        return  (l[i++] & 0xFFL) << 56 | (l[i++] & 0xFFL) << 48 |
                (l[i++] & 0xFFL) << 40 | (l[i++] & 0xFFL) << 32 |
                (l[i++] & 0xFFL) << 24 | (l[i++] & 0xFFL) << 16 |
                (l[i++] & 0xFFL) << 8 | l[i] & 0xFFL;
    }

    @Override
    public final float readFloat() {
        return Float.intBitsToFloat(readInt(readIndex(4)));
    }
    public final float readFloat(int i) {
        return Float.intBitsToFloat(readInt(i));
    }

    @Override
    public final double readDouble() {
        return Double.longBitsToDouble(readLong(readIndex(8)));
    }
    public final double readDouble(int i) {
        return Double.longBitsToDouble(readLong(i));
    }

    @Override
    public final int skipBytes(int i) {
        int skipped = Math.min(wIndex - rIndex - arrayOffset(), i);
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
        int i = rIndex + arrayOffset();
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
        rIndex = i - arrayOffset();
        return s;
    }

    public final String readAscii(int len) {
        return readAscii(readIndex(len), len);
    }
    @SuppressWarnings("deprecation")
    public final String readAscii(int i, int len) {
        return new String(list, 0, ci(i), len);
    }

    public final ByteList readZeroEnd(int max) {
        int i = rIndex + arrayOffset();
        byte[] l = list;
        while (max-- > 0) {
            if(l[i++] == 0) {
                return slice(i - rIndex - arrayOffset());
            }
        }
        return null;
    }

    public ByteList slice(int length) {
        if (length == 0) return new ByteList();
        ByteList list = slice(rIndex, length);
        rIndex += length;
        return list;
    }

    public final ByteList slice(int off, int len) {
        return new Slice(list, off + arrayOffset(), len);
    }

    @Nonnull
    @Override
    public final String readUTF() throws UTFDataFormatException {
        return _utf(readUnsignedShort());
    }

    public final String readUTF(int len) throws UTFDataFormatException {
        return _utf(len);
    }

    private String _utf(int len) throws UTFDataFormatException {
        CharList rct = IOUtil.getSharedCharBuf();
        rct.ensureCapacity(len);
        decodeUTF0(len + rIndex, rct, this, rIndex, 0);
        rIndex += len;
        return rct.toString();
    }

    public static String readUTF(ByteList list) throws UTFDataFormatException {
        CharList cl = IOUtil.getSharedCharBuf();
        decodeUTF0(list.wIndex(), cl, list, 0, 0);
        return cl.toString();
    }

    public static void decodeUTF(int max, CharList out, ByteList in) throws UTFDataFormatException {
        if (max <= 0) max = in.wIndex();
        out.ensureCapacity(max);

        decodeUTF0(max, out, in, 0, 0);
    }

    public static int decodeUTFPartial(int off, int max, CharList out, ByteList in) throws UTFDataFormatException {
        if (max <= 0) max = in.wIndex();

        return decodeUTF0(max, out, in, off,  FLAG_PARTIAL) - off;
    }

    public static final int FLAG_PARTIAL = 1;
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
                        if ((flag & 1) != 0) {
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
                        if ((flag & 1) != 0) {
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
                default:
                    /* 10xx xxxx,  1111 xxxx */
                    throw new UTFDataFormatException("malformed input around byte " + i);
            }
        }

        return i;
    }
    // endregion
    // region Relative bit get method from original BitReader
    public byte bitIndex;

    public final int readBit1() {
        byte bi = this.bitIndex;
        int bit = ((list[rIndex] << (bi & 0x7)) >>> 7) & 0x1;

        rIndex += (++bi) >> 3;
        this.bitIndex = (byte) (bi & 0x7);
        return bit;
    }

    public byte get0(int i) {
        return i >= wIndex ? 0 : list[i];
    }

    public final int readBit(int numBits) {
        int d;
        int ri = rIndex + arrayOffset();
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
                d = (((((list[ri++] & 0xFF) << 8) |
                        (get0(ri) & 0xFF)) << bitIndex)
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
                d = (((((get(ri++) & 0xFF) << 16) |
                        ((get0(ri++) & 0xFF) << 8) |
                        (get0(ri) & 0xFF)) << bitIndex)
                        & 0xFFFFFF) >> 24 - numBits;
            break;
            default:
                throw new IllegalArgumentException("get bit count must in [1,17]");
        }
        this.rIndex += (bitIndex += numBits) >> 3;
        bitIndex &= 0x7;
        return d;
    }

    public void skipBits(int i) {
        bitIndex += i;
        this.rIndex += bitIndex >> 3;
        this.bitIndex = (byte) (bitIndex & 0x7);
    }

    public void endBitRead() {
        if (bitIndex != 0) {
            bitIndex = 0;
            rIndex++;
        }
    }
    // endregion
    // region Ascii Sequence
    @Override
    public int length() {
        return wIndex - arrayOffset();
    }

    @Override
    public char charAt(int i) {
        return (char) list[i + arrayOffset()];
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return new Slice(list, start, end - start);
    }
    // endregion

    @Override
    public String toString() {
        return "ByteList{" + TextUtil.dumpBytes(list, arrayOffset(), wIndex - arrayOffset()) + '}';
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

    public boolean isDummy() {
        return false;
    }

    public static final class Streamed extends ByteList {
        OutputStream out;
        int fakeWriteIndex;

        public Streamed() {
            super(12);
        }

        public Streamed(OutputStream out, int bufferCapacity) {
            super(bufferCapacity);
            this.out = out;
        }

        public void setOut(OutputStream out) {
            this.out = out;
        }

        @Override
        public void ensureCapacity(int required) {
            if (required >= list.length) {
                required -= wIndex;
                flush();
            }
            if (required >= list.length) {
                list = new byte[required];
            }
        }

        @Override
        public boolean isDummy() {
            return out == null;
        }

        public void flush() {
            if (wIndex > 0) {
                if (out != null) {
                    try {
                        out.write(list, 0, wIndex);
                    } catch (IOException e) {
                        Helpers.athrow(e);
                    }
                }
                fakeWriteIndex += wIndex;
                wIndex = 0;
            }
        }

        @Override
        public void close() {
            flush();
        }

        @Override
        public int wIndex() {
            flush();
            return fakeWriteIndex;
        }

        @Override
        public void wIndex(int pos) {
            flush();
            this.fakeWriteIndex = pos;
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
        private final int off, len;

        public Slice(byte[] array, int start, int len) {
            super(array);
            this.wIndex = start + len;
            this.len = start + len;
            this.off = start;
        }

        @Override
        public void ensureCapacity(int required) {
            if (required > len) {
                throw new UnsupportedOperationException();
            }
        }

        @Override
        public int arrayOffset() {
            return off;
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int wIndex() {
            return wIndex - off;
        }

        @Override
        public void wIndex(int i) {
            if (i > len) {
                throw new UnsupportedOperationException();
            }
            this.wIndex = i + off;
        }

        @Override
        public void trim() {
            throw new UnsupportedOperationException();
        }

        @Override
        public byte[] toByteArray() {
            byte[] b = new byte[len - off];
            System.arraycopy(list, off, b, 0, b.length);
            return b;
        }
    }

    private final class AsInputStream extends InputStream {
        private int pos;

        AsInputStream() {}

        @Override
        public int read() {
            if (pos == wIndex) return -1;
            return list[pos++] & 0xFF;
        }

        @Override
        public int read(@Nonnull byte[] arr, int off, int len) {
            if(len == 0) return 0;

            int r = Math.min(wIndex - pos, len);
            if (r <= 0) return -1;

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
}