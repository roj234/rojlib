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
import java.util.NoSuchElementException;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/25 23:1
 */
public class LinkedMyHashMap<K, V> extends MyHashMap<K, V> {
    private boolean reversed, accessOrder;

    protected Entry<K, V> createEntry(K id) {
        return new LinkedEntry<>(id, null);
    }

    public static class LinkedEntry<K, V> extends MyHashMap.Entry<K, V> {
        public LinkedEntry<K, V> prev, _next;

        protected LinkedEntry(K k, V v) {
            super(k, v);
        }
    }

    public LinkedEntry<K, V> markEndEntry() {
        return head;
    }

    public LinkedEntry<K, V> firstEntry() {
        return tail == head ? null : head._next;
    }

    public LinkedEntry<K, V> lastEntry() {
        return tail == head ? null : tail;
    }

    public V lastValue() {
        return tail == head ? null : tail.v;
    }

    public V firstValue() {
        return tail == head ? null : head._next.v;
    }

    public V valueAt(int id) {
        if (id > size) throw new ArrayIndexOutOfBoundsException(id);

        LinkedEntry<K, V> entry = head._next;
        while (id-- > 0) {
            entry = entry._next;
        }
        return entry == null ? null : entry.v;
    }

    public V valueFromLast(int id) {
        if (id > size) throw new ArrayIndexOutOfBoundsException(id);

        LinkedEntry<K, V> entry = tail;
        while (id-- > 0) {
            entry = entry.prev;
        }
        return entry == null ? null : entry.v;
    }

    public LinkedMyHashMap() {
        this(16);
    }

    public LinkedMyHashMap(int size) {
        super(size);
        head._next = head;
    }

    final LinkedEntry<K, V> head = new LinkedEntry<>(null, null);
    LinkedEntry<K, V> tail = head;

    public void setAccessOrder(boolean accessOrder) {
        this.accessOrder = accessOrder;
    }

    public LinkedMyHashMap<K, V> setReverseIterate(boolean isReverse) {
        this.reversed = isReverse;
        return this;
    }

    @Override
    public Iterator<Entry<K, V>> entryIterator() {
        return new EntryItr();
    }

    public class EntryItr implements Iterator<Entry<K, V>> {
        private LinkedEntry<K, V> entry = reversed ? tail : head._next;
        private LinkedEntry<K, V> prevEntry;

        @Override
        public boolean hasNext() {
            //if(entry == null)
            //    throw new RuntimeException("我们中出现了叛徒!" + LinkedMyHashMap.this);
            return entry != null && entry != head;
        }

        @Override
        public Entry<K, V> next() {
            if (entry == null || entry == head) {
                throw new NoSuchElementException();
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
            LinkedMyHashMap.this.remove(prevEntry.k);
        }
    }

    @Override
    void afterPut(Entry<K, V> entry) {
        LinkedEntry<K, V> entry1 = (LinkedEntry<K, V>) entry;
        entry1.prev = tail;
        tail._next = entry1;
        tail = entry1;
        entry1._next = head; // mark of END
    }

    @Override
    void afterRemove(Entry<K, V> entry) {
        LinkedEntry<K, V> entry1 = (LinkedEntry<K, V>) entry;
        entry1.prev._next = entry1._next;
        if (entry1._next != null) {
            entry1._next.prev = entry1.prev;
        }
    }

    @Override
    void afterAccess(Entry<K, V> entry, V now) {
        if(accessOrder) {
            LinkedEntry<K, V> entry1 = (LinkedEntry<K, V>) entry;
            entry1.prev._next = entry1._next;
            if (entry1._next != null) {
                entry1._next.prev = entry1.prev;
            }
            entry1.prev = tail;
            tail._next = entry1;
            tail = entry1;
            entry1._next = head;
        }
    }

    public void clear() {
        super.clear();
        tail = head._next = head;
    }
}