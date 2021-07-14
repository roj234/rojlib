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

package roj.collect;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.ListIterator;
import java.util.PrimitiveIterator;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/27 13:37索引不变)
 */
public class IntList implements Iterable<Integer> {
    public static final int[] EMPTY = new int[0];
    public static final int DEFAULT_VALUE = -1;

    protected int[] list;
    protected int size = 0;
    protected int length;

    public IntList() {
        list = EMPTY;
        length = -1;
    }

    public IntList(int size) {
        list = new int[size];
        Arrays.fill(list, DEFAULT_VALUE);
        this.length = size - 1;
    }

    public void ensureCap(int cap) {
        if (length < cap) {
            int[] newList = new int[((cap * 3) >> 1) + 1];
            if (size > 0)
                System.arraycopy(list, 0, newList, 0, size);
            list = newList;
            length = ((cap * 3) >> 1) + 1;
            Arrays.fill(list, size, length, DEFAULT_VALUE);
        }
    }

    public int indexOf(int key) {
        int _id = 0;
        while (_id < size) {
            if (key == list[_id]) {
                return _id;
            }
            _id++;
        }
        return -1;
    }

    @Override
    public String toString() {
        return "IntList" + Arrays.toString(list);
    }

    @Nonnull
    public int[] toArray() {
        if(size == 0)
            return EMPTY;
        int[] arr = new int[size];
        System.arraycopy(list, 0, arr, 0, size);
        return arr;
    }

    public int[] getRawArray() {
        return list;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int size() {
        return size;
    }

    public boolean add(int e) {
        ensureCap(size + 1);
        list[size++] = e; // [1,1,1,2]
        return true; //[3]
    }

    public boolean addAll(int[] collection) {
        ensureCap(size + collection.length);
        System.arraycopy(collection, 0, list, size, collection.length);
        size += collection.length;
        return true;
    }

    public boolean addAll(int[] collection, int len) {
        if (len < 0)
            throw new NegativeArraySizeException();
        if (len == 0) return false;
        ensureCap(size + len);
        System.arraycopy(collection, 0, list, size, len);
        size += len;
        return true;
    }

    public boolean addAll(int i, int[] collection) {
        if (i > size)
            throw new ArrayIndexOutOfBoundsException(i);
        ensureCap(size + collection.length);
        System.arraycopy(list, i, list, i + collection.length, size - i);
        System.arraycopy(collection, 0, list, i, collection.length);
        size += collection.length;
        return true;
    }

    public boolean addAllReversed(int i, int[] collection) {
        if (i > size)
            throw new ArrayIndexOutOfBoundsException(i);
        ensureCap(size + collection.length);
        System.arraycopy(list, i, list, i + collection.length, size - i);
        for (int k = collection.length - 1; k >= 0; k--) {
            list[i++] = collection[k];
        }
        size += collection.length;
        return true;
    }

    public int set(int index, int e) {
        //if(index < 0 || index > length) // 3 < 4
        //	throw new ArrayIndexOutOfBoundsException(index);
        int o = list[index];
        list[index] = e;
        return o;
    }

    public void add(int i, int e) {
        if (i > size)
            throw new ArrayIndexOutOfBoundsException(i);
        System.arraycopy(list, i, list, i + 1, size - i);
        list[i] = e;
    }

    public boolean remove(int e) {
        int index = indexOf(e);
        if (index > 0) {
            removeAtIndex(index);
            return true;
        }

        return false;
    }

    public Integer removeAtIndex(int index) {
        if (index >= 0 && index < size) {
            int o = list[index];
            if (size - 1 - index >= 0) {
                System.arraycopy(list, index + 1, list, index, size - 1 - index);
            }
            return o;
        }
        return null;
    }

    public int lastIndexOf(int key) {
        int _id = size;
        while (_id >= 0) {
            if (key == list[_id]) {
                return _id;
            }
            _id--;
        }
        return -1;
    }

    @Nonnull
    public PrimitiveIterator.OfInt iterator() {
        return iterator(0);
    }

    @Nonnull
    public PrimitiveIterator.OfInt iterator(int i) {
        Itr itr = new Itr();
        itr._id = i;
        return itr;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public boolean contains(int o) {
        return indexOf(o) != -1;
    }

    public int get(int _id) {
        if (_id > size) // 3 < 4
            throw new ArrayIndexOutOfBoundsException(_id);
        return list[_id]; // 2
    }

    public void clear() {
        size = 0;
    }

    private class Itr implements ListIterator<Integer>, PrimitiveIterator.OfInt {
        protected int _id = 0;
        protected int prevId = 0;

        public boolean hasNext() {
            return _id < size;
        }

        public int nextInt() {
            return IntList.this.list[prevId = _id++];
        }

        public Integer next() {
            return nextInt();
        }

        public boolean hasPrevious() {
            return _id > 0;
        }

        public Integer previous() {
            return previousInt();
        }

        public int previousInt() {
            return IntList.this.list[prevId = _id--];
        }

        public int nextIndex() {
            return _id + 1;
        }

        public int previousIndex() {
            return _id - 1;
        }

        public void remove() {
            IntList.this.remove(_id = prevId);
        }

        @Override
        public void set(Integer integer) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void add(Integer integer) {
            throw new UnsupportedOperationException();
        }

        public void set(int e) {
            IntList.this.set(prevId, e);
        }

        public void add(int e) {
            IntList.this.add(prevId, e);
        }
    }
}