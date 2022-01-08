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
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.*;

import static roj.collect.IntMap.NOT_USING;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/18 10:35
 */
public class HashBiMap<K, V> implements Flippable<K, V>, CItrMap<MyHashMap.Entry<K, V>> {
    public static class Entry<K, V> extends MyHashMap.Entry<K, V> {
        protected Entry(K k, V v) {
            super(k, v);
        }

        public V setValue(V now) {
            throw new UnsupportedOperationException();
        }

        protected Entry<K, V> valueNext;

        @Override
        public Entry<K, V> nextEntry() {
            return (Entry<K, V>) next;
        }
    }

    static final class Inverse<V, K> implements Map<V, K>, Flippable<V, K> {
        private final HashBiMap<K, V> parent;

        private Inverse(HashBiMap<K, V> parent) {
            this.parent = parent;
        }

        public int size() {
            return parent.size();
        }

        public boolean isEmpty() {
            return parent.isEmpty();
        }

        public boolean containsKey(Object key) {
            return parent.containsValue(key);
        }

        public boolean containsValue(Object value) {
            return parent.containsKey(value);
        }

        @SuppressWarnings("unchecked")
        public K get(Object key) {
            return parent.getByValue((V) key);
        }

        public K put(V key, K value) {
            return parent.putByValue(key, value);
        }

        @Override
        public void putAll(@Nonnull Map<? extends V, ? extends K> map) {
            for (Map.Entry<? extends V, ? extends K> entry : map.entrySet()) {
                parent.putByValue(entry.getKey(), entry.getValue());
            }
        }

        public K forcePut(V key, K value) {
            return parent.forcePutByValue(key, value);
        }

        @SuppressWarnings("unchecked")
        public K remove(Object key) {
            return parent.removeByValue((V) key);
        }

        public void clear() {
            parent.clear();
        }

        @Nonnull
        public Set<V> keySet() {
            return new Values<>(parent);
        }

        @Nonnull
        public Collection<K> values() {
            return new KeySet<>(parent);
        }

        @Nonnull
        public Set<Entry<V, K>> entrySet() {
            return new EntrySet<>(this.parent);
        }

        static class EntrySet<V, K> extends AbstractSet<Map.Entry<V, K>> {
            private final HashBiMap<K, V> map;

            public EntrySet(HashBiMap<K, V> map) {
                this.map = map;
            }

            public final int size() {
                return map.size();
            }

            public final void clear() {
                map.clear();
            }

            @Nonnull
            public final Iterator<Map.Entry<V, K>> iterator() {
                if (isEmpty()) { return Collections.emptyIterator(); }
                EntryItr<MyHashMap.Entry<K, V>> dlg = new EntryItr<>(map.entries, map);
                return new AbstractIterator<Entry<V,K>>() {

                    @Override
                    public boolean computeNext() {
                        boolean next = dlg.hasNext();
                        if (next) {
                            MyHashMap.Entry<K, V> t = dlg.nextT();
                            result = new SimpleImmutableEntry<>(t.v, t.k);
                        }
                        return next;
                    }
                };
            }

            @SuppressWarnings("unchecked")
            public final boolean contains(Object o) {
                if (!(o instanceof Map.Entry))
                    return false;
                Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
                Object key = e.getKey();
                HashBiMap.Entry<?, ?> comp = map.getValueEntry((V) key);
                return comp != null && comp.v == e.getValue();
            }

            public final boolean remove(Object o) {
                if (o instanceof Map.Entry) {
                    HashBiMap.Entry<?, ?> e = (HashBiMap.Entry<?, ?>) o;
                    return map.remove(e.v) != null;
                }
                return false;
            }

            public final Spliterator<Map.Entry<V, K>> spliterator() {
                return Spliterators.spliterator(iterator(), size(), 0);
            }
        }

        public HashBiMap<K, V> flip() {
            return parent;
        }
    }

    protected Entry<?, ?>[] entries;
    protected Entry<?, ?>[] valueEntries;

    protected int size = 0;

    int length = 2, mask = 1;

    float loadFactor = 0.8f;

