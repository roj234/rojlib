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

import java.util.Iterator;
import java.util.ListIterator;
import java.util.NoSuchElementException;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51索引不变)
 */
public class ArrayIterator<E> implements ListIterator<E> {

    public static Object[] byIterator(Iterator<?> itr, int size) {
        int i = 0;
        Object[] arr = new Object[size];
        while (itr.hasNext()) {
            arr[i++] = itr.next();
        }
        return arr;
    }

    public static <T> T[] byIterator(Iterator<T> itr, T[] ts) {
        int i = 0;
        while (itr.hasNext()) {
            ts[i++] = itr.next();
        }
        return ts;

    }

    public ArrayIterator(E[] array, int i, int j) {
        this.list = array;
        this.index = i;
        this.prevId = i - 1;
        this.length = j;
    }

    public ArrayIterator(E[] array, int i) {
        this.list = array;
        this.index = i;
        this.prevId = i - 1;
        this.length = array.length;
    }

    public ArrayIterator(E[] array) {
        this.list = array;
        this.length = array.length;
    }

    private final E[] list;

    protected int index = 0;
    protected int prevId = -1;
    protected int length;

    @Override
    public boolean hasNext() {
        return index < length;
    }

    @Override
    public E next() {
        if (index >= length)
            throw new NoSuchElementException();
        return list[prevId = index++];
    }

    @Override
    public boolean hasPrevious() {
        return index > 0;
    }

    @Override
    public E previous() {
        return list[prevId = index--];
    }

    @Override
    public int nextIndex() {
        return index + 1;
    }

    @Override
    public int previousIndex() {
        return index - 1;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
        //list[prevId] = null;
    }

    @Override
    public void set(E e) {
        list[prevId] = e;
    }

    @Override
    public void add(E e) {
        throw new UnsupportedOperationException();
    }
}