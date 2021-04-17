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

package ilib.collect;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public final class UUIDList {
    private long[] uuidH;
    private long[] uuidL;
    private int size = 0;
    private int length = 0;

    public UUIDList() {
        this(8);
    }

    public UUIDList(int len) {
        ensureCapacity(len);
    }

    public int size() {
        return size;
    }

    public void add(long from, long to) {
        ensureCapacity(size);
        uuidH[size] = from;
        uuidL[size++] = to;
    }

    public void ensureCapacity(int size) {
        if (size >= length) {
            int newLength = (size * 3) >> 1;
            long[] newList1 = new long[newLength];
            if (uuidH != null)
                System.arraycopy(uuidH, 0, newList1, 0, this.size);
            uuidH = newList1;
            long[] newList2 = new long[newLength];
            if (uuidL != null)
                System.arraycopy(uuidL, 0, newList2, 0, this.size);
            uuidL = newList2;
            length = newLength;
        }
    }

    public long getUUIDH(int id) {
        if (id < 0 || id >= this.size)
            return 0L;
        return uuidH[id];
    }

    public long getUUIDL(int id) {
        if (id < 0 || id >= this.size)
            return 0L;
        return uuidL[id];
    }

    public void clear() {
        size = 0;
    }

    public static UUIDList fromIntArray(int[] array) {
        int len = array.length >> 2;
        UUIDList list = new UUIDList(len);
        int j = 0;
        for (int i = 0; i < len; i++) {
            list.uuidH[i] = (long) array[j++] << 32 | array[j++];
        }
        for (int i = 0; i < len; i++) {
            list.uuidL[i] = (long) array[j++] << 32 | array[j++];
        }
        return list;
    }

    public int[] toIntArray() {
        int[] arr = new int[size << 2];
        int j = 0;
        for (int i = 0; i < size; i++) {
            arr[j++] = (int) (uuidH[i] >>> 32);
            arr[j++] = (int) uuidH[i];
        }
        for (int i = 0; i < size; i++) {
            arr[j++] = (int) (uuidL[i] >>> 32);
            arr[j++] = (int) uuidL[i];
        }
        return arr;
    }
}