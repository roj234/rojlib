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

import roj.text.TextUtil;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * @author Roj234
 * @version 0.1
 * @since 2021/5/29 20:45
 */
public class ByteList {
    public byte[] list;
    protected int pointer;

    public ByteList() {
        this.list = EmptyArrays.BYTES;
    }

    public ByteList(int len) {
        list = new byte[len];
    }

    public ByteList(byte[] array) {
        list = array;
        pointer = array.length;
    }

    public int pos() {
        return pointer;
    }

    public void add(byte e) {
        ensureCapacity(this.pointer + 1);
        list[this.pointer++] = e;
    }

    public void ensureCapacity(int required) {
        if (required > list.length) {
            byte[] newList = new byte[list.length == 0 ? Math.max(required, 256) : ((required * 3) >>> 1) + 1];

            if (pointer > 0)
                System.arraycopy(list, 0, newList, 0, pointer);
            list = newList;
        }
    }

    public void addAll(byte[] array) {
        addAll(array, 0, array.length);
    }

    public void addAll(byte[] array, int start) {
        addAll(array, start, array.length - start);
    }

    public void addAll(byte[] array, int start, int length) {
        if (length < 0 || start < 0 || length > array.length - start)
            throw new ArrayIndexOutOfBoundsException();
        if (length > 0) {
            if (start >= array.length)
                throw new ArrayIndexOutOfBoundsException();
            ensureCapacity(pointer + length);
            System.arraycopy(array, start, list, pointer, length);
            pointer += length;
        }
    }

    public final void addAll(ByteList array, int start, int length) {
        if (array.getClass() == ByteList.EmptyByteList.class)
            return;
        addAll(array.list, start + offset(), length);
    }

    public void set(int index, byte e) {
        list[index] = e;
    }

    public int getU(int index) {
        if (index > pointer)
            throw new ArrayIndexOutOfBoundsException("Required " + index + " Current " + pointer);
        return list[index] & 0xFF;
    }

    public byte get(int index) {
        if (index > pointer)
            throw new ArrayIndexOutOfBoundsException("Required " + index + " Current " + pointer);
        return list[index]; // 2
    }

    byte get0(int index) {
        return index >= list.length ? 0 : list[index];
    }

    public void pos(int id) {
        ensureCapacity(id);
        this.pointer = id;
    }

    public byte[] toByteArray() {
        byte[] result = new byte[pointer - offset()];
        System.arraycopy(list, offset(), result, 0, result.length);
        return result;
    }

    @Override
    public String toString() {
        return "ByteList{" + TextUtil.dumpBytes(list, offset(), pointer) + '}';
    }

    public final ByteList subList(int start, int length) {
        return new ReadOnlySubList(this.list, start + offset(), length);
    }

    public void clear() {
        pointer = 0;
    }

    public String getString() {
        return new String(list, offset(), pointer, StandardCharsets.UTF_8);
    }

    public final int limit() {
        return pointer - offset();
    }

    public int offset() {
        return 0;
    }

    public final ByteList readStreamFully(InputStream stream) throws IOException {
        if (getClass() != ByteList.class)
            throw new IllegalStateException();

        int i = stream.available();
        if(i <= 0)
            i = 127;
        ensureCapacity(pointer + i + 1);

        int real;
        do {
            real = stream.read(this.list, pointer, list.length - pointer);
            if (real <= 0)
                break;
            pointer += real;
            ensureCapacity(pointer + 1);
        } while (true);
        stream.close();
        return this;
    }

    public final int readStream(InputStream stream, int max) throws IOException {
        if (getClass() != ByteList.class)
            throw new IllegalStateException();

        ensureCapacity(pointer + max);
        int real = stream.read(this.list, pointer, max);
        if (real > 0)
            pointer += real;
        return real;
    }

    /**
     * 注: 没有单独的R/W指针
     * Read: 0 - ptr-1
     * Write: ptr - Infinity
     */
    public final InputStream asInputStream() {
        return new AsInputStream(this);
    }

    /**
     * 注: 没有单独的R/W指针
     * Read: 0 - ptr-1
     * Write: ptr - Infinity
     */
    public final OutputStream asOutputStream() {
        return new AsOutputStream(this);
    }

