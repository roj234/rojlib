/**
 * This file is a part of MI <br>
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究
 * <p>
 * File version : 76
 * Author: R__
 * Filename: MyHashMap.java
 * 基于Hash-like机制实现的较高速Map
 */
package roj.collect;

import roj.math.MathUtils;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

import static roj.collect.IntMap.MAX_NOT_USING;
import static roj.collect.IntMap.NOT_USING;

public class MyHashMap<K, V> implements FindMap<K, V>, CItrMap<MyHashMap.Entry<K, V>> {
    public static class Entry<K, V> implements Map.Entry<K, V>, EntryIterable<Entry<K, V>> {
        public K k;
        public V v;

        public Entry(K k, V v) {
            this.k = k;
            this.v = v;
        }

        public K getKey() {
            return k;
        }

        public V getValue() {
            return v;
        }

        @Override
        public V setValue(V v) {
            V old = this.v;
            this.v = v;
            return old;
        }

        public Entry<K, V> next;

        @Override
        public Entry<K, V> nextEntry() {
            return next;
        }

        @Override
        public String toString() {
            return new StringBuilder().append(k).append('=').append(v).toString();
        }
    }

    protected Entry<?, ?>[] entries;
    protected int size = 0;

    protected int length = 1;
    protected float loadFactor = 0.8f;

    public MyHashMap() {
        this(16);
    }

    public MyHashMap(int size) {
        ensureCapacity(size);
    }

    public MyHashMap(int size, float loadFactor) {
        ensureCapacity(size);
        this.loadFactor = loadFactor;
    }

    public MyHashMap(Map<K, V> map) {
        this.putAll(map);
    }

    public void ensureCapacity(int size) {
        if (size < length) return;
        length = MathUtils.getMin2PowerOf(size);

        if (this.entries != null)
            resize();
    }

