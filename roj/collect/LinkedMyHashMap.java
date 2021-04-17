/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究
 * <p>
 * File version : 5
 * Author: R__
 * Filename: LinkedMyHashMap.java
 */
package roj.collect;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class LinkedMyHashMap<K, V> extends MyHashMap<K, V> {
    private boolean reversed;

    protected Entry<K, V> createEntry(K id, V v) {
        return new LinkedEntry<>(id, v);
    }

    public static class LinkedEntry<K, V> extends MyHashMap.Entry<K, V> {
        public LinkedEntry<K, V> prev, _next;

        protected LinkedEntry(K k, V v) {
            super(k, v);
        }
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

    public LinkedMyHashMap<K, V> setReverse(boolean isReverse) {
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

    void afterRemove(Entry<K, V> entry) {
        LinkedEntry<K, V> entry1 = (LinkedEntry<K, V>) entry;
        entry1.prev._next = entry1._next;
        if (entry1._next != null) {
            entry1._next.prev = entry1.prev;
        }
    }

    public void clear() {
        super.clear();
        tail = head._next = head;
    }
}