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
import java.util.function.ToIntFunction;

import static roj.collect.IntMap.MAX_NOT_USING;
import static roj.collect.IntMap.NOT_USING;

public class ToIntMap<K> implements CItrMap<ToIntMap.Entry<K>>, Map<K, Integer>, ToIntFunction<K> {
    public int getInt(K key) {
        return getOrDefault(key, 0);
    }

    public int getOrDefault(K key, int l) {
        Entry<K> entry = getEntry(key);
        return entry == null ? l : entry.v;
    }

    @Override
    public int applyAsInt(K value) {
        return getOrDefault(value, -1);
    }

    public static class Entry<K> implements EntryIterable<Entry<K>>, Map.Entry<K, Integer> {
        public K k;
        public int v;

        protected Entry(K k, int v) {
            this.k = k;
            this.v = v;
        }

        public K getKey() {
            return k;
        }

        @Override
        public Integer getValue() {
            return v;
        }

        @Override
        public Integer setValue(Integer value) {
            int ov = this.v;
            this.v = value;
            return ov;
        }

        public int getInt() {
            return v;
        }

        public int setInt(int v) {
            int old = this.v;
            this.v = v;
            return old;
        }

        public Entry<K> next;

        @Override
        public Entry<K> nextEntry() {
            return next;
        }
    }

    protected Entry<?>[] entries;
    protected int size = 0;

    int length = 2;
    protected int mask = 1;

    float loadFactor = 0.8f;

    boolean unmodifiable = false;

    public ToIntMap() {
        this(16);
    }

    public ToIntMap(int size) {
        ensureCapacity(size);
    }

    public ToIntMap(int size, float loadFactor) {
        ensureCapacity(size);
        this.loadFactor = loadFactor;
    }

    public ToIntMap(ToIntMap<K> map) {
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

    public EntrySet<K> selfEntrySet() {
        return new EntrySet<>(this);
    }

    @Nonnull
    public Set<Map.Entry<K, Integer>> entrySet() {
        return Helpers.cast(new EntrySet<>(this));
    }

    public KeySet<K> selfKeySet() {
        return new KeySet<>(this);
    }

    @Nonnull
    public Set<K> keySet() {
        return new KeySet<>(this);
    }

    @Nonnull
    public Collection<Integer> values() {
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

    void afterPut(K key, int val) {
    }

    @SuppressWarnings("unchecked")
    public void putAll(@Nonnull Map<? extends K, ? extends Integer> otherMap) {
        if (unmodifiable)
            throw new IllegalStateException("Try to modify an unmodifiable map");
        if (otherMap instanceof ToIntMap)
            putAll((ToIntMap<K>) otherMap);
        else {
            for (Map.Entry<? extends K, ? extends Integer> entry : otherMap.entrySet()) {
                this.putInt(entry.getKey(), entry.getValue());
            }
        }
    }

    @SuppressWarnings("unchecked")
    public void putAll(ToIntMap<K> otherMap) {
        if (unmodifiable)
            throw new IllegalStateException("Try to modify an unmodifiable map");
        for (int i = 0; i < otherMap.length; i++) {
            Entry<K> entry = (Entry<K>) otherMap.entries[i];
            if (entry == null)
                continue;
            while (entry != null) {
                this.putInt(entry.k, entry.v);
                entry = entry.next;
            }
        }
    }

    @SuppressWarnings("unchecked")
    protected void resize() {
        //System.err.println("扩容为: "+ DELIM);
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

    @Deprecated
    public Integer put(K key, Integer e) {
        return putInt(key, e);
    }

    public Integer putInt(K key, int e) {
        if (unmodifiable)
            throw new IllegalStateException("Try to modify an unmodifiable map");
        if (size > length * loadFactor) {
            length <<= 1;
            mask = length - 1;
            resize();
        }

        Entry<K> entry = getOrCreateEntry(key);
        Integer oldValue = entry.v;
        if (entry.k == NOT_USING) {
            oldValue = null;
            entry.k = key;
            afterPut(key, e);
            size++;
        }
        afterChange(key, oldValue, entry.v = e);
        return oldValue;
    }

    void afterChange(K key, Integer original, int now) {
    }

    void afterRemove(Entry<K> entry) {
    }

    @SuppressWarnings("unchecked")
    public Integer remove(Object o) {
        K id = (K) o;
        if (unmodifiable)
            throw new IllegalStateException("Try to modify an unmodifiable map");
        Entry<K> prevEntry = null;
        Entry<K> toRemove = null;
        {
            Entry<K> entry = getEntryFirst(id, false);
            while (entry != null) {
                if (Objects.equals(id, entry.k)) {
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

        int v = toRemove.v;

        putRemovedEntry(toRemove);

        return v;
    }

    @Override
    public boolean containsValue(Object value) {
        return containsIntValue((Integer) value);
    }

    public boolean containsIntValue(int e) {
        return getValueEntry(e) != null;
    }

    protected Entry<K> notUsing = null;

    protected Entry<K> getCachedEntry(K id, int value) {
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
    protected Entry<K> getValueEntry(int value) {
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
    @Deprecated
    public Integer get(Object id) {
        Entry<K> entry = getEntry((K) id);
        return entry == null ? null : entry.v;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("ToIntMap").append('{');
        for (ToIntMap.Entry<K> entry : new ToIntMap.EntrySet<>(this)) {
            sb.append(entry.getKey()).append('=').append(entry.getInt()).append(',');
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
        public KeyItr(ToIntMap<K> map) {
            super(map.entries, map);
        }

        @Override
        public K next() {
            return nextT().k;
        }
    }

    public static class KeySet<K> extends AbstractSet<K> {
        private final ToIntMap<K> map;

        public KeySet(ToIntMap<K> map) {
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

    public static class EntrySet<K> extends AbstractSet<Entry<K>> {
        private final ToIntMap<K> map;

        public EntrySet(ToIntMap<K> map) {
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
            if (!(o instanceof ToIntMap.Entry)) return false;
            ToIntMap.Entry<?> e = (ToIntMap.Entry<?>) o;
            Object key = e.getKey();
            ToIntMap.Entry<?> comp = map.getEntry((K) key);
            return comp != null && comp.v == e.v;
        }

        public final boolean remove(Object o) {
            if (o instanceof ToIntMap.Entry) {
                ToIntMap.Entry<?> e = (ToIntMap.Entry<?>) o;
                return map.remove(e.k) != null;
            }
            return false;
        }

        public final Spliterator<Entry<K>> spliterator() {
            return Spliterators.spliterator(iterator(), size(), 0);
        }
    }


}