    public Map.Entry<K, V> find(K k) {
        return getEntry(k);
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

    @Override
    public void removeEntry0(Entry<K, V> entry) {
        remove(entry.k);
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    void afterPut(Entry<K, V> entry) {
    }

    @SuppressWarnings("unchecked")
    public void putAll(MyHashMap<K, V> otherMap) {
        final Entry<?, ?>[] entries = otherMap.entries;
        if (entries == null)
            return;
        for (int i = 0; i < otherMap.length; i++) {
            Entry<K, V> entry = (Entry<K, V>) entries[i];
            if (entry == null)
                continue;
            while (entry != null) {
                this.put(entry.k, entry.v);
                entry = entry.next;
            }
        }
    }

    @SuppressWarnings("unchecked")
    protected void resize() {
        Entry<?, ?>[] newEntries = new Entry<?, ?>[length];
        Entry<K, V> entry;
        Entry<K, V> next;
        int i = 0, j = entries.length;
        for (; i < j; i++) {
            entry = (Entry<K, V>) entries[i];
            while (entry != null) {
                next = entry.next;
                int newKey = indexFor(entry.k);
                Entry<K, V> old = (Entry<K, V>) newEntries[newKey];
                newEntries[newKey] = entry;
                entry.next = old;
                entry = next;
            }
        }

        this.entries = newEntries;
    }

    public V put(K key, V e) {
        if (size > length * loadFactor) {
            length <<= 1;
            resize();
        }

        Entry<K, V> entry = getOrCreateEntry(key);
        V old = entry.v;
        if (old == NOT_USING) {
            afterPut(entry);
            size++;
            entry.v = e;
            return null;
        }
        afterChange(key, old, entry.v = e, entry);
        return old;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void putAll(@Nonnull Map<? extends K, ? extends V> map) {
        this.ensureCapacity(size + map.size());
        if (map instanceof MyHashMap)
            putAll((MyHashMap<K, V>) map);
        else {
            for (Map.Entry<? extends K, ? extends V> entry : map.entrySet()) {
                this.put(entry.getKey(), entry.getValue());
            }
        }
    }

    void afterChange(K key, V original, V now, Entry<K, V> entry) {
    }

    void afterRemove(Entry<K, V> entry) {
    }

    public V remove(Object o) {
        return remove0(o, NOT_USING);
    }

    @SuppressWarnings("unchecked")
    protected V remove0(Object k, Object v) {
        K id = (K) k;

        Entry<K, V> prevEntry = null;
        Entry<K, V> toRemove = null;
        {
            Entry<K, V> entry = getEntryFirst(id, false);
            while (entry != null) {
                if (Objects.equals(k, entry.k)) {
                    toRemove = entry;
                    break;
                }
                prevEntry = entry;
                entry = entry.next;
            }
        }

        if (toRemove == null)
            return null;
        if(v != NOT_USING && !Objects.equals(v, toRemove.v))
            return null;

        afterRemove(toRemove);

        this.size--;

        if (prevEntry != null) {
            prevEntry.next = toRemove.next;
        } else {
            this.entries[indexFor(id)] = toRemove.next;
        }

        V v1 = toRemove.v;

        putRemovedEntry(toRemove);

        return v1;
    }

    public boolean containsValue(Object e) {
        return getValueEntry(e) != null;
    }

    @SuppressWarnings("unchecked")
    public Entry<K, V> getValueEntry(Object value) {
        if (entries == null) return null;
        for (int i = 0; i < length; i++) {
            Entry<K, V> entry = (Entry<K, V>) entries[i];
            if (entry == null)
                continue;
            while (entry != null) {
                if (Objects.equals(value, entry.v)) {
                    return entry;
                }
                entry = entry.next;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public boolean containsKey(Object i) {
        Entry<K, V> entry = getEntry((K) i);

        return entry != null;
    }

    public Entry<K, V> getEntry(K id) {
        Entry<K, V> entry = getEntryFirst(id, false);
        while (entry != null) {
            if (Objects.equals(entry.k, id)) {
                return entry;
            }
            entry = entry.next;
        }
        return null;
    }

    protected Entry<K, V> getOrCreateEntry(K id) {
        Entry<K, V> entry = getEntryFirst(id, true);
        if (entry.v == NOT_USING)
            return entry;
        while (true) {
            if (Objects.equals(id, entry.k))
                return entry;
            if (entry.next == null)
                break;
            entry = entry.next;
        }
        Entry<K, V> firstUnused = getCachedEntry(id);
        entry.next = firstUnused;
        return firstUnused;
    }

    protected int indexFor(K id) {
        int v;
        return id == null ? 0 : ((v = id.hashCode()) ^ (v >>> 16)) & (length - 1);
    }

    @SuppressWarnings("unchecked")
    protected Entry<K, V> getEntryFirst(K id, boolean create) {
        int i = indexFor(id);
        if (entries == null) {
            if (!create)
                return null;
            entries = new Entry<?, ?>[length];
        }
        Entry<K, V> entry;
        if ((entry = (Entry<K, V>) entries[i]) == null) {
            if (!create)
                return null;
            entries[i] = entry = getCachedEntry(id);
        }
        return entry;
    }

    protected Entry<K, V> notUsing = null;
    protected int removedLength = 0;

    @SuppressWarnings("unchecked")
    protected Entry<K, V> getCachedEntry(K id) {
        Entry<K, V> et = this.notUsing;

        if (et != null) {
            et.k = id;
            this.notUsing = et.next;
            et.next = null;
            removedLength--;
        } else {
            et = createEntry(id);
        }
        et.v = (V) NOT_USING;
        return et;
    }

    protected void putRemovedEntry(Entry<K, V> entry) {
        if (notUsing != null && removedLength > MAX_NOT_USING) {
            return;
        }
        entry.k = null;
        entry.v = null;
        entry.next = notUsing;
        removedLength++;
        notUsing = entry;
    }

    protected Entry<K, V> createEntry(K id) {
        return new Entry<>(id, null);
    }

    @SuppressWarnings("unchecked")
    public V get(Object id) {
        Entry<K, V> entry = getEntry((K) id);
        return entry == null ? null : entry.v;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("MyHashMap").append('{');
        for (Map.Entry<K, V> entry : new EntrySet<>(this)) {
            sb.append(entry.getKey()).append('=').append(entry.getValue()).append(',');
        }
        if (!isEmpty()) {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.append('}').toString();
    }

    public void clear() {
        if (size == 0)
            return;
        size = 0;
        if (entries != null)
            Arrays.fill(entries, null);
    }

    static class KeyItr<K, V> extends MapItr<Entry<K, V>> implements Iterator<K> {
        public KeyItr(EntryIterable<?>[] entries, CItrMap<Entry<K, V>> map) {
            super(entries, map);
        }

        @Override
        public K next() {
            return nextT().k;
        }
    }

    static class ValItr<K, V> extends MapItr<Entry<K, V>> implements Iterator<V> {
        public ValItr(EntryIterable<?>[] entries, CItrMap<Entry<K, V>> map) {
            super(entries, map);
        }

        @Override
        public V next() {
            return nextT().v;
        }
    }

    public Iterator<Entry<K, V>> entryIterator() {
        return null;
    }

    static class KeySet<K, V> extends AbstractSet<K> {
        private final MyHashMap<K, V> map;

        public KeySet(MyHashMap<K, V> map) {
            this.map = map;
        }

        public final int size() {
            return map.size();
        }

        public final void clear() {
            map.clear();
        }

        public final Iterator<K> iterator() {
            return isEmpty() ? Collections.emptyIterator() : new KeyItr<>(map.entries, map);
        }

        public final boolean contains(Object o) {
            return map.containsKey(o);
        }

        public final boolean remove(Object o) {
            return map.remove(o) != null;
        }
    }

    static class Values<K, V> extends AbstractCollection<V> {
        private final MyHashMap<K, V> map;

        public Values(MyHashMap<K, V> map) {
            this.map = map;
        }

        public final int size() {
            return map.size();
        }

        public final void clear() {
            map.clear();
        }

        public final Iterator<V> iterator() {
            return isEmpty() ? Collections.emptyIterator() : new ValItr<>(map.entries, map);
        }

        public final boolean contains(Object o) {
            return map.containsValue(o);
        }

        public final boolean remove(Object o) {
            Entry<K, V> entry = map.getValueEntry(o);
            return entry != null && map.remove(entry.k) != null;
        }

        public final Spliterator<V> spliterator() {
            return Spliterators.spliterator(iterator(), size(), 0);
        }
    }

    static class EntrySet<K, V> extends AbstractSet<Map.Entry<K, V>> {
        private final MyHashMap<K, V> map;

        public EntrySet(MyHashMap<K, V> map) {
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
            if (!(o instanceof MyHashMap.Entry)) return false;
            MyHashMap.Entry<?, ?> e = (MyHashMap.Entry<?, ?>) o;
            Object key = e.getKey();
            MyHashMap.Entry<?, ?> comp = map.getEntry((K) key);
            return comp != null && comp.v == e.v;
        }

        public final boolean remove(Object o) {
            if (o instanceof Map.Entry) {
                MyHashMap.Entry<?, ?> e = (MyHashMap.Entry<?, ?>) o;
                return map.remove(e.k) != null;
            }
            return false;
        }

        public final Spliterator<Map.Entry<K, V>> spliterator() {
            return Spliterators.spliterator(iterator(), size(), 0);
        }
    }
    
    @Override
    public boolean remove(Object key, Object value) {
        int os = size;
        remove0(key, value);
        return os != size;
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        Entry<K, V> entry = getEntry(key);
        if(entry == null || entry.v == NOT_USING)
            return false;
        if(Objects.equals(oldValue, entry.v)) {
            entry.v = newValue;
            return true;
        }
        return false;
    }

    @Override
    public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        Entry<K, V> entry = getEntry(key);
        V newV = remappingFunction.apply(key, entry == null || entry.v == NOT_USING ? null : entry.v);
        if(newV == null) {
            if(entry != null && entry.v != NOT_USING) {
                remove(key);
            }
            return null;
        } else if(entry == null) {
            entry = getOrCreateEntry(key);
        }

        if(entry.v == NOT_USING)
            size++;
        entry.v = newV;

        return newV;
    }

    @Override
    public V computeIfAbsent(K key, @Nonnull Function<? super K, ? extends V> mappingFunction) {
        Entry<K, V> entry = getEntry(key);
        if(entry != null && entry.v != NOT_USING)
            return entry.v;
        if(entry == null) {
            entry = getOrCreateEntry(key);
        }
        if(entry.v == NOT_USING)
            size++;
        return entry.v = mappingFunction.apply(key);
    }

    @Override
    public V computeIfPresent(K key, @Nonnull BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        Entry<K, V> entry = getEntry(key);
        if(entry == null || entry.v == NOT_USING)
            return null;
        if(entry.v == null)
            return null; // default implement guarantee
        V newV = remappingFunction.apply(key, entry.v);
        if(newV == null) {
            remove(key);
            return null;
        }

        return entry.v = newV;
    }

    @Override
    @SuppressWarnings("unchecked")
    public V getOrDefault(Object key, V defaultValue) {
        K cs = (K) key;
        Entry<K, V> entry = getEntry(cs);
        if(entry == null || entry.v == NOT_USING)
            return defaultValue;
        return entry.v;
    }

    @Override
    public V putIfAbsent(K key, V v) {
        Entry<K, V> entry = getOrCreateEntry(key);
        if(entry.v == NOT_USING) {
            size++;
            entry.v = v;
            return null;
        }
        return entry.v;
    }

    @Override
    public V replace(K key, V val) {
        Entry<K, V> entry = getEntry(key);
        if(entry == null)
            return null;

        V v = entry.v;
        if(v == NOT_USING)
            v = null;

        entry.v = val;
        return v;
    }
}