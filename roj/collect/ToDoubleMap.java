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

import roj.math.MathUtils;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import java.util.*;

import static roj.collect.IntMap.MAX_NOT_USING;
import static roj.collect.IntMap.NOT_USING;

/**
 * @author Roj234
 * @since 2021/5/31 1:21
 * 基于Hash-like机制实现的较高速Map
 */
public class ToDoubleMap<K> implements MapLike<ToDoubleMap.Entry<K>>, Map<K, Double> {
    public double getDouble(K key) {
        return getOrDefault(key, Double.NaN);
    }

    public double getOrDefault(K key, double l) {
        Entry<K> entry = getEntry(key);
        return entry == null ? l : entry.v;
    }

    public Set<Entry<K>> selfEntrySet() {
        return new EntrySet<>(this);
    }

    public static class Entry<K> implements MapLikeEntry<Entry<K>>, Map.Entry<K, Double> {
        public K k;
        public double v;

        protected Entry(K k, double v) {
            this.k = k;
            this.v = v;
        }

        public K getKey() {
            return k;
        }

        /**
         * Returns the value corresponding to this entry.  If the mapping
         * has been removed from the backing map (by the iterator's
         * <tt>remove</tt> operation), the results of this call are undefined.
         *
         * @return the value corresponding to this entry
         * @throws IllegalStateException implementations may, but are not
         *                               required to, throw this exception if the entry has been
         *                               removed from the backing map.
         */
        @Override
        public Double getValue() {
            return this.v;
        }

        /**
         * Replaces the value corresponding to this entry with the specified
         * value (optional operation).  (Writes through to the map.)  The
         * behavior of this call is undefined if the mapping has already been
         * removed from the map (by the iterator's <tt>remove</tt> operation).
         *
         * @param value new value to be stored in this entry
         * @return old value corresponding to the entry
         * @throws UnsupportedOperationException if the <tt>put</tt> operation
         *                                       is not supported by the backing map
         * @throws ClassCastException            if the class of the specified value
         *                                       prevents it from being stored in the backing map
         * @throws NullPointerException          if the backing map does not permit
         *                                       null values, and the specified value is null
         * @throws IllegalArgumentException      if some property of this value
         *                                       prevents it from being stored in the backing map
         * @throws IllegalStateException         implementations may, but are not
         *                                       required to, throw this exception if the entry has been
         *                                       removed from the backing map.
         */
        @Override
        public Double setValue(Double value) {
            double ov = v;
            v = value;
            return ov;
        }

        public double getDouble() {
            return v;
        }

        public double setDouble(double v) {
            double old = this.v;
            this.v = v;
            return old;
        }

        public Entry<K> next;

        @Override
        public Entry<K> nextEntry() {
            return next;
        }

        @Override
        public String toString() {
            return String.valueOf(k) + '=' + v;
        }
    }

    protected Entry<?>[] entries;
    protected int size = 0;

    int length = 2;
    protected int mask = 1;

    float loadFactor = 0.8f;

    boolean unmodifiable = false;

    public ToDoubleMap() {
        this(16);
    }

    public ToDoubleMap(int size) {
        ensureCapacity(size);
    }

    public ToDoubleMap(int size, float loadFactor) {
        ensureCapacity(size);
        this.loadFactor = loadFactor;
    }

    public ToDoubleMap(ToDoubleMap<K> map) {
        this.putAll(map);
    }

    public void ensureCapacity(int size) {
        if (size < length) return;
        length = MathUtils.getMin2PowerOf(size);
        mask = length - 1;

        if (this.entries != null)
            resize();
    }

    public void unmodifiable() {
        this.unmodifiable = true;
    }

    @Nonnull
    public Set<Map.Entry<K, Double>> entrySet() {
        return Helpers.cast(new EntrySet<>(this));
    }

    @Nonnull
    public Set<K> keySet() {
        return new KeySet<>(this);
    }

    @Nonnull
    @Override
    public Collection<Double> values() {
        throw new UnsupportedOperationException();
    }

    public int size() {
        return size;
    }

    @Override
    public void removeEntry0(Entry<K> entry) {
        remove(entry.k);
    }

    public boolean isEmpty() {
        return size == 0;
    }

    void afterPut(K key, double val) {
    }


