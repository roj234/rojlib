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

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A simple ring buffer
 *
 * @author Roj234
 * @version 0.1
 * @since  2021/4/13 23:25
 */
public final class RingBuffer<T> implements Iterable<T> {
    private final class Itr extends AbstractIterator<T> {
        int i;
        int dir;
        int fence;

        @SuppressWarnings("unchecked")
        public Itr(boolean rev) {
            i = rev ? tail : head;
            dir = rev ? -1 : 1;
            fence = rev ? head : tail;
            stage = AbstractIterator.CHECKED;
            result = (T) array[i];
            i += dir;
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean computeNext() {
            if (i == fence) return false;
            if (i == -1) {
                i = array.length - 1;
            } else if (i == array.length) {
                i = 0;
            }
            result = (T) array[i];
            i += dir;
            return true;
        }

        @Override
        protected void remove(T obj) {
            // todo support index change
            //RingBuffer.this.remove(i - dir);
        }
    }

    private Object[] array;
    private int      head, tail, size;

    public RingBuffer(int capacity) {
        array = new Object[capacity];
    }

    public void expand(int capacity) {
        if (array.length >= capacity) return;
        Object[] newArray = new Object[capacity];
        if (size > 0) {
            int i = 0;
            for (T t : this) {
                newArray[i++] = t;
            }
        }
        array = newArray;
    }

    public int capacity() {
        return array.length;
    }

    public int head() {
        return head;
    }

    public int tail() {
        return tail;
    }

    public Object[] getArray() {
        return array;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public boolean contains(Object o) {
        return indexOf(o) != -1;
    }

    @Nonnull
    public Iterator<T> iterator() {
        return size == 0 ? Collections.emptyIterator() : new Itr(false);
    }

    @Nonnull
    public Iterator<T> descendingIterator() {
        return size == 0 ? Collections.emptyIterator() : new Itr(true);
    }

    @SuppressWarnings("unchecked")
    public T getLast() {
        if (size == 0)
            throw new NoSuchElementException();
        return (T) array[tail];
    }

    @SuppressWarnings("unchecked")
    public T peekLast() {
        return size == 0 ? null : (T) array[tail];
    }

    @SuppressWarnings("unchecked")
    public T addLast(T t) {
        int end = tail;
        if(size < array.length) {
            size++;
        } else {
            int h = head;
            if (end == h) {
                head = ++h == array.length ? 0 : h;
            }
        }

        T orig = (T) array[end];
        array[end++] = t;
        if((tail = end) == array.length) {
            tail = 0;
        }
        return orig;
    }

    @SuppressWarnings("unchecked")
    public T removeLast() {
        if(size == 0)
            throw new NoSuchElementException();

        int e = tail;
        if(e == 0)
            e = array.length;

        T val = (T) array[--e];
        array[e] = null;

        if(--size == 0) {
            head = tail = 0;
        } else {
            int h = head;
            if (e == h) {
                head = (e == 0 ? array.length : h) - 1;
            }
            tail = e;
        }

        return val;
    }

    public T pollLast() {
        return size == 0 ? null : removeLast();
    }

    @SuppressWarnings("unchecked")
    public T getFirst() {
        if (size == 0)
            throw new NoSuchElementException();
        return (T) array[head];
    }

    @SuppressWarnings("unchecked")
    public T peekFirst() {
        return size == 0 ? null : (T) array[head];
    }

    @SuppressWarnings("unchecked")
    public T addFirst(T t) {
        if(size < array.length)
            size++;

        int h = head;
        if(tail == 0) {
            tail = array.length - 1;
        }
        if(tail == h) {
            tail--;
        }

        head = h == 0 ? (h = array.length - 1) : --h;
        T orig = (T) array[h];
        array[h] = t;
        return orig;
    }

    @SuppressWarnings("unchecked")
    public T removeFirst() {
        if(size == 0)
            throw new NoSuchElementException();

        int head = this.head;
        T val = (T) array[head];
        array[head++] = null;

        if(--size == 0) {
            this.head = tail = 0;
        } else {
            // not using %
            this.head = head == array.length ? 0 : head;
        }

        return val;
    }

    public T pollFirst() {
        return size == 0 ? null : removeFirst();
    }

    public void clear() {
        head = tail = size = 0;
        for (int i = 0; i < array.length; i++) {
            array[i] = null;
        }
    }

    @SuppressWarnings("unchecked")
    public T get(int i) {
        return (T) array[i];
    }

    @SuppressWarnings("unchecked")
    public T set(int i, T val) {
        T orig = (T) array[i];
        array[i] = val;
        return orig;
    }

    public boolean remove(Object o) {
        int i = indexOf(o);
        if(i == -1)
            return false;
        remove(i);
        return true;
    }

    @SuppressWarnings("unchecked")
    public T remove(int i) {
        Object[] array = this.array;
        T t = (T) array[i];
        int tail = this.tail;

        if (head > tail) {
            /**
             *   E S =>
             * 0 1 2 3 4
             */
            if (i > tail) {
                if (i < head)
                    throw new IllegalStateException();
                // i = 3:
                // move 4 to 3
                System.arraycopy(array, i + 1, array, i, array.length - i);
                // move 0 to 4
                array[array.length - 1] = array[0];
            }
            // move 1 to 0
            System.arraycopy(array, 1, array, 0, tail);
            this.tail = (tail == 0 ? array.length : tail) - 1;
        } else {
            if (i < head || i > tail)
                throw new IllegalStateException();
            /**
             *   S =>  E
             * 0 1 2 3 4
             */
            if (tail > i)
                System.arraycopy(array, i + 1, array, i, tail - i);
            this.tail = tail - 1;
        }
        // clear ref
        array[tail] = null;

        return t;
    }

    public int indexOf(Object o) {
        for (int i = 0; i < array.length; i++) {
            if(o.equals(array[i])) {
                return i;
            }
        }
        return -1;
    }

    public int lastIndexOf(Object o) {
        for (int i = array.length - 1; i >= 0; i--) {
            if(o.equals(array[i])) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder().append("RingBuffer{\n  ");
        for (int i = 0; i < array.length; i++) {
            sb.append(i).append(' ');
        }
        sb.append("\n  ");
        for (int i = 0; i < head; i++) {
            sb.append("  ");
        }
        sb.append("S\n  ");
        for (int i = 0; i < tail; i++) {
            sb.append("  ");
        }
        return sb.append("E\n  ")
        .append(ArrayUtil.toString(array, 0, array.length)).toString();
    }
}
