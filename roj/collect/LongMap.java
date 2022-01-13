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
import java.util.function.Supplier;

import static roj.collect.IntMap.MAX_NOT_USING;
import static roj.collect.IntMap.NOT_USING;

public class LongMap<V> implements MapLike<LongMap.Entry<V>> {
    @SuppressWarnings("unchecked")
    public void putAll(LongMap<V> map) {
        if (map.entries == null) return;
        this.ensureCapacity(size + map.size());
        for (int i = 0; i < map.length; i++) {
            Entry<?> entry = map.entries[i];
            while (entry != null) {
                put(entry.k, (V) entry.v);

                entry = entry.next;
            }
        }
    }

    public V getOrDefault(long k, V def) {
        Entry<V> entry = getEntry(k);
        return entry == null ? def : entry.v;
    }

    public static class Entry<V> implements MapLikeEntry<Entry<V>> {
        protected long k;
        protected V v;

        protected Entry(long k, V v) {
            this.k = k;
            this.v = v;
        }

        public long getKey() {
            return k;
        }

        public V getValue() {
            return v;
        }

        public V setValue(V now) {
            V v = this.v;
            this.v = now;
            return v;
        }

        protected Entry<V> next;

        @Override
        public Entry<V> nextEntry() {
            return next;
        }
    }

    protected Entry<?>[] entries;
    protected int size = 0;

    protected Entry<V> notUsing = null;

    int length = 2;
    float loadFactor = 0.8f;

    boolean unmodifiable = false;

    public LongMap() {
        this(16);
    }

    public LongMap(int size) {
        ensureCapacity(size);
    }

    public LongMap(int size, float loadFactor) {
        ensureCapacity(size);
        this.loadFactor = loadFactor;
    }

    @SuppressWarnings("unchecked")
    public LongMap(LongMap<V> map) {
        ensureCapacity(map.size);
        this.loadFactor = map.loadFactor;
        if (map.size() == 0) return;

        this.entries = new Entry<?>[map.entries.length];
        for (int i = 0; i < this.entries.length; i++) {
            this.entries[i] = cloneNode((Entry<V>) map.entries[i]);
        }
        this.size = map.size();

        this.unmodifiable = map.unmodifiable;
    }

    private Entry<V> cloneNode(Entry<V> entry) {
        if (entry == null)
            return null;
        Entry<V> newEntry = getCachedEntry(entry.k, entry.getValue());
        Entry<V> head = newEntry;
        while (entry.next != null) {
            entry = entry.next;
            newEntry.next = getCachedEntry(entry.k, entry.getValue());
            newEntry = newEntry.next;
        }
        return head;
    }

    public void ensureCapacity(int size) {
        if (size < length) return;
        length = MathUtils.getMin2PowerOf(size);

        if (this.entries != null)
            resize();
    }