    @SuppressWarnings("unchecked")
    public void putAll(@Nonnull Map<? extends K, ? extends Double> otherMap) {
        if (unmodifiable)
            throw new IllegalStateException("Try to modify an unmodifiable map");
        if (otherMap instanceof ToDoubleMap)
            putAll((ToDoubleMap<K>) otherMap);
        else {
            for (Map.Entry<? extends K, ? extends Double> entry : otherMap.entrySet()) {
                this.putDouble(entry.getKey(), entry.getValue());
            }
        }
    }

    @SuppressWarnings("unchecked")
    public void putAll(ToDoubleMap<K> otherMap) {
        if (unmodifiable)
            throw new IllegalStateException("Try to modify an unmodifiable map");
        for (int i = 0; i < otherMap.length; i++) {
            Entry<K> entry = (Entry<K>) otherMap.entries[i];
            if (entry == null)
                continue;
            while (entry != null) {
                this.putDouble(entry.k, entry.v);
                entry = entry.next;
            }
        }
    }

    @SuppressWarnings("unchecked")
    protected void resize() {
        Entry<?>[] newEntries = new Entry<?>[length];
        Entry<K> entry;
        Entry<K> next;
        int i = 0, j = entries.length;
        for (; i < j; i++) {
            entry = (Entry<K>) entries[i];
            entries[i] = null;
            while (entry != null) {
                next = entry.next;
                int newKey = indexFor(entry.k);
                Entry<K> old = (Entry<K>) newEntries[newKey];
                newEntries[newKey] = entry;
                entry.next = old;
                entry = next;
            }
        }

        this.entries = newEntries;
    }

    @Override
    public Double put(K key, Double value) {
        return putDouble(key, value);
    }

    public Double putDouble(K key, double e) {
        if (unmodifiable)
            throw new IllegalStateException("Try to modify an unmodifiable map");
        if (size > length * loadFactor) {
            length <<= 1;
            mask = length - 1;
            resize();
        }

        Entry<K> entry = getOrCreateEntry(key);
        Double oldValue = entry.v;
        if (entry.k == NOT_USING) {
            oldValue = null;
            entry.k = key;
            afterPut(key, e);
            size++;
        }
        afterChange(key, oldValue, entry.v = e);
        return oldValue;
    }

    void afterChange(K key, Double original, double now) {
    }

    void afterRemove(Entry<K> entry) {
    }

    @SuppressWarnings("unchecked")
    public Double remove(Object o) {
        K id = (K) o;
        if (unmodifiable)
            throw new IllegalStateException("Try to modify an unmodifiable map");
        Entry<K> prevEntry = null;
        Entry<K> toRemove = null;
        {
            Entry<K> entry = getEntryFirst(id, false);
            while (entry != null) {
                if (Objects.equals(entry.k, id)) {
                    toRemove = entry;
                    break;
                }
                prevEntry = entry;
                entry = entry.next;
            }
        }

        if (toRemove == null)
            return null;

        afterRemove(toRemove);

        this.size--;

        if (prevEntry != null) {
            prevEntry.next = toRemove.next;
        } else {
            this.entries[indexFor(id)] = toRemove.next;
        }

        double v = toRemove.v;

        putRemovedEntry(toRemove);

        return v;
    }

    public boolean containsValue(Object e) {
        return containsDoubleValue((Double) e);
    }

    public boolean containsDoubleValue(double e) {
        return getValueEntry(e) != null;
    }

    protected Entry<K> notUsing = null;

    protected Entry<K> getCachedEntry(K id, double value) {
        Entry<K> cached = this.notUsing;
        if (cached != null) {
            cached.k = id;
            cached.v = value;
            this.notUsing = cached.next;
            cached.next = null;
            return cached;
        }

        return new Entry<>(id, value);
    }

    protected void putRemovedEntry(Entry<K> entry) {
        if (notUsing != null && notUsing.v > MAX_NOT_USING) {
            return;
        }
        entry.k = null;
        entry.v = notUsing == null ? 1 : notUsing.v + 1;
        entry.next = notUsing;
        notUsing = entry;
    }


