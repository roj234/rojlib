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

import roj.util.Helpers;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/9/28 12:29
 */
public class ReuseStack<K> implements Iterable<K> {
    protected static final Entry<?> head = new Entry<>();

    protected Entry<K> tail;

    protected int size;

    public ReuseStack() {
        clear();
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public K last() {
        check();
        return tail.value;
    }

    public K pop() {
        check();
        K v = tail.value;

        tail = tail.prev;
        size--;
        return v;
    }

    private void check() {
        if (tail == head) throw new IllegalStateException("NodeStack is empty.");
    }

    public void setLast(K base) {
        check();
        tail.value = base;
    }

    public void push(K base) {
        Entry<K> entry = new Entry<>(base);

        entry.prev = tail;
        tail = entry;

        size++;
    }

    public void clear() {
        tail = Helpers.cast(head);
        size = 0;
    }

    public int size() {
        return size;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[");
        if (tail != head) {
            Entry<?> entry = tail;
            while (entry != head) {
                sb.append(entry.value).append(", ");
                entry = entry.prev;
            }
            sb.delete(sb.length() - 2, sb.length());
        }
        return sb.append(']').toString();
    }

    /**
     * Returns an iterator over elements of type {@code T}.
     *
     * @return an Iterator.
     */
    @Override
    public Iterator<K> iterator() {
        return new EntryIterator<>(this);
    }

    protected static final class Entry<k> {
        public k value;
        public Entry<k> prev;

        private Entry() {
        }

        public Entry(k base) {
            this.value = base;
        }
    }

    private static final class EntryIterator<K> implements Iterator<K> {
        Entry<K> last, curr;
        ReuseStack<K> stack;

        public EntryIterator(ReuseStack<K> stack) {
            last = Helpers.cast(head);
            curr = stack.tail;
            this.stack = stack;
        }

        @Override
        public boolean hasNext() {
            return curr != head;
        }

        @Override
        public K next() {
            if (curr == head)
                throw new NoSuchElementException();
            K k = curr.value;


            last = curr;


            curr = curr.prev;

            return k;
        }

        @Override
        public void remove() {
            if (last == null)
                throw new IllegalStateException();
            last.prev = last.prev.prev; // curr = next.prev
            stack.size--;
            last = null;
        }
    }
}
