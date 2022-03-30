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


import roj.util.ArrayUtil;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * @author Roj234
 * @since  2021/2/2 19:59
 */
public class BSLowHeap<T> implements List<T> {
    static final int DEF_SIZE = 16;
    static final float INC_RATE = 1.5f;

    protected final Comparator<T> cmp;

    protected Object[] entries;
    protected int size;

    @SuppressWarnings("unchecked")
    protected final int binarySearch(T key) {
        key.getClass();

        int low = 0;
        int high = size - 1;

        Object[] a = entries;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            int midVal = cmp.compare((T) a[mid], key);

            if (midVal < 0) {
                low = mid + 1;
            } else if (midVal > 0) {
                high = mid - 1;
            } else
                return mid; // key found
        }

        // low ...

        return -(low + 1);  // key not found.
    }

    public BSLowHeap(Comparator<T> cmp) {
        this(DEF_SIZE, cmp);
    }

    @SuppressWarnings("unchecked")
    public BSLowHeap(int capacity, Comparator<T> cmp) {
        if(capacity <= 1)
            capacity = DEF_SIZE;
        this.entries = Helpers.cast(new Object[capacity]);
        this.cmp = cmp == null ? (o1, o2) -> ((Comparable<T>)o1).compareTo(o2) : cmp;
    }

    public void resize() {
        Object[] entriesO = this.entries;

        Object[] entriesN = new Object[(int) (entriesO.length * INC_RATE + 1)];
        System.arraycopy(entriesO, 0, entriesN, 0, entriesO.length);
        this.entries = entriesN;
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean remove(Object o) {
        int i = indexOf((T) o);
        return i != -1 && remove(i) != null;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        for (Object o : c) {
            if(!contains(o))
                return false;
        }
        return true;
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        for (T o : c) {
            add(o);
        }
        return true;
    }

    @Override
    public boolean addAll(int index, @Nonnull Collection<? extends T> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        boolean fl = false;
        for (Object o : c) {
            fl |= remove(o);
        }
        return fl;
    }

    @Override
    public boolean retainAll(@Nonnull Collection<?> c) {
        throw new UnsupportedOperationException("懒得搞");
    }

    /* 入堆操作 */
    public boolean add(T node) {
        if (size == entries.length - 1) {
            resize();
        }

        int nearest = binarySearch(node);
        if(nearest >= 0) {
            return false;
        } else {
            int index = -nearest - 1;
            final Object[] data1 = this.entries;
            if(size - index > 0)
                System.arraycopy(data1, index, data1, index + 1, size - index);
            data1[index] = node;
            size++;
            return true;
        }
    }

    @SuppressWarnings("unchecked")
    public T remove(int idx) {
        if (idx >= size) throw new ArrayIndexOutOfBoundsException(idx);

        Object[] data1 = this.entries;
        if(size - idx - 1 > 0)
            System.arraycopy(data1, idx + 1, data1, idx, size - idx - 1);
        T t = (T) data1[--size];
        data1[size] = null;
        return t;
    }

    public T pop() {
        return remove(0);
    }

    @SuppressWarnings("unchecked")
    public int indexOf(Object o) {
        int index = binarySearch((T) o);

        return index >= 0 ? index : -1;
    }

    @Override
    public int lastIndexOf(Object o) {
        return indexOf(o);
    }

    @SuppressWarnings("unchecked")
    public T top() {
        if (0 >= size) throw new ArrayIndexOutOfBoundsException(0);
        return (T) entries[0];
    }

    @SuppressWarnings("unchecked")
    public T bottom() {
        if (0 >= size) throw new ArrayIndexOutOfBoundsException(-1);
        return (T) entries[size - 1];
    }

    @SuppressWarnings("unchecked")
    public T get(int idx) {
        if (idx >= size) throw new ArrayIndexOutOfBoundsException(idx);
        return (T) entries[idx];
    }

    @Override
    @SuppressWarnings("unchecked")
    public T set(int idx, T el) {
        if (idx >= size) throw new ArrayIndexOutOfBoundsException(idx);
        T oel = (T) entries[idx];
        entries[idx] = el;
        return oel;
    }

    @Override
    public void add(int index, T element) {
        throw new UnsupportedOperationException();
    }

    public void clear() {
        for (int i = 0; i < size; i++) {
            entries[i] = null;
        }
        size = 0;
    }

    @Override
    public String toString() {
        return "BSLowHeap[" + ArrayUtil.toString(entries, 0, size) + ']';
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public boolean contains(Object o) {
        return indexOf(o) != -1;
    }

    @Nonnull
    @Override
    public Iterator<T> iterator() {
        return listIterator(0);
    }

    @Nonnull
    @Override
    public ListIterator<T> listIterator() {
        return listIterator(0);
    }

    @Nonnull
    @Override
    @SuppressWarnings("unchecked")
    public ListIterator<T> listIterator(int index) {
        return entries == null || size == 0 ? Collections.emptyListIterator() : new ArrayIterator<>((T[]) entries, index, size);
    }

    @Nonnull
    @Override
    public List<T> subList(int fromIndex, int toIndex) {
        throw new UnsupportedOperationException();
    }

    @Nonnull
    @Override
    public Object[] toArray() {
        if(entries == null || size == 0)
            return new Object[0];
        Object[] objs = new Object[size];
        System.arraycopy(entries, 0, objs, 0, size);
        return objs;
    }

    @Nonnull
    @Override
    public <T1> T1[] toArray(@Nonnull T1[] a) {
        if(entries == null)
            return a;
        if(a.length < size)
            a = Arrays.copyOf(a, size);
        System.arraycopy(entries, 0, a, 0, size);
        return a;
    }

    @Override
    public void sort(Comparator<? super T> c) {
        if (c != cmp && c != null)
            throw new UnsupportedOperationException();
    }
}