    @SuppressWarnings("unchecked")
    protected Entry<K> getValueEntry(double value) {
        if (entries == null) return null;
        for (int i = 0; i < length; i++) {
            Entry<K> entry = (Entry<K>) entries[i];
            if (entry == null)
                continue;
            while (entry != null) {
                if (value == entry.v) {
                    return entry;
                }
                entry = entry.next;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public boolean containsKey(Object i) {
        Entry<K> entry = getEntry((K) i);

        return entry != null;
    }

    public void modifiable() {
        this.unmodifiable = false;
    }

    public Entry<K> getEntry(K id) {
        Entry<K> entry = getEntryFirst(id, false);
        while (entry != null) {
            if (Objects.equals(id, entry.k)) {
                return entry;
            }
            entry = entry.next;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    protected Entry<K> getOrCreateEntry(K id) {
        Entry<K> entry = getEntryFirst(id, true);
        if (entry.k == NOT_USING)
            return entry;
        while (true) {
            if (Objects.equals(id, entry.k))
                return entry;
            if (entry.next == null)
                break;
            entry = entry.next;
        }
        Entry<K> firstUnused = getCachedEntry((K) NOT_USING, 0);
        entry.next = firstUnused;
        return firstUnused;
    }

    int indexFor(K id) {
        int v;
        return id == null ? 0 : ((v = id.hashCode()) ^ (v >>> 16)) & mask;
    }

    @SuppressWarnings("unchecked")
    protected Entry<K> getEntryFirst(K id, boolean create) {
        int i = indexFor(id);
        if (entries == null) {
            if (!create)
                return null;
            entries = new Entry<?>[length];
        }
        Entry<K> entry;
        if ((entry = (Entry<K>) entries[i]) == null) {
            if (!create)
                return null;
            entries[i] = entry = getCachedEntry((K) NOT_USING, 0);
        }
        return entry;
    }

    @SuppressWarnings("unchecked")
    public Double get(Object id) {
        Entry<K> entry = getEntry((K) id);
        return entry == null ? null : entry.v;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("ToDoubleMap").append('{');
        for (ToDoubleMap.Entry<K> entry : new EntrySet<>(this)) {
            sb.append(entry.getKey()).append('=').append(entry.getDouble()).append(',');
        }
        if (!isEmpty()) {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.append('}').toString();
    }

    public void clear() {
        if (unmodifiable)
            throw new IllegalStateException("Try to modify an unmodifiable map");
        if (size == 0)
            return;
        size = 0;
        if (entries != null)
            if (notUsing == null || notUsing.v < MAX_NOT_USING) {
                for (int i = 0; i < length; i++) {
                    if (entries[i] != null) {
                        putRemovedEntry(Helpers.cast(entries[i]));
                        entries[i] = null;
                    }
                }
            } else Arrays.fill(entries, null);
    }

    static class KeyItr<K> extends MapItr<Entry<K>> implements Iterator<K> {
        public KeyItr(ToDoubleMap<K> map) {
            super(map.entries, map);
        }

        @Override
        public K next() {
            return nextT().k;
        }
    }

    static class KeySet<K> extends AbstractSet<K> {
        private final ToDoubleMap<K> map;

        public KeySet(ToDoubleMap<K> map) {
            this.map = map;
        }

        public final int size() {
            return map.size();
        }

        public final void clear() {
            map.clear();
        }

        public final Iterator<K> iterator() {
            return isEmpty() ? Collections.emptyIterator() : new KeyItr<>(map);
        }

        public final boolean contains(Object o) {
            return map.containsKey(o);
        }

        public final boolean remove(Object o) {
            return map.remove(o) != null;
        }
    }

    static class EntrySet<K> extends AbstractSet<Entry<K>> {
        private final ToDoubleMap<K> map;

        public EntrySet(ToDoubleMap<K> map) {
            this.map = map;
        }

        public final int size() {
            return map.size();
        }

        public final void clear() {
            map.clear();
        }

        @Nonnull
        public final Iterator<Entry<K>> iterator() {
            return isEmpty() ? Collections.emptyIterator() : Helpers.cast(new EntryItr<>(map.entries, map));
        }

        @SuppressWarnings("unchecked")
        public final boolean contains(Object o) {
            if (!(o instanceof ToDoubleMap.Entry)) return false;
            ToDoubleMap.Entry<?> e = (ToDoubleMap.Entry<?>) o;
            Object key = e.getKey();
            ToDoubleMap.Entry<?> comp = map.getEntry((K) key);
            return comp != null && comp.v == e.v;
        }

        public final boolean remove(Object o) {
            if (o instanceof ToDoubleMap.Entry) {
                ToDoubleMap.Entry<?> e = (ToDoubleMap.Entry<?>) o;
                return map.remove(e.k) != null;
            }
            return false;
        }

        public final Spliterator<Entry<K>> spliterator() {
            return Spliterators.spliterator(iterator(), size(), 0);
        }
    }


}