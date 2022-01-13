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
import java.util.function.IntFunction;

import static roj.collect.IntMap.MAX_NOT_USING;
import static roj.collect.IntMap.NOT_USING;

public final class CharMap<V> implements MapLike<CharMap.Entry<V>>, Map<Character, V> {
    @SuppressWarnings("unchecked")
    public void putAll(CharMap<V> map) {
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

    @Override
    @SuppressWarnings("unchecked")
    public void putAll(@Nonnull Map<? extends Character, ? extends V> m) {
        if (m instanceof CharMap)
            putAll((CharMap<V>) m);
        else {
            for (Map.Entry<? extends Character, ? extends V> entry : m.entrySet()) {
                put(entry.getKey(), entry.getValue());
            }
        }
    }

    public static class Entry<V> implements MapLikeEntry<Entry<V>>, Map.Entry<Character, V> {
        char k;
        Object v;

        Entry(char k, Object v) {
            this.k = k;
            this.v = v;
        }

        public char getChar() {
            return k;
        }

        @Override
        public Character getKey() {
            return k;
        }

        @SuppressWarnings("unchecked")
        public V getValue() {
            return (V) v;
        }

        @SuppressWarnings("unchecked")
        public V setValue(V now) {
            Object v = this.v;
            this.v = now;
            return (V) v;
        }

        Entry<V> next;

        @Override
        public Entry<V> nextEntry() {
            return next;
        }
    }

    Entry<?>[] entries;
    int size = 0;

    Entry<V> notUsing = null;

    int length = 1;
    float loadFactor = 0.8f;

    public CharMap() {
        this(16);
    }

    public CharMap(int size) {
        ensureCapacity(size);
    }

    public CharMap(int size, float loadFactor) {
        ensureCapacity(size);
        this.loadFactor = loadFactor;
    }

    @SuppressWarnings("unchecked")
    public CharMap(CharMap<V> map) {
        ensureCapacity(map.size);
        this.loadFactor = map.loadFactor;
        if (map.size() == 0) return;

        this.entries = new Entry<?>[map.entries.length];
        for (int i = 0; i < this.entries.length; i++) {
            this.entries[i] = cloneNode((Entry<V>) map.entries[i]);
        }
        this.size = map.size();
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

    public boolean isEmpty() {
        return size == 0;
    }

    public Set<Entry<V>> selfEntrySet() {
        return new EntrySet<>(this);
    }

    public Collection<V> values() {
        return new Values<>(this);
    }

    public KeySet<V> selfKeySet() {
        return new KeySet<>(this);
    }

    @Nonnull
    @Override
    public Set<Character> keySet() {
        return Helpers.cast(selfEntrySet());
    }

    @Nonnull
    @Override
    public Set<Map.Entry<Character, V>> entrySet() {
        return Helpers.cast(selfEntrySet());
    }

    @Override
    public boolean containsKey(Object key) {
        return containsKey((char) key);
    }

    @Override
    public V get(Object key) {
        return get((char) key);
    }

    @Override
    public V put(Character key, V value) {
        return put((char) key, value);
    }

    @Override
    public V remove(Object key) {
        return remove((char) key);
    }

    public int size() {
        return size;
    }

    @Override
    public void removeEntry0(Entry<V> vEntry) {
        remove(vEntry.k);
    }

    @Nonnull
    public V computeIfAbsent(char k, @Nonnull IntFunction<V> supplier) {
        V v = get(k);
        if (v == null) {
            put(k, v = supplier.apply(k));
        }
        return v;
    }

    @SuppressWarnings("unchecked")
    private void resize() {
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

    public V put(char key, V e) {
        if (size > length * loadFactor) {
            length <<= 1;
            resize();
        }

        Entry<V> entry = getOrCreateEntry(key);
        V oldV = entry.setValue(e);
        if (oldV == NOT_USING) {
            oldV = null;
            size++;
        }
        return oldV;
    }

    @SuppressWarnings("unchecked")
    public V remove(char id) {
        Entry<V> prevEntry = null;
        Entry<V> entry = getEntryFirst(id, false);
        while (entry != null) {
            if (entry.k == id) {
                break;
            }
            prevEntry = entry;
            entry = entry.next;
        }

        if (entry == null)
            return null;

        this.size--;

        if (prevEntry != null) {
            prevEntry.next = entry.next;
        } else {
            this.entries[indexFor(id)] = entry.next;
        }

        V v = (V) entry.v;

        putRemovedEntry(entry);

        return v;
    }

    @SuppressWarnings("unchecked")
    public boolean containsValue(Object v) {
        return getEntry((V) v) != null;
    }

    public boolean containsKey(char i) {
        return getEntry(i) != null;
    }

    @SuppressWarnings("unchecked")
    private Entry<V> getEntry(V v) {
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

    private Entry<V> getEntry(char id) {
        Entry<V> entry = getEntryFirst(id, false);
        while (entry != null) {
            if (entry.k == id)
                return entry;
            entry = entry.next;
        }
        return null;
    }

    private Entry<V> getOrCreateEntry(char id) {
        Entry<V> entry = getEntryFirst(id, true);
        while (true) {
            if (entry.k == id)
                return entry;
            if (entry.next == null)
                break;
            entry = entry.next;
        }

        return entry.next = getCachedEntry(id, NOT_USING);
    }

    private Entry<V> getCachedEntry(char id, Object value) {
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

    private void putRemovedEntry(Entry<V> entry) {
        if (notUsing != null && notUsing.k > MAX_NOT_USING) {
            return;
        }
        entry.next = notUsing;
        entry.k = (char) (notUsing == null ? 1 : notUsing.k + 1);
        notUsing = entry;
    }

    private int indexFor(int id) {
        return (id ^ (id >>> 16)) & (length - 1);
    }

    static <V> Entry<V> createEntry(char id, Object value) {
        return new Entry<>(id, value);
    }

    @SuppressWarnings("unchecked")
    private Entry<V> getEntryFirst(char k, boolean create) {
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
            return (Entry<V>) (entries[id] = getCachedEntry(k, NOT_USING));
        }
        return entry;
    }

    public V get(char id) {
        Entry<V> entry = getEntry(id);
        return entry == null ? null : entry.getValue();
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("CharMap").append('{');
        for (Entry<V> entry : new EntrySet<>(this)) {
            sb.append(entry.getChar()).append('=').append(entry.getValue()).append(',');
        }
        if (!isEmpty()) {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.append('}').toString();
    }

    public void clear() {
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

    static final class KeyItr<V> extends MapItr<Entry<V>> implements PrimitiveIterator.OfInt {
        KeyItr(CharMap<V> map) {
            super(map.entries, map);
        }

        @Override
        public int nextInt() {
            return nextT().k;
        }
    }

    public Iterator<Entry<V>> entryIterator() {
        return null;
    }

    static final class ValItr<V> extends MapItr<Entry<V>> implements Iterator<V> {
        ValItr(CharMap<V> map) {
            super(map.entries, map);
        }

        @Override
        @SuppressWarnings("unchecked")
        public V next() {
            return (V) nextT().v;
        }
    }

    public static final class KeySet<V> extends AbstractSet<Integer> {
        private final CharMap<V> map;

        private KeySet(CharMap<V> map) {
            this.map = map;
        }

        public final int size() {
            return map.size();
        }

        public final void clear() {
            map.clear();
        }

        public final PrimitiveIterator.OfInt iterator() {
            return new KeyItr<>(map);
        }

        public final boolean contains(Object o) {
            if (!(o instanceof CharMap.Entry))
                return false;
            Entry<?> e = (Entry<?>) o;
            char key = e.getChar();
            Entry<?> comp = map.getEntry(key);
            return comp != null && comp.v == e.v;
        }

        public final boolean remove(Object o) {
            if (!(o instanceof Character)) {
                return false;
            }
            CharMap.Entry<?> entry = map.getEntry((Character) o);
            return entry != null && map.remove(entry.k) != null;
        }
    }

    static final class Values<V> extends AbstractCollection<V> {
        private final CharMap<V> map;

        private Values(CharMap<V> map) {
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

    static final class EntrySet<V> extends AbstractSet<Entry<V>> {
        private final CharMap<V> map;

        private EntrySet(CharMap<V> map) {
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
            if (!(o instanceof CharMap.Entry))
                return false;
            Entry<?> e = (Entry<?>) o;
            char key = e.getChar();
            Entry<?> comp = map.getEntry(key);
            return comp != null && comp.v == e.v;
        }

        public final boolean remove(Object o) {
            if (o instanceof Map.Entry) {
                CharMap.Entry<?> e = (CharMap.Entry<?>) o;
                return map.remove(e.k) != null;
            }
            return false;
        }

        public final Spliterator<CharMap.Entry<V>> spliterator() {
            return Spliterators.spliterator(map.entries, 0);
        }
    }
}