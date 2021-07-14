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
 * @since 2021/5/27 23:23索引不变)
 */
public class DoubleList implements Iterable<Double> {
    public static final double[] EMPTY = new double[0];
    public static final int DEFAULT_VALUE = -1;

    protected double[] list;
    protected int size = 0;
    protected int length;

    public DoubleList() {
        list = EMPTY;
        length = -1;
    }

    public DoubleList(int size) {
        list = new double[size];
        Arrays.fill(list, DEFAULT_VALUE);
        this.length = size - 1;
    }

    public void ensureCap(int cap) {
        if (length < cap) {
            double[] newList = new double[((cap * 3) >> 1) + 1];
            if (size > 0)
                System.arraycopy(list, 0, newList, 0, size);
            list = newList;
            length = ((cap * 3) >> 1) + 1;
            Arrays.fill(list, size, length, DEFAULT_VALUE);
        }
    }

    public int indexOf(double key) {
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
    public double[] toArray() {
        if(size == 0)
            return EMPTY;
        double[] arr = new double[size];
        System.arraycopy(list, 0, arr, 0, size);
        return arr;
    }

    public double[] getRawArray() {
        return list;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int size() {
        return size;
    }

    public boolean add(double e) {
        ensureCap(size + 1);
        list[size++] = e; // [1,1,1,2]
        return true; //[3]
    }

    public boolean addAll(double[] collection) {
        ensureCap(size + collection.length);
        System.arraycopy(collection, 0, list, size, collection.length);
        size += collection.length;
        return true;
    }

    public boolean addAll(double[] collection, int len) {
        if (len < 0)
            throw new NegativeArraySizeException();
        if (len == 0) return false;
        ensureCap(size + len);
        System.arraycopy(collection, 0, list, size, len);
        size += len;
        return true;
    }

    public boolean addAll(int i, double[] collection) {
        if (i > size)
            throw new ArrayIndexOutOfBoundsException(i);
        ensureCap(size + collection.length);
        System.arraycopy(list, i, list, i + collection.length, size - i);
        System.arraycopy(collection, 0, list, i, collection.length);
        size += collection.length;
        return true;
    }

    public boolean addAllReversed(int i, double[] collection) {
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

    public double set(int index, double e) {
        //if(index < 0 || index > length) // 3 < 4
        //	throw new ArrayIndexOutOfBoundsException(index);
        double o = list[index];
        list[index] = e;
        return o;
    }

    public void add(int i, double e) {
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

    public Double removeAtIndex(int index) {
        if (index >= 0 && index < size) {
            double o = list[index];
            if (size - 1 - index >= 0) {
                System.arraycopy(list, index + 1, list, index, size - 1 - index);
            }
            return o;
        }
        return null;
    }

    public int lastIndexOf(double key) {
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
    public PrimitiveIterator.OfDouble iterator() {
        return iterator(0);
    }

    @Nonnull
    public PrimitiveIterator.OfDouble iterator(int i) {
        Itr itr = new Itr();
        itr._id = i;
        return itr;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public boolean contains(double o) {
        return indexOf(o) != -1;
    }

    public double get(int _id) {
        if (_id > size) // 3 < 4
            throw new ArrayIndexOutOfBoundsException(_id);
        return list[_id]; // 2
    }

    public void clear() {
        size = 0;
    }

    private class Itr implements ListIterator<Double>, PrimitiveIterator.OfDouble {
        protected int _id = 0;
        protected int prevId = 0;

        public boolean hasNext() {
            return _id < size;
        }

        public double nextDouble() {
            return DoubleList.this.list[prevId = _id++];
        }

        public Double next() {
            return nextDouble();
        }

        public boolean hasPrevious() {
            return _id > 0;
        }

        public Double previous() {
            return previousDouble();
        }

        public double previousDouble() {
            return DoubleList.this.list[prevId = _id--];
        }

        public int nextIndex() {
            return _id + 1;
        }

        public int previousIndex() {
            return _id - 1;
        }

        public void remove() {
            DoubleList.this.remove(_id = prevId);
        }

        @Override
        public void set(Double integer) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void add(Double integer) {
            throw new UnsupportedOperationException();
        }

        public void set(int e) {
            DoubleList.this.set(prevId, e);
        }

        public void add(int e) {
            DoubleList.this.add(prevId, e);
        }
    }
}