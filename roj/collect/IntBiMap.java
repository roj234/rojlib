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
import java.util.function.ToIntFunction;

import static roj.collect.IntMap.NOT_USING;

public class IntBiMap<V> implements CItrMap<IntMap.Entry<V>>, ToIntFunction<V> {
    public void setNullId(int nullId) {
        this.nullId = nullId;
    }

    @Override
    public int applyAsInt(V value) {
        return getByValue(value);
    }

    public static class Entry<V> extends IntMap.Entry<V> {
        protected Entry(int k, V v) {
            super(k, v);
        }

        protected Entry<V> valueNext;

        public V setValue(V now) {
            throw new UnsupportedOperationException();
        }

        public Entry<V> nextEntry() {
            return (Entry<V>) next;
        }
    }

    public static final class Inverse<x> {
        private final IntBiMap<x> parent;

        private Inverse(IntBiMap<x> parent) {
            this.parent = parent;
        }

        public int size() {
            return parent.size();
        }

        public boolean isEmpty() {
            return parent.isEmpty();
        }

        public boolean containsKey(x key) {
            return parent.containsValue(key);
        }

        public boolean containsValue(int key) {
            return parent.containsKey(key);
        }

        public int get(x key) {
            return parent.getByValue(key);
        }

        public int put(x key, int value) {
            return parent.putByValue(value, key);
        }

        public int forcePut(x key, int value) {
            return parent.forcePutByValue(value, key);
        }

        public int remove(x key) {
            return parent.removeByValue(key);
        }

        public void clear() {
            parent.clear();
        }

        public Set<x> keySet() {
            return new Values<>(parent);
        }

        public Collection<Integer> values() {
            return new KeySet<>(parent);
        }

        public Set<Entry<x>> entrySet() {
            throw new UnsupportedOperationException();
        }

        public IntBiMap<x> flip() {
            return parent;
        }
    }

    protected Entry<?>[] entries;
    protected Entry<?>[] valueEntries;

    protected int size = 0;

    int length = 2, mask = 1;

    float loadFactor = 0.8f;
    int nullId = -1;

    boolean unmodifiable = false;

    private final Inverse<V> inverse = new Inverse<>(this);

    public IntBiMap() {
        this(16);
    }

    public IntBiMap(int size) {
        ensureCapacity(size);
    }

    public void ensureCapacity(int size) {
        if (size < length) return;
        length = MathUtils.getMin2PowerOf(size);
        mask = length - 1;

        resize();
    }

    public V find(V v) {
        return getOrDefault(getByValue(v), v);
    }

    public V getOrDefault(int key, V v) {
        Entry<V> entry = getKeyEntry(key);
        return entry == null ? v : entry.v;
    }

