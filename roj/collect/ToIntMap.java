package roj.collect;

import roj.math.MathUtils;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.ToIntFunction;

import static roj.collect.IntMap.MAX_NOT_USING;
import static roj.collect.IntMap.UNDEFINED;

public class ToIntMap<K> extends AbstractMap<K, Integer> implements MapLike<ToIntMap.Entry<K>>, ToIntFunction<K>, IIntMap<K> {
	@Override
	public int applyAsInt(K value) {
		return getOrDefault(value, -1);
	}

	public int getInt(K key) {
		return getOrDefault(key, 0);
	}

	@SuppressWarnings("unchecked")
	public int getOrDefault(Object key, int l) {
		Entry<K> entry = getEntry((K) key);
		return entry == null ? l : entry.v;
	}

	public int increase(K key, int i) {
		Entry<K> entry = getOrCreateEntry(key);
		if (entry.k == UNDEFINED) {
			entry.k = key;
			if (++size > length * loadFactor) {
				length <<= 1;
				resize();
			}
		}
		return entry.v += i;
	}

	public static class Entry<K> implements MapLikeEntry<Entry<K>>, Map.Entry<K, Integer> {
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

		@Override
		public String toString() {
			return k+"="+v;
		}
	}

	protected Entry<?>[] entries;
	protected int size = 0;

	int length = 2;
	protected int mask = 1;

	float loadFactor = 0.8f;

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

		if (this.entries != null) resize();
	}

	public EntrySet<K> selfEntrySet() {
		return new EntrySet<>(this);
	}

	@Nonnull
	public Set<Map.Entry<K, Integer>> entrySet() {
		return Helpers.cast(new EntrySet<>(this));
	}

	public int size() {
		return size;
	}

	@Override
	public void removeEntry0(Entry<K> entry) {
		remove(entry.k);
	}

	@SuppressWarnings("unchecked")
	public void putAll(@Nonnull Map<? extends K, ? extends Integer> otherMap) {
		if (otherMap instanceof ToIntMap) {
			putAll((ToIntMap<K>) otherMap);
		} else {
			super.putAll(otherMap);
		}
	}

	@SuppressWarnings("unchecked")
	public void putAll(ToIntMap<K> otherMap) {
		for (int i = 0; i < otherMap.length; i++) {
			Entry<K> entry = (Entry<K>) otherMap.entries[i];
			if (entry == null) continue;
			while (entry != null) {
				this.putInt(entry.k, entry.v);
				entry = entry.next;
			}
		}
	}

	@SuppressWarnings("unchecked")
	protected void resize() {
		Entry<?>[] newEntries = new Entry<?>[length];
		int newMask=length-1;
		Entry<K> entry,next;
		int i = 0, j = entries.length;
		for (; i < j; i++) {
			entry = (Entry<K>) entries[i];
			entries[i] = null;
			while (entry != null) {
				next = entry.next;
				int newKey = indexFor(entry.k)&newMask;
				Entry<K> old = (Entry<K>) newEntries[newKey];
				newEntries[newKey] = entry;
				entry.next = old;
				entry = next;
			}
		}

		entries = newEntries;
		mask = length-1;
	}

	@Deprecated
	public Integer put(K key, Integer e) {
		return putInt(key, e);
	}

	public Integer putInt(K key, int e) {
		Entry<K> entry = getOrCreateEntry(key);
		Integer oldValue = entry.v;
		if (entry.k == UNDEFINED) {
			oldValue = null;
			entry.k = key;

			if (++size > length * loadFactor) {
				length <<= 1;
				resize();
			}
		}
		entry.v = e;
		return oldValue;
	}

	public boolean putIntIfAbsent(K key, int e) {
		Entry<K> entry = getOrCreateEntry(key);
		if (entry.k == UNDEFINED) {
			entry.k = key;
			entry.v = e;
			if (++size > length * loadFactor) {
				length <<= 1;
				resize();
			}
			return true;
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	public Integer remove(Object o) {
		return getEntry((K) o) == null ? null : removeInt(o);
	}

	@SuppressWarnings("unchecked")
	public int removeInt(Object o) {
		K id = (K) o;
		Entry<K> prevEntry = null;
		Entry<K> toRemove = null;
		{
			Entry<K> entry = getEntryFirst(id, false);
			while (entry != null) {
				if (equals(id, entry.k)) {
					toRemove = entry;
					break;
				}
				prevEntry = entry;
				entry = entry.next;
			}
		}

		if (toRemove == null) return -1;

		this.size--;

		if (prevEntry != null) {
			prevEntry.next = toRemove.next;
		} else {
			entries[indexFor(id)&mask] = toRemove.next;
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

	private Entry<K> notUsing = null;

	private Entry<K> getCachedEntry(K id, int value) {
		Entry<K> cached = this.notUsing;
		if (cached != null) {
			cached.k = id;
			cached.v = value;
			this.notUsing = cached.next;
			cached.next = null;
			return cached;
		}

		return newEntry(id, value);
	}

	protected Entry<K> newEntry(K id, int value) {
		return new Entry<>(id, value);
	}

	private void putRemovedEntry(Entry<K> entry) {
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
			if (equals(id, entry.k)) return entry;
			entry = entry.next;
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	protected Entry<K> getOrCreateEntry(K id) {
		Entry<K> entry = getEntryFirst(id, true);
		if (entry.k == UNDEFINED) return entry;
		while (true) {
			if (equals(id, entry.k)) return entry;
			if (entry.next == null) break;
			entry = entry.next;
		}
		Entry<K> firstUnused = getCachedEntry((K) UNDEFINED, 0);
		entry.next = firstUnused;
		return firstUnused;
	}

	protected int indexFor(K id) {
		int v;
		return id == null ? 0 : ((v = id.hashCode()) ^ (v >>> 16));
	}
	protected boolean equals(K in, K entry) {
		return in == null ? entry == null : in.equals(entry);
	}

	@SuppressWarnings("unchecked")
	protected Entry<K> getEntryFirst(K id, boolean create) {
		int i = indexFor(id)&mask;
		if (entries == null) {
			if (!create) return null;
			entries = new Entry<?>[length];
		}
		Entry<K> entry;
		if ((entry = (Entry<K>) entries[i]) == null) {
			if (!create) return null;
			entries[i] = entry = getCachedEntry((K) UNDEFINED, 0);
		}
		return entry;
	}

	@SuppressWarnings("unchecked")
	@Deprecated
	public Integer get(Object id) {
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