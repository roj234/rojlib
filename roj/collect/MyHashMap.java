package roj.collect;

import roj.math.MathUtils;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import static roj.collect.IntMap.MAX_NOT_USING;
import static roj.collect.IntMap.UNDEFINED;

/**
 * @author Roj234
 * @since 2021/6/18 11:5
 * 基于Hash-like机制实现的较高速Map
 */
public class MyHashMap<K, V> extends AbstractMap<K, V> implements FindMap<K, V>, MapLike<MyHashMap.Entry<K, V>> {
	public static class Entry<K, V> implements Map.Entry<K, V>, MapLikeEntry<Entry<K, V>> {
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
			return String.valueOf(k) + '=' + v;
		}
	}

	protected Entry<?, ?>[] entries;
	protected int size = 0;

	protected int length = 1, mask;
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

	public void setLoadFactor(float loadFactor) {
		this.loadFactor = loadFactor;
	}

	public void ensureCapacity(int size) {
		if (size < length) return;
		length = MathUtils.getMin2PowerOf(size);

		if (this.entries != null) resize();
		else this.mask = length-1;
	}

	public Map.Entry<K, V> find(K k) {
		return getEntry(k);
	}

	@Nonnull
	public Set<Map.Entry<K, V>> entrySet() {
		return new EntrySet<>(this);
	}

	public int size() {
		return size;
	}

	@Override
	public void removeEntry0(Entry<K, V> entry) {
		remove(entry.k);
	}

	void afterPut(Entry<K, V> entry) {
	}

	@SuppressWarnings("unchecked")
	public void putAll(MyHashMap<K, V> otherMap) {
		Entry<?, ?>[] ent = otherMap.entries;
		if (ent == null) return;
		for (int i = 0; i < otherMap.length; i++) {
			Entry<K, V> entry = (Entry<K, V>) ent[i];
			if (entry == null) continue;
			while (entry != null) {
				put(entry.k, entry.v);
				entry = entry.next;
			}
		}
	}

	@SuppressWarnings("unchecked")
	protected void resize() {
		Entry<?, ?>[] newEntries = new Entry<?, ?>[length];
		int i = 0, j = entries.length;
		int mask1 = length-1;
		for (; i < j; i++) {
			Entry<K, V> entry = (Entry<K, V>) entries[i];
			while (entry != null) {
				Entry<K, V> next = entry.next;
				int newKey = hash(entry.k)&mask1;
				Entry<K, V> old = (Entry<K, V>) newEntries[newKey];
				newEntries[newKey] = entry;
				entry.next = old;
				entry = next;
			}
		}

		this.entries = newEntries;
		this.mask = mask1;
	}

	public V put(K key, V e) {
		Entry<K, V> entry = getOrCreateEntry(key);
		V old = entry.v;
		if (old == UNDEFINED) {
			afterPut(entry);
			size++;
			entry.v = e;
			return null;
		} else {
			afterAccess(entry, entry.v = e);
		}
		return old;
	}

	@Override
	@SuppressWarnings("unchecked")
	public void putAll(@Nonnull Map<? extends K, ? extends V> map) {
		ensureCapacity(size + map.size());
		if (map instanceof MyHashMap) putAll((MyHashMap<K, V>) map);
		else {
			for (Map.Entry<? extends K, ? extends V> entry : map.entrySet()) {
				put(entry.getKey(), entry.getValue());
			}
		}
	}

	void afterAccess(Entry<K, V> entry, V now) {
	}

	void afterRemove(Entry<K, V> entry) {
	}

	public V remove(Object o) {
		return remove0(o, UNDEFINED);
	}

	@SuppressWarnings("unchecked")
	protected V remove0(Object k, Object v) {
		K id = (K) k;

		Entry<K, V> prevEntry = null;
		Entry<K, V> toRemove = null;
		{
			Entry<K, V> entry = getEntryFirst(id, false);
			while (entry != null) {
				if (eq(k, entry)) {
					toRemove = entry;
					break;
				}
				prevEntry = entry;
				entry = entry.next;
			}
		}

		if (toRemove == null) return null;
		if (v != UNDEFINED && !Objects.equals(v, toRemove.v)) return null;

		afterRemove(toRemove);

		size--;

		if (prevEntry != null) {
			prevEntry.next = toRemove.next;
		} else {
			entries[hash(id)&mask] = toRemove.next;
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
		Entry<?, ?>[] ent = entries;
		if (ent == null) return null;
		for (int i = ent.length - 1; i >= 0; i--) {
			Entry<K, V> entry = (Entry<K, V>) ent[i];
			if (entry == null) continue;
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
			if (eq(id, entry)) return entry;
			entry = entry.next;
		}
		return null;
	}

	protected boolean eq(Object id, Entry<K, V> entry) {
		return id == null ? entry.k == null : id.equals(entry.k);
	}

	public Entry<K, V> getOrCreateEntry(K id) {
		if (size > length * loadFactor) {
			length <<= 1;
			resize();
		}

		Entry<K, V> entry = getEntryFirst(id, true);
		if (entry.v == UNDEFINED) return entry;
		while (true) {
			if (eq(id, entry)) return entry;
			if (entry.next == null) break;
			entry = entry.next;
		}
		Entry<K, V> firstUnused = getCachedEntry(id);
		entry.next = firstUnused;
		return firstUnused;
	}

	protected int hash(K id) {
		int v;
		return id == null ? 0 : ((v = id.hashCode()) ^ (v >>> 16));
	}

	@SuppressWarnings("unchecked")
	protected Entry<K, V> getEntryFirst(K id, boolean create) {
		int i = hash(id)&mask;
		if (entries == null) {
			if (!create) return null;
			entries = new Entry<?, ?>[length];
		}
		Entry<K, V> entry;
		if ((entry = (Entry<K, V>) entries[i]) == null) {
			if (!create) return null;
			entries[i] = entry = getCachedEntry(id);
		}
		return entry;
	}

	protected Entry<K, V> notUsing;
	protected int removedLength;

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
		et.v = (V) UNDEFINED;
		return et;
	}

	protected void putRemovedEntry(Entry<K, V> entry) {
		if (notUsing != null && removedLength > MAX_NOT_USING) {
			return;
		}
		entry.k = null;
		entry.v = Helpers.cast(UNDEFINED);
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

	public void clear() {
		if (size == 0) return;
		size = 0;
		if (entries == null) return;
		if (removedLength < MAX_NOT_USING) {
			for (int i = 0; i < length; i++) {
				if (entries[i] != null) {
					putRemovedEntry(Helpers.cast(entries[i]));
					entries[i] = null;
				}
			}
		} else Arrays.fill(entries, null);
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
	}

	@Override
	public boolean remove(Object key, Object value) {
		int os = size;
		remove0(key, value);
		return os != size;
	}

	@SuppressWarnings("unchecked")
	public void removeIf(Predicate<K> predicate) {
		if (entries == null) return;
		K k;
		for (Entry<?, ?> entry : entries) {
			while (entry != null) {
				if (entry.v != UNDEFINED && predicate.test(k = (K) entry.k)) {
					entry = entry.next;
					remove(k);
					continue;
				}
				entry = entry.next;
			}
		}
	}

	@Override
	public boolean replace(K key, V oldValue, V newValue) {
		Entry<K, V> entry = getEntry(key);
		if (entry == null || entry.v == UNDEFINED) return false;
		if (Objects.equals(oldValue, entry.v)) {
			afterAccess(entry, newValue);
			entry.v = newValue;
			return true;
		}
		return false;
	}

	@Override
	@SuppressWarnings("unchecked")
	public void forEach(BiConsumer<? super K, ? super V> action) {
		Entry<?, ?>[] es = this.entries;
		if (es == null) return;
		for (int i = 0; i < length; i++) {
			Entry<K, V> e = (Entry<K, V>) es[i];
			if (e == null) continue;
			while (e != null) {
				action.accept(e.k, e.v);
				e = e.next;
			}
		}
	}

	@Override
	public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
		Entry<K, V> entry = getEntry(key);
		V newV = remappingFunction.apply(key, entry == null || entry.v == UNDEFINED ? null : entry.v);
		if (newV == null) {
			if (entry != null && entry.v != UNDEFINED) {
				remove(key);
			}
			return null;
		} else if (entry == null) {
			entry = getOrCreateEntry(key);
		}

		if (entry.v == UNDEFINED) {
			size++;
			afterPut(entry);
		}
		entry.v = newV;

		return newV;
	}

	@Override
	public V computeIfAbsent(K key, @Nonnull Function<? super K, ? extends V> mappingFunction) {
		Entry<K, V> entry = getEntry(key);
		if (entry != null && entry.v != UNDEFINED) return entry.v;
		if (entry == null) entry = getOrCreateEntry(key);
		if (entry.v == UNDEFINED) {
			size++;
			afterPut(entry);
		}
		return entry.v = mappingFunction.apply(key);
	}

	@Override
	public V computeIfPresent(K key, @Nonnull BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
		Entry<K, V> entry = getEntry(key);
		if (entry == null || entry.v == UNDEFINED) return null;
		if (entry.v == null) return null; // default implement guarantee
		V newV = remappingFunction.apply(key, entry.v);
		if (newV == null) {
			remove(key);
			return null;
		}

		return entry.v = newV;
	}

	@Override
	@SuppressWarnings("unchecked")
	public V getOrDefault(Object key, V defaultValue) {
		Entry<K, V> entry = getEntry((K) key);
		if (entry == null || entry.v == UNDEFINED) return defaultValue;
		return entry.v;
	}

	@Override
	public V putIfAbsent(K key, V v) {
		Entry<K, V> entry = getOrCreateEntry(key);
		if (entry.v == UNDEFINED) {
			afterPut(entry);
			size++;
			entry.v = v;
			return null;
		}
		return entry.v;
	}

	@Override
	public V replace(K key, V val) {
		Entry<K, V> entry = getEntry(key);
		if (entry == null) return null;

		V v = entry.v;
		if (v == UNDEFINED) v = null;

		entry.v = val;
		return v;
	}
}