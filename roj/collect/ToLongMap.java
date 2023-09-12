package roj.collect;

import roj.math.MathUtils;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.ToLongFunction;

import static roj.collect.IntMap.MAX_NOT_USING;
import static roj.collect.IntMap.UNDEFINED;

/**
 * @author Roj234
 * @since 2021/6/18 11:6
 */
public class ToLongMap<K> extends AbstractMap<K, Long> implements _Generic_Map<ToLongMap.Entry<K>>, ToLongFunction<K> {
	@Override
	public long applyAsLong(K value) {
		return getOrDefault(value, -1L);
	}

	public long getLong(K key) {
		return getOrDefault(key, 0L);
	}

	public long getOrDefault(K key, long l) {
		Entry<K> entry = getEntry(key);
		return entry == null ? l : entry.v;
	}

	public static class Entry<K> implements _Generic_Entry<Entry<K>>, Map.Entry<K, Long> {
		public K k;
		public long v;

		protected Entry(K k, long v) {
			this.k = k;
			this.v = v;
		}

		public K getKey() {
			return k;
		}

		@Override
		public Long getValue() {
			return this.v;
		}

		@Override
		public Long setValue(Long value) {
			long ov = v;
			v = value;
			return ov;
		}

		public long getLong() {
			return v;
		}

		public long setLong(long v) {
			long old = this.v;
			this.v = v;
			return old;
		}

		public Entry<K> next;

		@Override
		public Entry<K> __next() {
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

	public ToLongMap() {
		this(16);
	}

	public ToLongMap(int size) {
		ensureCapacity(size);
	}

	public ToLongMap(int size, float loadFactor) {
		ensureCapacity(size);
		this.loadFactor = loadFactor;
	}

	public ToLongMap(ToLongMap<K> map) {
		this.putAll(map);
	}

	public void ensureCapacity(int size) {
		if (size < length) return;
		length = MathUtils.getMin2PowerOf(size);
		mask = length - 1;

		if (this.entries != null) resize();
	}

	public int size() { return size; }

	public Set<Map.Entry<K, Long>> entrySet() { return Helpers.cast(selfEntrySet()); }
	public Set<Entry<K>> selfEntrySet() { return _Generic_EntrySet.create(this); }
	public _Generic_Entry<?>[] __entries() { return entries; }
	public void __remove(Entry<K> entry) { remove(entry.k); }

	@SuppressWarnings("unchecked")
	public void putAll(@Nonnull Map<? extends K, ? extends Long> otherMap) {
		if (otherMap instanceof ToLongMap) {
			putAll((ToLongMap<K>) otherMap);
		} else {
			super.putAll(otherMap);
		}
	}

	@SuppressWarnings("unchecked")
	public void putAll(ToLongMap<K> otherMap) {
		for (int i = 0; i < otherMap.length; i++) {
			Entry<K> entry = (Entry<K>) otherMap.entries[i];
			if (entry == null) continue;
			while (entry != null) {
				this.putLong(entry.k, entry.v);
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
	public Long put(K key, Long value) {
		return putLong(key, value);
	}

	public Long putLong(K key, long e) {
		if (size > length * loadFactor) {
			length <<= 1;
			mask = length - 1;
			resize();
		}

		Entry<K> entry = getOrCreateEntry(key);
		Long oldValue = entry.v;
		if (entry.k == UNDEFINED) {
			oldValue = null;
			entry.k = key;
			afterPut(key, e);
			size++;
		}
		afterChange(key, oldValue, entry.v = e);
		return oldValue;
	}

	void afterPut(K key, long val) {}
	void afterChange(K key, Long original, long now) {}
	void afterRemove(Entry<K> entry) {}

	@SuppressWarnings("unchecked")
	public Long remove(Object o) {
		K id = (K) o;
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

		if (toRemove == null) return null;

		afterRemove(toRemove);

		this.size--;

		if (prevEntry != null) {
			prevEntry.next = toRemove.next;
		} else {
			this.entries[indexFor(id)] = toRemove.next;
		}

		long v = toRemove.v;

		putRemovedEntry(toRemove);

		return v;
	}

	public boolean containsValue(Object e) {
		return containsLongValue((Long) e);
	}

	public boolean containsLongValue(long e) {
		return getValueEntry(e) != null;
	}

	protected Entry<K> notUsing = null;

	protected Entry<K> getCachedEntry(K id, long value) {
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
	protected Entry<K> getValueEntry(long value) {
		if (entries == null) return null;
		for (int i = 0; i < length; i++) {
			Entry<K> entry = (Entry<K>) entries[i];
			if (entry == null) continue;
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
		return getEntry((K) i) != null;
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
		if (entry.k == UNDEFINED) return entry;
		while (true) {
			if (Objects.equals(id, entry.k)) return entry;
			if (entry.next == null) break;
			entry = entry.next;
		}
		Entry<K> firstUnused = getCachedEntry((K) UNDEFINED, 0L);
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
			if (!create) return null;
			entries = new Entry<?>[length];
		}
		Entry<K> entry;
		if ((entry = (Entry<K>) entries[i]) == null) {
			if (!create) return null;
			entries[i] = entry = getCachedEntry((K) UNDEFINED, 0L);
		}
		return entry;
	}

	@SuppressWarnings("unchecked")
	public Long get(Object id) {
		Entry<K> entry = getEntry((K) id);
		return entry == null ? null : entry.v;
	}

	public void clear() {
		if (size == 0) return;
		size = 0;
		if (entries != null) {
			if (notUsing == null || notUsing.v < MAX_NOT_USING) {
				for (int i = 0; i < length; i++) {
					if (entries[i] != null) {
						putRemovedEntry(Helpers.cast(entries[i]));
						entries[i] = null;
					}
				}
			} else {Arrays.fill(entries, null);}
		}
	}
}