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

import roj.concurrent.OperationDone;
import roj.math.MathUtils;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.*;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/27 0:8
 */
public class WeakMyHashMap<K,V> implements Map<K,V>, CItrMap<WeakMyHashMap.Entry<V>> {
    private final ReferenceQueue<Object> queue = new ReferenceQueue<>();

    protected static class Entry<V> extends WeakReference<Object> implements EntryIterable<Entry<V>> {
        public Entry(ReferenceQueue<Object> queue, Object referent) {
            super(referent, queue);
        }

        private V v;
        private int hash;
        private Entry<V> next;

        @Override
        public Entry<V> nextEntry() {
            return next;
        }
    }

    protected Entry<?>[] entries;
    protected int size = 0;

    int length = 2;

    WeakReference<V> hasNull;

    public WeakMyHashMap() {
        this(16);
    }

    /**
     * @param size 初始化大小
     */
    public WeakMyHashMap(int size) {
        ensureCapacity(size);
    }

    public void ensureCapacity(int size) {
        if (size <= 0) throw new NegativeArraySizeException(String.valueOf(size));
        if (size < length) return;
        length = MathUtils.getMin2PowerOf(size);

        if (entries != null)
            resize();
    }


    private static class Itr<K,V> extends MapItr<Entry<V>> implements Iterator<Entry<V>> {
        public Itr(WeakMyHashMap<K,V> map) {
            super(map.entries, map);
        }

        /**
         * Returns the next element in the iteration.
         *
         * @return the next element in the iteration
         * @throws NoSuchElementException if the iteration has no more elements
         */
        @Override
        public Entry<V> next() {
            return nextT();
        }
    }

    @Override
    public int size() {
        return hasNull != null ? size + 1 : size;
    }

    @Override
    public void removeEntry0(Entry<V> entry) {
        remove(entry.get());
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    public void resize() {
        Entry<?>[] newEntries = new Entry<?>[length];
        Entry<?> entry;
        Entry<?> next;
        int i = 0, j = entries.length;
        for (; i < j; i++) {
            entry = entries[i];
            entries[i] = null;
            while (entry != null) {
                next = entry.next;
                int newKey = indexFor(entry.hash);
                Entry<?> old = newEntries[newKey];
                newEntries[newKey] = entry;
                entry.next = Helpers.cast(old);
                entry = next;
            }
        }

        this.entries = newEntries;
    }

    @Override
    @SuppressWarnings("unchecked")
    public V put(K key, V val) {
        if (key == null) {
            V prev = this.hasNull == null ? null : this.hasNull.get();
            this.hasNull = new WeakReference<>(val);
            return prev;
        }

        removeClearedEntry();

        int hash = key.hashCode();
        int index = indexFor(hash);
        Entry<V> result;
        if (this.entries == null)
            this.entries = new Entry<?>[length];
        result = (Entry<V>) entries[index];

        if (size > length * 0.8f) {
            length <<= 1;
            resize();
        }

        if (result == null) {
            (entries[index] = new Entry<>(queue, key)).hash = hash;
            size++;
            return null;
        }

        while (true) {
            if (result.equals(key)) {
                V prev = result.v;
                result.v = val;
                return prev;
            }
            if (result.next == null)
                break;
            result = result.next;
        }

        (result.next = new Entry<>(queue, key)).hash = hash;
        size++;
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public V remove(Object key) {
        if (key == null) {
            if (this.hasNull != null) {
                V prev = this.hasNull.get();
                this.hasNull = null;
                return prev;
            } else {
                return null;
            }
        }

        removeClearedEntry();

        if (this.entries == null)
            return null;
        int index = indexFor(key.hashCode());
        Entry<V> curr = (Entry<V>) entries[index];
        Entry<V> prev = null;
        while (curr != null) {
            if (Objects.equals(curr.get(), key)) {
                if (prev == null)
                    entries[index] = null;
                else {
                    prev.next = curr.next;
                }
                return curr.v;
            }
            prev = curr;
            curr = curr.next;
        }

        return null;
    }

    @Override
    public void putAll(@Nonnull Map<? extends K, ? extends V> m) {
        throw new UnsupportedOperationException();
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean containsKey(Object key) {
        if (key == null)
            return this.hasNull != null;
        if (entries == null) return false;

        removeClearedEntry();

        int index = indexFor(key.hashCode());
        Entry<V> curr = (Entry<V>) entries[index];
        while (curr != null) {
            if (Objects.equals(curr.get(), key)) {
                return true;
            }
            curr = curr.next;
        }
        return false;
    }

    @Override
    @Deprecated
    public boolean containsValue(Object value) {
        throw new UnsupportedOperationException();
    }

    @Override
    @SuppressWarnings("unchecked")
    public V get(Object key) {
        if (key == null)
            return this.hasNull.get();
        if (entries == null) return null;

        removeClearedEntry();

        int index = indexFor(key.hashCode());
        Entry<V> curr = (Entry<V>) entries[index];
        while (curr != null) {
            if (Objects.equals(curr.get(), key)) {
                return curr.v;
            }
            curr = curr.next;
        }
        return null;
    }

    @Nonnull
    public Iterator<Entry<V>> iterator() {
        // 这不香吗
        return isEmpty() ? Collections.emptyIterator() : new Itr<>(this);
    }

    int indexFor(int obj) {
        return obj & (length - 1);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("WeakHashMap").append('{');

        if (!isEmpty()) {
            removeClearedEntry();
            Iterator<Entry<V>> itr = iterator();
            while (itr.hasNext()) {
                sb.append(itr.next().get()).append(',');
            }
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.append('}').toString();
    }

    public void removeClearedEntry() {
        Entry<?> entry;
        while ((entry = (Entry<?>) queue.poll()) != null) {
            Entry<?> curr = entries[indexFor(entry.hash)];
            Entry<?> prev = null;
            while (curr != entry) {
                if (curr == null)
                    throw OperationDone.NEVER;
                prev = curr;
                curr = curr.next;
            }

            if (prev == null) {
                entries[indexFor(entry.hash)] = null;
            } else {
                prev.next = Helpers.cast(curr.next);
            }
            size--;
        }
    }

    @Override
    public void clear() {
        size = 0;
        while (queue.poll() != null) ;

        this.hasNull = null;
        if (this.entries != null)
            Arrays.fill(entries, null);

        while (queue.poll() != null) ;
    }

    @Deprecated
    public Set<K> keySet() {
        throw new UnsupportedOperationException();
    }

    @Deprecated
    public Collection<V> values() {
        throw new UnsupportedOperationException();
    }

    @Deprecated
    public Set<Map.Entry<K, V>> entrySet() {
        throw new UnsupportedOperationException();
    }

    public boolean containsNull() {
        return this.hasNull != null;
    }
}