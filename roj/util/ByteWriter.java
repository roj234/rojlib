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

import javax.annotation.Nonnull;
import java.io.DataOutput;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UTFDataFormatException;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/30 23:4
 */
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
        return new AsDataOutput(list);
    }

    public ByteWriter writeBoolean(boolean b) {
        list.add((byte) (b ? 1 : 0));
        return this;
    }

    public ByteWriter writeByte(byte b) {
        list.add(b);
        return this;
    }

    public ByteWriter writeVarInt(int i) {
        return writeVarInt(i, true);
    }

    public ByteWriter writeVarInt(int i, boolean canBeNegative) {
        if (canBeNegative) {
            i = zig(i);
        }
        writeVarInt(list, i);
        return this;
    }

    private static void writeVarInt(ByteList list, int i) {
        do {
            list.add((byte) ((i & 0x7F) | 0x80));
        } while ((i >>>= 7) != 0);
        list.list[list.pointer - 1] &= 0x7F;
    }

    public static int zig(int i) {
        return (i & Integer.MIN_VALUE) == 0 ? i << 1 : ((-i << 1) - 1);
    }

    public ByteWriter writeString(CharSequence s) {
        if (s == null) {
            writeInt(-1);
            return this;
        }
        writeInt(0);

        final ByteList list = this.list;
        int opos = list.pointer;
        writeUTF(list, s, -1);
        int ptr = list.pointer;
        list.pointer = opos - 4;
        writeInt(ptr - opos);
        list.pointer = ptr;

        return this;
    }

    public ByteWriter writeVString(CharSequence s) {
        if (s == null) {
            throw new NullPointerException("s");
        }
        writeUTF(list, s, 1);
        return this;
    }

    public ByteWriter writeVarLong(long i) {
        return writeVarLong(i, true);
    }

    public ByteWriter writeVarLong(long i, boolean canBeNegative) {
        if (canBeNegative) {
            i = zig(i);
        }
        do {
            list.add((byte) ((i & 0x7F) | 0x80));
        } while ((i >>>= 7) != 0);
        list.list[list.pointer - 1] &= 0x7F;
        return this;
    }

    public static long zig(long i) {
        return (i & Long.MIN_VALUE) == 0 ? i << 1 : ((-i << 1) - 1);
    }

    public ByteWriter writeBytes(byte[] bs) {
        list.addAll(bs);
        return this;
    }

    public ByteWriter writeBytes(ByteList list) {
        this.list.addAll(list, list.offset(), list.limit());
        return this;
    }

    public ByteWriter writeBytes(ByteWriter writer) {
        return writeBytes(writer.list);
    }

    public ByteWriter writeIntR(int i) {
        final ByteList list = this.list;
        list.add((byte) (i));
        list.add((byte) (i >>> 8));
        list.add((byte) (i >>> 16));
        list.add((byte) (i >>> 24));
        return this;
    }

    public ByteWriter writeInt(int i) {
        final ByteList list = this.list;
        list.add((byte) (i >>> 24));
        list.add((byte) (i >>> 16));
        list.add((byte) (i >>> 8));
        list.add((byte) (i));
        return this;
    }

    public ByteWriter writeLong(long l) {
        final ByteList list = this.list;
        list.add((byte) (l >>> 56));
        list.add((byte) (l >>> 48));
        list.add((byte) (l >>> 40));
        list.add((byte) (l >>> 32));
        list.add((byte) (l >>> 24));
        list.add((byte) (l >>> 16));
        list.add((byte) (l >>> 8));
        list.add((byte) (l));
        return this;
    }

    public ByteWriter writeLongR(long l) {
        final ByteList list = this.list;
        list.add((byte) (l));
        list.add((byte) (l >>> 8));
        list.add((byte) (l >>> 16));
        list.add((byte) (l >>> 24));
        list.add((byte) (l >>> 32));
        list.add((byte) (l >>> 40));
        list.add((byte) (l >>> 48));
        list.add((byte) (l >>> 56));
        return this;
    }

    public ByteWriter writeFloat(float f) {
        writeInt(Float.floatToIntBits(f));
        return this;
    }

    public ByteWriter writeDouble(double d) {
        writeLong(Double.doubleToLongBits(d));
        return this;
    }

    public ByteWriter writeChars(CharSequence s) {
        for (int i = 0; i < s.length(); i++) {
            writeShort(s.charAt(i));
        }
        return this;
    }

    public ByteWriter writeShort(int s) {
        list.add((byte) (s >>> 8));
        list.add((byte) (s));
        return this;
    }

    public ByteWriter writeShortR(int s) {
        list.add((byte) (s));
        list.add((byte) (s >>> 8));
        return this;
    }

    public byte[] toByteArray() {
        return list.toByteArray();
    }

    public void writeToStream(OutputStream os) throws IOException {
        list.writeToStream(os);
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

            list.ensureCapacity((utfLen * 2) + 2);

            switch (type) {
                case 1:
                    writeVarInt(list, utfLen);
                    break;
                case 0:
                    list.add((byte) (utfLen >> 8));
                    list.add((byte) utfLen);
                    break;
            }
        }

        int c;
        for (int j = 0; j < len; j++) {
            c = str.charAt(j);
            if ((c >= 0x0001) && (c <= 0x007F)) {
                list.add((byte) c);
            } else if (c > 0x07FF) {
                list.add((byte) (0xE0 | ((c >> 12) & 0x0F)));
                list.add((byte) (0x80 | ((c >> 6) & 0x3F)));
                list.add((byte) (0x80 | (c & 0x3F)));
            } else {
                list.add((byte) (0xC0 | ((c >> 6) & 0x1F)));
                list.add((byte) (0x80 | (c & 0x3F)));
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

    public void writeBytes(byte[] array, int off, int len) {
        list.addAll(array, off, len);
    }

    public ByteWriter writeAllUTF(CharSequence s) {
        writeUTF(list, s, 2);
        return this;
    }

    public void writeJavaUTF(CharSequence s) {
        writeUTF(list, s, 0);
    }

    private static class AsDataOutput implements DataOutput {
        private final ByteList list;

        public AsDataOutput(ByteList list) {
            this.list = list;
        }

        @Override
        public void write(int b) {
            list.add((byte) b);
        }

        @Override
        public void write(@Nonnull byte[] b) {
            list.addAll(b);
        }

        @Override
        public void write(@Nonnull byte[] b, int off, int len) {
            list.addAll(b, off, len);
        }

        @Override
        public void writeBoolean(boolean v) {
            list.add((byte) (v ? 1 : 0));
        }

        @Override
        public void writeByte(int v) {
            list.add((byte) v);
        }

        @Override
        public void writeShort(int s) {
            list.add((byte) (s >>> 8));
            list.add((byte) (s));
        }

        @Override
        public void writeChar(int c) {
            writeShort(c);
        }

        @Override
        public void writeInt(int i) {
            final ByteList list = this.list;
            list.add((byte) (i >>> 24));
            list.add((byte) (i >>> 16));
            list.add((byte) (i >>> 8));
            list.add((byte) (i));
        }

        @Override
        public void writeLong(long l) {
            final ByteList list = this.list;
            list.add((byte) (l >>> 56));
            list.add((byte) (l >>> 48));
            list.add((byte) (l >>> 40));
            list.add((byte) (l >>> 32));
            list.add((byte) (l >>> 24));
            list.add((byte) (l >>> 16));
            list.add((byte) (l >>> 8));
            list.add((byte) (l));
        }

        @Override
        public void writeFloat(float v) {
            writeInt(Float.floatToIntBits(v));
        }

        @Override
        public void writeDouble(double v) {
            writeLong(Double.doubleToLongBits(v));
        }

        @Override
        public void writeBytes(@Nonnull String s) {
            int len = s.length();
            for (int i = 0; i < len; i++) {
                write((byte) s.charAt(i));
            }
        }

        @Override
        public void writeChars(@Nonnull String s) {
            int len = s.length();
            for (int i = 0; i < len; i++) {
                writeShort(s.charAt(i));
            }
        }

        @Override
        public void writeUTF(@Nonnull String str) throws UTFDataFormatException {
            try {
                ByteWriter.writeUTF(list, str, 0);
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new UTFDataFormatException(e.getMessage());
            }
        }
    }
}