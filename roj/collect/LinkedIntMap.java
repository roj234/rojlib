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
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public class LinkedIntMap<V> extends IntMap<V> {
    private boolean reversed;

    protected Entry<V> createEntry(int id, V value) {
        return new LinkedEntry<>(id, value);
    }

    public V lastValue() {
        return tail.v;
    }

    public V firstValue() {
        return tail == head ? null : head._next.v;
    }

    public V valueAt(int id) {
        if (id > size) throw new ArrayIndexOutOfBoundsException(id);

        LinkedEntry<V> entry = head._next;
        while (id-- > 0) {
            entry = entry._next;
        }
        return entry == null ? null : entry.v;
    }

    public V valueFromLast(int id) {
        if (id > size) throw new ArrayIndexOutOfBoundsException(id);

        LinkedEntry<V> entry = tail;
        while (id-- > 0) {
            entry = entry.prev;
        }
        return entry == null ? null : entry.v;
    }

    public static class LinkedEntry<V> extends Entry<V> {
        public LinkedEntry<V> prev, _next;

        protected LinkedEntry(int k, V v) {
            super(k, v);
        }
    }


    public LinkedIntMap() {
        this(16);
    }

    public LinkedIntMap(int size) {
        super(size);
        head._next = head;
    }

    final LinkedEntry<V> head = new LinkedEntry<>(0, null);
    LinkedEntry<V> tail = head;

    public LinkedIntMap<V> setReverse(boolean isReverse) {
        this.reversed = isReverse;
        return this;
    }

    @Override
    public Iterator<Entry<V>> entryIterator() {
        return new EntryItr();
    }

    public class EntryItr implements Iterator<Entry<V>> {
        private LinkedEntry<V> entry = reversed ? tail : head._next;
        private LinkedEntry<V> prevEntry;

        @Override
        public boolean hasNext() {
            return entry != null && entry != head;
        }

        @Override
        public Entry<V> next() {
            if (entry == null || entry == head) {
                return null;
            }

            prevEntry = entry;
            if (reversed) {
                entry = entry.prev;
            } else {
                entry = entry._next;
            }

            return prevEntry;
        }

        @Override
        public void remove() {
            LinkedIntMap.this.remove(prevEntry.k);
        }
    }

    @Override
    void afterPut(Entry<V> entry) {
        LinkedEntry<V> entry1 = (LinkedEntry<V>) entry;
        tail._next = entry1;
        entry1.prev = this.tail;
        tail = entry1;
        entry1._next = head; // mark of END
    }

    void afterRemove(Entry<V> entry) {
        LinkedEntry<V> entry1 = (LinkedEntry<V>) entry;
        entry1.prev._next = entry1._next;
        entry1._next.prev = entry1.prev;
    }


    public void clear() {
        super.clear();
        tail = head._next = head;
    }
}