    public Inverse<V> flip() {
        return this.inverse;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public void unmodifiable() {
        this.unmodifiable = true;
    }

    @Deprecated
    public void setNoRefreshList(boolean flag) {
    }

    public Set<Entry<V>> entrySet() {
        return new EntrySet<>(this);
    }

    public Collection<V> values() {
        return new Values<>(this);
    }

    public Set<Integer> keySet() {
        return new KeySet<>(this);
    }

    public KeySet<V> selfKeySet() {
        return new KeySet<>(this);
    }

    public int size() {
        return size;
    }

    @Override
    public void removeEntry0(IntMap.Entry<V> vEntry) {
        removeEntry((Entry<V>) vEntry);
    }

    @Nonnull
    public V computeIfAbsent(int k, @Nonnull IntFunction<V> supplier) {
        V v = get(k);
        if (v == null) {
            put(k, v = supplier.apply(k));
        }
        return v;
    }

    @SuppressWarnings("unchecked")
    protected void resize() {
        if(valueEntries != null && entries != null) {
            Entry<?>[] newEntries = new Entry<?>[length];
            Entry<?>[] newValues = new Entry<?>[length];

            Entry<V> entry;
            Entry<V> next;
            int i = 0, j = entries.length;

            for (; i < j; i++) {
                entry = (Entry<V>) entries[i];
                while (entry != null) {
                    next = entry.nextEntry();
                    int newIndex = indexFor(entry.k);
                    Entry<V> old = (Entry<V>) newEntries[newIndex];
                    newEntries[newIndex] = entry;
                    entry.next = old;
                    entry = next;
                }

                entry = (Entry<V>) valueEntries[i];
                while (entry != null) {
                    next = entry.valueNext;

                    int newIndex = indexFor(hashFor(entry.v));
                    Entry<V> old = (Entry<V>) newValues[newIndex];
                    newValues[newIndex] = entry;
                    entry.valueNext = old;

                    entry = next;
                }
            }
            this.valueEntries = newValues;
            this.entries = newEntries;

        } else if(this.valueEntries != this.entries)
            throw new Error();
    }

    protected int hashFor(Object v) {
        return v == null ? 0 : v.hashCode();
    }

    public V put(int key, V e) {
        return put0(key, e, false);
    }

    public V forcePut(int key, V e) {
        return put0(key, e, true);
    }

    public int putByValue(int key, V e) {
        return putByValue0(e, key, false);
    }

    public int forcePutByValue(int key, V e) {
        return putByValue0(e, key, true);
    }

    private int putByValue0(V v, int key, boolean replace) {
        if (unmodifiable)
            throw new IllegalStateException("Try to modify an unmodifiable map");
        if (size > length * loadFactor) {
            length <<= 1;
            mask = length - 1;
            resize();
        }

        Entry<V> keyEntry = getKeyEntry(key);
        Entry<V> valueEntry = getValueEntry(v);

        if (keyEntry != null) {
            if (keyEntry == valueEntry) {
                return key;
            }

            if (valueEntry != null) { // value 替换key
                removeEntry(keyEntry);
                // keyEntry will be deleted

                removeKeyEntry(valueEntry, valueEntry.k);

                int old = valueEntry.k;
                valueEntry.k = key;

                putKeyEntry(valueEntry);

                return old;
            } else {
                if (!replace)
                    throw new IllegalArgumentException("Multiple value(" + v + ", " + keyEntry.v + ") bind to same key(" + keyEntry.k + ")! use forcePut()!");

                // key找到, 没找到value
                removeValueEntry(keyEntry, keyEntry.v);

                keyEntry.v = v;

                putValueEntry(keyEntry);

                return key;
            }
        } else {
            if (valueEntry != null) {

                // key没找到, 找到value
                int oldKey = valueEntry.k;
                removeKeyEntry(valueEntry, oldKey);

                valueEntry.k = key;

                putKeyEntry(valueEntry);

                return oldKey;
            } else {
                // 全为空
                putValueEntry(createEntry(key, v));

                return nullId;
            }
        }
    }

    private V put0(int key, V v, boolean replace) {
        if (unmodifiable)
            throw new IllegalStateException("Try to modify an unmodifiable map");
        if (size > length * loadFactor) {
            length <<= 1;
            mask = length - 1;
            resize();
        }

        Entry<V> keyEntry = getKeyEntry(key);
        Entry<V> valueEntry = getValueEntry(v);

        // key替换value

        if (keyEntry != null) {
            if (keyEntry == valueEntry) {
                return v;
            }


            if (valueEntry != null) {
                // key和value都找到了
                removeEntry(valueEntry);
            }
            // key找到, 没找到value
            V oldV = keyEntry.v;

            removeValueEntry(keyEntry, oldV);

            keyEntry.v = v;
            putValueEntry(keyEntry);

            return oldV;
        } else {
            if (valueEntry != null) {
                if (!replace)
                    throw new IllegalArgumentException("Multiple key(" + key + ", " + valueEntry.k + ") bind to same value(" + valueEntry.v + ")! use forcePut()!");

                // key没找到, 找到value
                removeKeyEntry(valueEntry, valueEntry.k);

                valueEntry.k = key;

                putKeyEntry(valueEntry);

                return v;
            } else {
                // 全为空
                putValueEntry(createEntry(key, v));
                return null;
            }
        }
    }

    public V remove(int id) {
        if (unmodifiable)
            throw new IllegalStateException("Try to modify an unmodifiable map");
        Entry<V> entry = getKeyEntry(id);
        if (entry == null)
            return null;
        removeEntry(entry);
        return entry.v;
    }

    public int removeByValue(V v) {
        if (unmodifiable)
            throw new IllegalStateException("Try to modify an unmodifiable map");
        Entry<V> entry = getValueEntry(v);
        if (entry == null)
            return nullId;
        removeEntry(entry);
        return entry.k;
    }

    public int getByValue(V key) {
        Entry<V> entry = getValueEntry(key);
        return entry == null ? nullId : entry.k;
    }

    public V get(int id) {
        Entry<V> entry = getKeyEntry(id);
        return entry == null ? null : entry.v;
    }

    public boolean containsKey(int i) {
        return getKeyEntry(i) != null;
    }

    public boolean containsValue(V v) {
        return getValueEntry(v) != null;
    }

    public void modifiable() {
        this.unmodifiable = false;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("IntBiMap").append('{');
        for (Entry<V> entry : new EntrySet<>(this)) {
            sb.append(entry.getKey()).append('=').append(entry.getValue()).append(',');
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
        if (entries != null && valueEntries != null && entries.length < 128) {
            Arrays.fill(entries, null);
            Arrays.fill(valueEntries, null);
        } else {
            entries = valueEntries = null;
            length = 2;
            mask = 1;
        }
    }

    protected void removeEntry(Entry<V> toRemove) {
        removeKeyEntry(toRemove, toRemove.k);
        removeValueEntry(toRemove, toRemove.v);
        this.size--;
    }

    @SuppressWarnings("unchecked")
    boolean removeKeyEntry(Entry<V> entry, int index) {
        index = indexFor(index);
        if (entries == null)
            return false;
        Entry<V> currentEntry;
        Entry<V> prevEntry;
        if ((currentEntry = (Entry<V>) entries[index]) == null) {
            return false;
        }

        if (currentEntry == entry) {
            entries[index] = currentEntry.nextEntry();
            entry.next = null;
            return true;
        }

        while (currentEntry.next != null) {
            prevEntry = currentEntry;
            currentEntry = currentEntry.nextEntry();
            if (currentEntry == entry) {
                prevEntry.next = entry.next;
                entry.next = null;
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    boolean removeValueEntry(Entry<V> entry, V v) {
        int index = indexFor(hashFor(v));
        if (valueEntries == null)
            return false;
        Entry<V> currentEntry;
        Entry<V> prevEntry;
        if ((currentEntry = (Entry<V>) valueEntries[index]) == null) {
            return false;
        }

        if (currentEntry == entry) {
            valueEntries[index] = entry.valueNext;
            entry.valueNext = null;
            return true;
        }

        while (currentEntry.valueNext != null) {
            prevEntry = currentEntry;
            currentEntry = currentEntry.valueNext;
            if (currentEntry == entry) {
                prevEntry.valueNext = entry.valueNext;
                entry.valueNext = null;
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    void putValueEntry(Entry<V> entry) {
        int index = indexFor(hashFor(entry.v));
        if (valueEntries == null)
            valueEntries = new Entry<?>[length];
        Entry<V> currentEntry;
        if ((currentEntry = (Entry<V>) valueEntries[index]) == null) {
            valueEntries[index] = entry;
        } else {
            while (currentEntry.valueNext != null) {
                if (currentEntry == entry)
                    return;
                currentEntry = currentEntry.valueNext;
            }
            currentEntry.valueNext = entry;
        }
    }

    @SuppressWarnings("unchecked")
    void putKeyEntry(Entry<V> entry) {
        int index = indexFor(entry.k);
        if (entries == null)
            entries = new Entry<?>[length];
        Entry<V> currentEntry;
        if ((currentEntry = (Entry<V>) entries[index]) == null) {
            entries[index] = entry;
        } else {
            while (currentEntry.next != null) {
                if (currentEntry == entry)
                    return;
                currentEntry = currentEntry.nextEntry();
            }
            currentEntry.next = entry;
        }
    }

    @SuppressWarnings("unchecked")
    protected Entry<V> getValueEntry(V v) {
        if (valueEntries == null)
            return null;
        int id = indexFor(hashFor(v));

        Entry<V> entry = (Entry<V>) valueEntries[id];

        while (entry != null) {
            if (Objects.equals(v, entry.v)) {
                return entry;
            }
            entry = entry.valueNext;
        }
        return null;
    }

    protected Entry<V> getKeyEntry(int id) {
        Entry<V> entry = getEntryFirst(id, false);
        if (entry == null) return null;
        while (entry != null) {
            if (entry.k == id)
                return entry;
            entry = entry.nextEntry();
        }
        return null;
    }

    protected Entry<V> createEntry(int id, V v) {
        Entry<V> entry = getEntryFirst(id, true);
        size++;
        if (entry.v == NOT_USING) {
            entry.v = v;
            return entry;
        }
        while (entry.next != null) {
            entry = entry.nextEntry();
        }
        Entry<V> subEntry = new Entry<>(id, v);
        entry.next = subEntry;
        return subEntry;
    }

    int indexFor(int id) {
        return (id ^ (id >>> 16)) & mask;
    }

    @SuppressWarnings("unchecked")
    Entry<V> getEntryFirst(int id, boolean create) {
        int id1 = indexFor(id);
        if (entries == null) {
            if (!create)
                return null;
            entries = new Entry<?>[length];
        }
        Entry<V> entry;
        if ((entry = (Entry<V>) entries[id1]) == null) {
            if (!create)
                return null;
            entries[id1] = entry = new Entry<>(id, (V) NOT_USING);
        }
        return entry;
    }

    public static final class KeySet<V> extends AbstractSet<Integer> {
        private final IntBiMap<V> map;

        KeySet(IntBiMap<V> map) {
            this.map = map;
        }

        public final int size() {
            return map.size();
        }

        public final void clear() {
            map.clear();
        }

        public final PrimitiveIterator.OfInt iterator() {
            return new IntMap.KeyItr<>(map.entries, map);
        }

        public final boolean contains(Object o) {
            if (!(o instanceof Number)) return false;
            return map.containsKey(((Number) o).intValue());
        }

        public final boolean remove(Object o) {
            if (!(o instanceof Number)) return false;
            return map.remove(((Number) o).intValue()) != null;
        }
    }

    static final class Values<V> extends AbstractSet<V> {
        private final IntBiMap<V> map;

        public Values(IntBiMap<V> map) {
            this.map = map;
        }

        public final int size() {
            return map.size();
        }

        public final void clear() {
            map.clear();
        }

        public final Iterator<V> iterator() {
            return isEmpty() ? Collections.emptyIterator() : new IntMap.ValItr<>(map.entries, map);
        }

        @SuppressWarnings("unchecked")
        public final boolean contains(Object o) {
            return map.containsValue((V) o);
        }

        @SuppressWarnings("unchecked")
        public final boolean remove(Object o) {
            Entry<V> entry = map.getValueEntry((V) o);
            return entry != null && map.remove(entry.k) != null;
        }

        public final Spliterator<V> spliterator() {
            return Spliterators.spliterator(iterator(), size(), 0);
        }
    }

    static final class EntrySet<V> extends AbstractSet<Entry<V>> {
        private final IntBiMap<V> map;

        public EntrySet(IntBiMap<V> map) {
            this.map = map;
        }

        public final int size() {
            return map.size();
        }

        public final void clear() {
            map.clear();
        }

        @Nonnull
        public final Iterator<Entry<V>> iterator() {
            return isEmpty() ? Collections.emptyIterator() : Helpers.cast(new EntryItr<>(map.entries, map));
        }

        public final boolean contains(Object o) {
            if (!(o instanceof Entry))
                return false;
            Entry<?> e = (Entry<?>) o;
            int key = e.getKey();
            Entry<?> comp = map.getKeyEntry(key);
            return comp != null && comp.v == e.getValue();
        }

        public final boolean remove(Object o) {
            if (o instanceof Map.Entry) {
                Entry<?> e = (Entry<?>) o;
                return map.remove(e.k) != null;
            }
            return false;
        }

        public final Spliterator<Entry<V>> spliterator() {
            return Spliterators.spliterator(iterator(), size(), 0);
        }
    }
}