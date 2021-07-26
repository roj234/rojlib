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
import java.util.Arrays;
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
    /*public static void main(String[] args) {
        RingBuffer<Integer> buffer = new RingBuffer<>(9);
        Random rnd = new Random(5342);
        for (int i = 0; i < 999; i++) {
            switch (rnd.nextInt(4)) {
                case 0:
                    if(buffer.size < 9) {
                        buffer.push(i);
                    }
                    break;
                case 1:
                    if(buffer.size < 9) {
                        buffer.unshift(i);
                    }
                    break;
                case 2:
                    if(buffer.size > 0) {
                        buffer.pop();
                    }
                    break;
                case 3:
                    if(buffer.size > 0) {
                        buffer.shift();
                    }
                    break;
            }
        }
        System.out.println(buffer);
        for (Object o : buffer)
            System.out.println(o);
    }*/

    Object[] array;
    int begin, end, size;

    public RingBuffer(int capacity) {
        array = new Object[capacity];
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
        return size == 0 ? Collections.emptyIterator() : new AbstractIterator<T>() {
            int i = begin;
            boolean f;

            @Override
            @SuppressWarnings("unchecked")
            public boolean computeNext() {
                if(f) {
                    if (i == end) return false;
                    if (i == array.length) {
                        i = 0;
                    }
                }
                f = true;
                result = (T) array[i++];
                return true;
            }
        };
    }

    @Nonnull
    public Iterator<T> iteratorReverse() {
        return size == 0 ? Collections.emptyIterator() : new AbstractIterator<T>() {
            int i = end;
            boolean f;

            @Override
            @SuppressWarnings("unchecked")
            public boolean computeNext() {
                if(f) {
                    if (i == begin) return false;
                    if (i == -1) {
                        i = array.length - 1;
                    }
                }
                f = true;
                result = (T) array[i--];
                return true;
            }
        };
    }

    //  0  1  2  3
    //           E
    //  ------------
    //     0  1  2
    //     S
    //  ------------
    //    [4, 5, 6]
    @SuppressWarnings("unchecked")
    public T push(T t) {
        if(size < array.length) {
            size++;
        } else {
            if (end == begin) {
                begin++;
                if (begin == array.length) {
                    begin = 0;
                }
            }
        }

        T orig = (T) array[end];
        array[end++] = t;
        if(end == array.length) {
            end = 0;
        }
        return orig;
    }

    //  0  1  2  3
    //        E
    //  ------------
    //     0  1  2
    //        S
    //  ------------
    //    [/, /, /]
    @SuppressWarnings("unchecked")
    public T pop() {
        if(size == 0)
            throw new NoSuchElementException();
        size--;

        int e = this.end;

        if(e == 0) {
            e = array.length;
        }

        T val = (T) array[--e];
        array[e] = null;

        if(e == begin) {
            if(e == 0) {
                begin = array.length - 1;
            } else {
                begin--;
            }
        }
        this.end = e;

        if(size == 0) {
            begin = end = 0;
        }

        return val;
    }

    //  0  1  2  3
    //           E
    //  ------------
    //     0  1  2
    //     S
    //  ------------
    //    [4, 3, 2]
    @SuppressWarnings("unchecked")
    public T unshift(T t) {
        if(size < array.length) {
            size++;
        }

        int b = begin;

        if(end == 0) {
            end = array.length - 1;
        }
        if(end == b) {
            end--;
        }

        if(b == 0) {
            begin = b = array.length - 1;
        } else {
            begin = --b;
        }

        T orig = (T) array[b];
        array[b] = t;

        return orig;
    }

    //     1  2  3
    //     E
    //  ------------
    //     0  1  2
    //     S
    //  ------------
    //    [/, /, /]
    @SuppressWarnings("unchecked")
    public T shift() {
        if(size == 0)
            throw new NoSuchElementException();
        size--;

        T val = (T) array[begin];
        array[begin++] = null;

        if(begin == array.length)
            begin = 0;

        if(size == 0) {
            begin = end = 0;
        }

        return val;
    }

    public void clear() {
        begin = end = size = 0;
        Arrays.fill(array, null);
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

    public boolean remove(T entry) {
        int i = indexOf(entry);
        if(i == -1)
            return false;
        remove(i);
        return true;
    }

    @SuppressWarnings("unchecked")
    public T remove(int index) {
        T t = (T) array[index];

        if(begin > end) {
            /**
             * E   S =>
             * 0 1 2 3 4
             */
            if(index > end && index < begin)
                throw new IllegalStateException();
            if(index > end) {
                // 2, 3, or 4
                // need move twice

            }

        } else {
            if(index < begin || index > end)
                throw new IllegalStateException();
            /**
             *   S     E
             * 0 1 2 3 4
             */
            if(end > index)
                System.arraycopy(array, index + 1, array, index, end - index);
            end--;
        }

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
        for (int i = 0; i < begin; i++) {
            sb.append("  ");
        }
        sb.append("S\n  ");
        for (int i = 0; i < end; i++) {
            sb.append("  ");
        }
        return sb.append("E\n  ")
        .append(ArrayUtil.toString(array, 0, array.length)).toString();
    }
}