    boolean unmodifiable = false;

    private final Inverse<V, K> inverse = new Inverse<>(this);

    public HashBiMap() {
        this(16);
    }

    public HashBiMap(int size) {
        ensureCapacity(size);
    }

    public HashBiMap(int size, float loadFactor) {
        ensureCapacity(size);
        this.loadFactor = loadFactor;
    }

    public HashBiMap(Map<K, V> map) {
        ensureCapacity(map.size());
        putAll(map);
    }

    public void ensureCapacity(int size) {
        if (size < length) return;
        length = MathUtils.getMin2PowerOf(size);
        mask = length - 1;

        resize();
    }

    public Flippable<V, K> flip() {
        return this.inverse;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public void unmodifiable() {
        this.unmodifiable = true;
    }

    @Nonnull
    public Set<Map.Entry<K, V>> entrySet() {
        return new EntrySet<>(this);
    }

    @Nonnull
    public Collection<V> values() {
        return new Values<>(this);
    }

    @Nonnull
    public Set<K> keySet() {
        return new KeySet<>(this);
    }

    public int size() {
        return size;
    }

    @SuppressWarnings("unchecked")
    protected void resize() {
        if(valueEntries != null && entries != null) {
            Entry<?, ?>[] newEntries = new Entry<?, ?>[length];
            Entry<?, ?>[] newValues = new Entry<?, ?>[length];

            Entry<K, V> entry;
            Entry<K, V> next;
            int i = 0, j = entries.length;

            for (; i < j; i++) {
                entry = (Entry<K, V>) entries[i];
                while (entry != null) {
                    next = entry.nextEntry();
                    int newIndex = indexFor(entry.k);
                    Entry<K, V> old = (Entry<K, V>) newEntries[newIndex];
                    newEntries[newIndex] = entry;
                    entry.next = old;
                    entry = next;
                }

                entry = (Entry<K, V>) valueEntries[i];
                while (entry != null) {
                    next = entry.valueNext;

                    int newIndex = indexFor(entry.v);
                    Entry<K, V> old = (Entry<K, V>) newValues[newIndex];
                    newValues[newIndex] = entry;
                    entry.valueNext = old;

                    entry = next;
                }
            }
            this.valueEntries = newValues;
            this.entries = newEntries;

        } else if(valueEntries != entries)
            throw new Error();
    }

    protected int indexFor(Object v) {
        int h = v.hashCode();
        return (h ^ (h >>> 16)) & mask;
    }

    public V put(K key, V e) {
        return put0(key, e, false);
    }

    public V forcePut(K key, V e) {
        return put0(key, e, true);
    }

    public K putByValue(V e, K key) {
        return putByValue0(e, key, false);
    }

    public K forcePutByValue(V e, K key) {
        return putByValue0(e, key, true);
    }

    private K putByValue0(V v, K key, boolean replace) {
        if (unmodifiable)
            throw new IllegalStateException("Try to modify an unmodifiable map");
        if (size > length * loadFactor) {
            length <<= 1;
            mask = length - 1;
            resize();
        }

        Entry<K, V> keyEntry = getKeyEntry(key);
        Entry<K, V> valueEntry = getValueEntry(v);

        if (keyEntry != null) {
            if (keyEntry == valueEntry) {
                return key;
            }

            if (valueEntry != null) { // value 替换key
                removeEntry(keyEntry);

                removeKeyEntry(valueEntry, valueEntry.k);

                K old = valueEntry.k;
                valueEntry.k = key;

                putKeyEntry(valueEntry);

                return old;
            } else {
                if (!replace)
                    throw new IllegalArgumentException("Multiple value(" + v + ", " + keyEntry.v + ") bind to same key(" + keyEntry.k + ")! use forcePut()!");

                removeValueEntry(keyEntry, keyEntry.v);

                keyEntry.v = v;

                putValueEntry(keyEntry);

                return key;
            }
        } else {
            if (valueEntry != null) {

                // key没找到, 找到value
                K oldKey = valueEntry.k;
                removeKeyEntry(valueEntry, oldKey);

                valueEntry.k = key;

                putKeyEntry(valueEntry);

                return oldKey;
            } else {
                // 全为空
                putValueEntry(createEntry(key, v));

                return null;
            }
        }
    }

    private V put0(K key, V v, boolean replace) {
        if (unmodifiable)
            throw new IllegalStateException("Try to modify an unmodifiable map");
        if (size > length * loadFactor) {
            length <<= 1;
            mask = length - 1;
            resize();
        }

        Entry<K, V> keyEntry = getKeyEntry(key);
        Entry<K, V> valueEntry = getValueEntry(v);

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
                putValueEntry(createEntry(key, v));
                return null;
            }
        }
    }

    @SuppressWarnings("unchecked")
    public V remove(Object id) {
        if (unmodifiable)
            throw new IllegalStateException("Try to modify an unmodifiable map");
        Entry<K, V> entry = getKeyEntry((K) id);
        if (entry == null)
            return null;
        removeEntry(entry);
        return entry.v;
    }

    @Override
    public void putAll(@Nonnull Map<? extends K, ? extends V> map) {
        if (unmodifiable)
            throw new IllegalStateException("Try to modify an unmodifiable map");
        for (Map.Entry<? extends K, ? extends V> entry : map.entrySet()) {
            this.put(entry.getKey(), entry.getValue());
        }
    }

    public K removeByValue(V v) {
        if (unmodifiable)
            throw new IllegalStateException("Try to modify an unmodifiable map");
        Entry<K, V> entry = getValueEntry(v);
        if (entry == null)
            return null;
        removeEntry(entry);
        return entry.k;
    }

    public K getByValue(V key) {
        Entry<K, V> entry = getValueEntry(key);
        return entry == null ? null : entry.k;
    }

    @SuppressWarnings("unchecked")
    public V get(Object id) {
        Entry<K, V> entry = getKeyEntry((K) id);
        return entry == null ? null : entry.v;
    }

    @SuppressWarnings("unchecked")
    public boolean containsKey(Object i) {
        return getKeyEntry((K) i) != null;
    }

    @SuppressWarnings("unchecked")
    public boolean containsValue(Object v) {
        return getValueEntry((V) v) != null;
    }

    public void modifiable() {
        this.unmodifiable = false;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("HashBiMap").append('{');
        for (Map.Entry<K, V> entry : new EntrySet<>(this)) {
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

    public void removeEntry0(MyHashMap.Entry<K, V> entry) {
        removeEntry((Entry<K, V>) entry);
    }

    protected void removeEntry(Entry<K, V> toRemove) {
        removeKeyEntry(toRemove, toRemove.k);
        removeValueEntry(toRemove, toRemove.v);
        this.size--;
    }

    @SuppressWarnings("unchecked")
    protected boolean removeKeyEntry(Entry<K, V> entry, K k) {
        int index = k == null ? 0 : indexFor(k);
        if (entries == null)
            return false;
        Entry<K, V> currentEntry;
        Entry<K, V> prevEntry;
        if ((currentEntry = (Entry<K, V>) entries[index]) == null) {
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
    protected boolean removeValueEntry(Entry<K, V> entry, V v) {
        int index = indexFor(v);
        if (valueEntries == null)
            return false;
        Entry<K, V> currentEntry;
        Entry<K, V> prevEntry;
        if ((currentEntry = (Entry<K, V>) valueEntries[index]) == null) {
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
    protected void putValueEntry(Entry<K, V> entry) {
        int index = indexFor(entry.v);
        if (valueEntries == null)
            valueEntries = new Entry<?, ?>[length];
        Entry<K, V> currentEntry;
        if ((currentEntry = (Entry<K, V>) valueEntries[index]) == null) {
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
    protected void putKeyEntry(Entry<K, V> entry) {
        int index = indexFor(entry.k);
        if (entries == null)
            entries = new Entry<?, ?>[length];
        Entry<K, V> currentEntry;
        if ((currentEntry = (Entry<K, V>) entries[index]) == null) {
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
    protected Entry<K, V> getValueEntry(V v) {
        if (valueEntries == null)
            return null;
        int id = indexFor(v);
        Entry<K, V> entry = (Entry<K, V>) valueEntries[id];
        while (entry != null) {
            if (Objects.equals(v, entry.v)) {
                return entry;
            }
            entry = entry.valueNext;
        }
        return null;
    }

    protected Entry<K, V> getKeyEntry(K k) {
        Entry<K, V> entry = getEntryFirst(k, false);
        if (entry == null) return null;
        while (entry != null) {
            if (Objects.equals(k, entry.k))
                return entry;
            entry = entry.nextEntry();
        }
        return null;
    }

    protected Entry<K, V> createEntry(K id, V v) {
        Entry<K, V> entry = getEntryFirst(id, true);
        size++;
        if (entry.v == NOT_USING) {
            entry.v = v;
            return entry;
        }
        while (entry.next != null) {
            entry = entry.nextEntry();
        }
        Entry<K, V> subEntry = new Entry<>(id, v);
        entry.next = subEntry;
        return subEntry;
    }

    @SuppressWarnings("unchecked")
    protected Entry<K, V> getEntryFirst(K o, boolean create) {
        int id = o == null ? 0 : indexFor(o);
        if (entries == null) {
            if(!create)
                return null;
            entries = new Entry<?, ?>[length];
        }
        Entry<K, V> entry;
        if ((entry = (Entry<K, V>) entries[id]) == null) {
            if (!create)
                return null;
            entries[id] = entry = new Entry<>(o, (V) NOT_USING);
        }
        return entry;
    }

    static class KeySet<K, V> extends AbstractSet<K> {
        private final HashBiMap<K, V> map;

        public KeySet(HashBiMap<K, V> map) {
            this.map = map;
        }

        public final int size() {
            return map.size();
        }

        public final void clear() {
            map.clear();
        }

        public final Iterator<K> iterator() {
            return isEmpty() ? Collections.emptyIterator() : new MyHashMap.KeyItr<>(map.entries, map);
        }

        public final boolean contains(Object o) {
            return map.containsKey(o);
        }

        public final boolean remove(Object o) {
            return map.remove(o) != null;
        }
    }

    static class Values<K, V> extends AbstractSet<V> {
        private final HashBiMap<K, V> map;

        public Values(HashBiMap<K, V> map) {
            this.map = map;
        }

        public final int size() {
            return map.size();
        }

        public final void clear() {
            map.clear();
        }

        public final Iterator<V> iterator() {
            return isEmpty() ? Collections.emptyIterator() : new MyHashMap.ValItr<>(map.entries, map);
        }

        public final boolean contains(Object o) {
            return map.containsValue(o);
        }

        @SuppressWarnings("unchecked")
        public final boolean remove(Object o) {
            Entry<K, V> entry = map.getValueEntry((V) o);
            return entry != null && map.remove(entry.k) != null;
        }

        public final Spliterator<V> spliterator() {
            return Spliterators.spliterator(iterator(), size(), 0);
        }
    }

    static class EntrySet<K, V> extends AbstractSet<Map.Entry<K, V>> {
        private final HashBiMap<K, V> map;

        public EntrySet(HashBiMap<K, V> map) {
            this.map = map;
        }

        public final int size() {
            return map.size();
        }

        public final void clear() {
            map.clear();
        }

        @Nonnull
        public final Iterator<Map.Entry<K, V>> iterator() {
            return isEmpty() ? Collections.emptyIterator() : Helpers.cast(new EntryItr<>(map.entries, map));
        }

        @SuppressWarnings("unchecked")
        public final boolean contains(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
            Object key = e.getKey();
            Entry<?, ?> comp = map.getKeyEntry((K) key);
            return comp != null && comp.v == e.getValue();
        }

        public final boolean remove(Object o) {
            if (o instanceof Map.Entry) {
                Entry<?, ?> e = (Entry<?, ?>) o;
                return map.remove(e.k) != null;
            }
            return false;
        }

        public final Spliterator<Map.Entry<K, V>> spliterator() {
            return Spliterators.spliterator(iterator(), size(), 0);
        }
    }
}