    public final void writeToStream(OutputStream os) throws IOException {
        if (pos() > 0) {
            os.write(this.list, offset(), pos());
        }
    }

    public final ByteList setValue(byte[] array) {
        if (getClass() != ByteList.class)
            throw new IllegalStateException();
        if (array == null)
            array = EmptyArrays.BYTES;

        list = array;
        pointer = array.length;
        return this;
    }

    public final byte[] getByteArray() {
        return list == null ? null : (pointer == list.length && offset() == 0 ? list : toByteArray());
    }

    public void trimToSize() {
        if (list != null && pointer < list.length) {
            if (pointer > 0) {
                byte[] newList = new byte[pointer];
                System.arraycopy(list, 0, newList, 0, pointer);
                list = newList;
            } else {
                list = EmptyArrays.BYTES;
            }
        }
    }

    public int lastIndexOf(byte[] bytes) {
        return lastIndexOf(list, offset(), limit(), bytes, 0, bytes.length, limit());
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ByteList byteList = (ByteList) o;
        return ArrayUtil.rangedEquals(this.list, offset(), pos(), byteList.list, byteList.offset(), byteList.pos());
    }

    @Override
    public int hashCode() {
        return ArrayUtil.rangedHashCode(this.list, offset(), pos());
    }

    /**
     * 只写，加快速度
     */
    public final static class EmptyByteList extends ByteList {
        public EmptyByteList() {
            super();
            this.list = EmptyArrays.BYTES;
        }

        @Override
        public void ensureCapacity(int required) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void pos(int pos) {
            this.pointer = pos;
        }

        @Override
        public final void add(byte e) {
            pointer++;
        }

        public void addAll(byte[] array, int start, int length) {
            pointer += length;
        }

        @Override
        public void trimToSize() {
            throw new UnsupportedOperationException();
        }
    }


    /**
     * 只读
     */
    public final static class ReadOnlySubList extends ByteList {
        private final int offset;
        private final int length;

        public ReadOnlySubList(byte[] array, int start, int length) {
            super(array);
            this.pointer = start + length;
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
        public int offset() {
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
        public int pos() {
            return pointer - offset;
        }

        @Override
        public final void add(byte e) {
            throw new UnsupportedOperationException();
        }

        @Override
        public final void set(int index, byte e) {
            super.set(index + offset, e);
        }

        @Override
        public byte get(int index) {
            return super.get(index + offset);
        }

        byte get0(int index) {
            return (index += offset) >= list.length ? 0 : list[index];
        }

        @Override
        public int getU(int index) {
            return super.getU(index + offset);
        }

        @Override
        public void addAll(byte[] array, int start, int length) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void trimToSize() {
            throw new UnsupportedOperationException();
        }

        @Override
        public byte[] toByteArray() {
            byte[] result = new byte[length - offset];
            System.arraycopy(list, offset, result, 0, result.length);
            return result;
        }
    }

    final static class AsInputStream extends InputStream {
        final ByteList list;
        int readPtr;

        public AsInputStream(ByteList list) {
            this.list = list;
        }

        @Override
        public int read() {
            return readPtr >= list.pointer ? -1 : (list.list[readPtr++] & 0xFF);
        }

        @Override
        public int read(@Nonnull byte[] arr, int off, int len) {
            if(len == 0)
                return 0;

            int willRead = Math.min(list.pointer - readPtr, len);
            if (willRead <= 0)
                return -1;

            System.arraycopy(list.list, readPtr, arr, off, willRead);
            readPtr += willRead;
            return willRead;
        }

        @Override
        public long skip(long len) {
            long skipped = Math.min(available(), len);
            readPtr += skipped;
            return skipped;
        }

        @Override
        public int available() {
            return Math.max(0, list.pointer - readPtr);
        }
    }

    final static class AsOutputStream extends OutputStream {
        final ByteList list;

        public AsOutputStream(ByteList list) {
            this.list = list;
        }

        @Override
        public void write(int i) {
            list.add((byte) i);
        }

        @Override
        public void write(@Nonnull byte[] arr, int off, int len) {
            list.addAll(arr, off, len);
        }
    }
}