    public void unmodifiable() {
        this.unmodifiable = true;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public Set<Entry<V>> entrySet() {
        return new EntrySet<>(this);
    }

    public Collection<V> values() {
        return new Values<>(this);
    }

    public KeySet<V> keySet() {
        return new KeySet<>(this);
    }

    public int size() {
        return size;
    }

    @Override
    public void removeEntry0(Entry<V> vEntry) {
        remove(vEntry.k);
    }

    void afterPut(Entry<V> entry) {
    }

    @Nonnull
    public V computeIfAbsent(long k, @Nonnull Supplier<V> supplier) {
        V v = get(k);
        if (v == null) {
            put(k, v = supplier.get());
        }
        return v;
    }

    @SuppressWarnings("unchecked")
    protected void resize() {
        //System.err.println("扩容为: "+ DELIM);
        Entry<?>[] newEntries = new Entry<?>[length];
        Entry<V> entry;
        Entry<V> next;
        int i = 0, j = entries.length;
        for (; i < j; i++) {
            entry = (Entry<V>) entries[i];
            while (entry != null) {
                next = entry.next;
                int newKey = indexFor(entry.k);
                Entry<V> entry2 = (Entry<V>) newEntries[newKey];
                newEntries[newKey] = entry;
                entry.next = entry2;
                entry = next;
            }
        }

        this.entries = newEntries;
    }

    public V put(long key, V e) {
        if (unmodifiable)
            throw new IllegalStateException("Try to modify an unmodifiable map");
        if (size > length * loadFactor) {
            length <<= 1;
            resize();
        }

        Entry<V> entry = getOrCreateEntry(key);
        V oldV = entry.setValue(e);
        if (oldV == NOT_USING) {
            oldV = null;
            afterPut(entry);
            size++;
        }
        return oldV;
    }

    void afterRemove(Entry<V> entry) {
    }

    public V remove(long id) {
        if (unmodifiable)
            throw new IllegalStateException("Try to modify an unmodifiable map");

        Entry<V> prevEntry = null;
        Entry<V> toRemove = null;
        {
            Entry<V> entry = getEntryFirst(id, false);
            while (entry != null) {
                if (entry.k == id) {
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

        V v = toRemove.v;

        putRemovedEntry(toRemove);

        return v;
    }

    @SuppressWarnings("unchecked")
    public boolean containsValue(Object v) {
        return getEntry((V) v) != null;
    }

    public boolean containsKey(long i) {
        return getEntry(i) != null;
    }

    public void modifiable() {
        this.unmodifiable = false;
    }

    @SuppressWarnings("unchecked")
    Entry<V> getEntry(V v) {
        if (entries == null)
            return null;
        for (int i = 0; i < length; i++) {
            Entry<V> entry = (Entry<V>) entries[i];
            if (entry == null)
                continue;
            while (entry != null) {
                if (Objects.equals(v, entry.getValue())) {
                    return entry;
                }
                entry = entry.next;
            }
        }
        return null;
    }

    protected Entry<V> getEntry(long id) {
        Entry<V> entry = getEntryFirst(id, false);
        while (entry != null) {
            if (entry.k == id)
                return entry;
            entry = entry.next;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    Entry<V> getOrCreateEntry(long id) {
        Entry<V> entry = getEntryFirst(id, true);
        if (entry.v == NOT_USING)
            return entry;
        while (true) {
            if (entry.k == id)
                return entry;
            if (entry.next == null)
                break;
            entry = entry.next;
        }

        return entry.next = getCachedEntry(id, (V) NOT_USING);
    }

    protected Entry<V> getCachedEntry(long id, V value) {
        Entry<V> cached = this.notUsing;
        if (cached != null) {
            cached.k = id;
            cached.v = value;
            this.notUsing = cached.next;
            cached.next = null;
            return cached;
        }

        return createEntry(id, value);
    }

    protected void putRemovedEntry(Entry<V> entry) {
        if (notUsing != null && notUsing.k > MAX_NOT_USING) {
            return;
        }
        entry.next = notUsing;
        entry.k = notUsing == null ? 1 : notUsing.k + 1;
        notUsing = entry;
    }

    int indexFor(long id) {
        return (int) ((id ^ (id >>> 16) ^ (id >>> 32))) & (length - 1);
    }

    protected Entry<V> createEntry(long id, V value) {
        return new Entry<>(id, value);
    }

    @SuppressWarnings("unchecked")
    Entry<V> getEntryFirst(long k, boolean create) {
        int id = indexFor(k);
        if (entries == null) {
            if (!create)
                return null;
            entries = new Entry<?>[length];
        }
        Entry<V> entry;
        if ((entry = (Entry<V>) entries[id]) == null) {
            if (!create)
                return null;
            return (Entry<V>) (entries[id] = getCachedEntry(k, (V) NOT_USING));
        }
        return entry;
    }

    public V get(long id) {
        Entry<V> entry = getEntry(id);
        return entry == null ? null : entry.getValue();
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("LongMap").append('{');
        for (Entry<V> entry : new EntrySet<>(this)) {
            sb.append(entry.getKey()).append('=').append(entry.getValue()).append(',');
        }
        if (!isEmpty()) {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.append('}').toString();
    }

    public void clear() {
        if (unmodifiable) return;
        if (size == 0) return;
        size = 0;
        if (entries != null)
            if (notUsing == null || notUsing.k < MAX_NOT_USING) {
                for (int i = 0; i < length; i++) {
                    if (entries[i] != null) {
                        putRemovedEntry(Helpers.cast(entries[i]));
                        entries[i] = null;
                    }
                }
            } else Arrays.fill(entries, null);
    }

    static class KeyItr<V> extends MapItr<Entry<V>> implements PrimitiveIterator.OfLong {
        private KeyItr(LongMap<V> map) {
            super(map.entries, map);
        }

        @Override
        public long nextLong() {
            return nextT().k;
        }
    }

    public Iterator<Entry<V>> entryIterator() {
        return null;
    }

    static class ValItr<V> extends MapItr<Entry<V>> implements Iterator<V> {
        private ValItr(LongMap<V> map) {
            super(map.entries, map);
        }

        @Override
        public V next() {
            return nextT().v;
        }
    }

    public static class KeySet<V> extends AbstractSet<Long> {
        private final LongMap<V> map;

        private KeySet(LongMap<V> map) {
            this.map = map;
        }

        public final int size() {
            return map.size();
        }

        public final void clear() {
            map.clear();
        }

        public final PrimitiveIterator.OfLong iterator() {
            return new KeyItr<>(map);
        }

        public final boolean contains(Object o) {
            if (!(o instanceof LongMap.Entry))
                return false;
            Entry<?> e = (Entry<?>) o;
            long key = e.getKey();
            Entry<?> comp = map.getEntry(key);
            return comp != null && comp.v == e.v;
        }

        public final boolean remove(Object o) {
            if (!(o instanceof Number)) {
                return false;
            }
            LongMap.Entry<?> entry = map.getEntry(((Number) o).intValue());
            return entry != null && map.remove(entry.k) != null;
        }
    }

    static class Values<V> extends AbstractCollection<V> {
        private final LongMap<V> map;

        private Values(LongMap<V> map) {
            this.map = map;
        }

        public final int size() {
            return map.size();
        }

        public final void clear() {
            map.clear();
        }

        public final Iterator<V> iterator() {
            return isEmpty() ? Collections.emptyIterator() : new ValItr<>(map);
        }

        public final boolean contains(Object o) {
            return map.containsValue(o);
        }

        @SuppressWarnings("unchecked")
        public final boolean remove(Object o) {
            Entry<V> entry = map.getEntry((V) o);
            return entry != null && map.remove(entry.k) != null;
        }

        public final Spliterator<V> spliterator() {
            return Spliterators.spliterator(iterator(), size(), 0);
        }
    }

    static class EntrySet<V> extends AbstractSet<Entry<V>> {
        private final LongMap<V> map;

        private EntrySet(LongMap<V> map) {
            this.map = map;
        }

        public final int size() {
            return map.size();
        }

        public final void clear() {
            map.clear();
        }

        public final Iterator<Entry<V>> iterator() {
            return isEmpty() ? Collections.emptyIterator() : new EntryItr<>(map.entries, map);
        }

        public final boolean contains(Object o) {
            if (!(o instanceof LongMap.Entry))
                return false;
            Entry<?> e = (Entry<?>) o;
            long key = e.getKey();
            Entry<?> comp = map.getEntry(key);
            return comp != null && comp.v == e.v;
        }

        public final boolean remove(Object o) {
            if (o instanceof Map.Entry) {
                LongMap.Entry<?> e = (LongMap.Entry<?>) o;
                return map.remove(e.k) != null;
            }
            return false;
        }

        public final Spliterator<LongMap.Entry<V>> spliterator() {
            return Spliterators.spliterator(map.entries, 0);
        }
    }
}