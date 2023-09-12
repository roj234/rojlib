package roj.collect;

import roj.math.MathUtils;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class IntMap<V> extends AbstractMap<Integer, V> implements _Generic_Map<IntMap.Entry<V>>, IIntMap<V> {
	public static final Object UNDEFINED = new Object() {
		public String toString() { return "roj.collect.UNDEFINED"; }
		public boolean equals(Object obj) { return obj == UNDEFINED; }
		public int hashCode() { return 0; }
	};
	public static final int MAX_NOT_USING = 5;

	@SuppressWarnings("unchecked")
	public void putAll(IntMap<? extends V> map) {
		if (map.entries == null) return;
		this.ensureCapacity(size + map.size());
		for (int i = 0; i < map.length; i++) {
			Entry<?> entry = map.entries[i];
			while (entry != null) {
				putInt(entry.k, (V) entry.v);

				entry = entry.next;
			}
		}
	}

	public V getOrDefault(int key, V def) {
		Entry<V> entry = getEntry(key);
		return entry == null ? def : entry.v;
	}

	public static class Entry<V> implements _Generic_Entry<Entry<V>>, Map.Entry<Integer, V> {
		protected int k;
		protected V v;

		public Entry(int k, V v) {
			this.k = k;
			this.v = v;
		}

		@Override
		@Deprecated
		public Integer getKey() {
			return k;
		}

		public int getIntKey() {
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
		public Entry<V> __next() {
			return next;
		}

		@Override
		public String toString() {
			return k + "=" + v + '\n';
		}
	}

	protected Entry<?>[] entries;
	protected int size = 0;

	protected Entry<V> notUsing = null;

	int length = 2;
	float loadFactor = 0.8f;

	public IntMap() {
		this(16);
	}

	public IntMap(int size) {
		ensureCapacity(size);
	}

	public IntMap(int size, float loadFactor) {
		ensureCapacity(size);
		this.loadFactor = loadFactor;
	}

	@SuppressWarnings("unchecked")
	public IntMap(IntMap<? extends V> map) {
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
		if (entry == null) return null;
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

		if (this.entries != null) resize();
	}

	public int size() { return size; }

	public Set<Entry<V>> selfEntrySet() { return _Generic_EntrySet.create(this); }
	public Set<Map.Entry<Integer, V>> entrySet() { return Helpers.cast(selfEntrySet()); }

	public _Generic_Entry<?>[] __entries() { return entries; }
	public void __remove(Entry<V> vEntry) { remove(vEntry.k); }

	public V computeIfAbsentInt(int k, @Nonnull IntFunction<V> fn) {
		V v = get(k);
		if (v == null && !containsKey(k)) {
			putInt(k, v = fn.apply(k));
		}
		return v;
	}
	public V computeIfAbsentIntS(int k, @Nonnull Supplier<V> supplier) {
		V v = get(k);
		if (v == null && !containsKey(k)) {
			putInt(k, v = supplier.get());
		}
		return v;
	}

	public V remove(Object key) { return remove((int) key); }
	public V get(Object key) { return get((int) key); }
	public boolean containsKey(Object key) { return containsKey((int) key); }
	public V put(Integer key, V value) { return putInt(key, value); }
	public V getOrDefault(Object key, V def) { return getOrDefault((int) key, def); }

	@SuppressWarnings("unchecked")
	protected void resize() {
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

	public V putInt(int key, V e) {
		if (size > length * loadFactor) {
			length <<= 1;
			resize();
		}

		Entry<V> entry = getOrCreateEntry(key);
		V oldV = entry.setValue(e);
		if (oldV == UNDEFINED) {
			oldV = null;
			afterPut(entry);
		}
		return oldV;
	}

	void afterPut(Entry<V> entry) {}
	void afterRemove(Entry<V> entry) {}

	public V remove(int id) {
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

		if (toRemove == null) return null;

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

	public boolean containsKey(int i) {
		return getEntry(i) != null;
	}

	@SuppressWarnings("unchecked")
	Entry<V> getEntry(V v) {
		if (entries == null) return null;
		for (int i = 0; i < length; i++) {
			Entry<V> entry = (Entry<V>) entries[i];
			if (entry == null) continue;
			while (entry != null) {
				if (Objects.equals(v, entry.getValue())) {
					return entry;
				}
				entry = entry.next;
			}
		}
		return null;
	}

	public Entry<V> getEntry(int id) {
		Entry<V> entry = getEntryFirst(id, false);
		while (entry != null) {
			if (entry.k == id) return entry;
			entry = entry.next;
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	public Entry<V> getOrCreateEntry(int id) {
		Entry<V> entry = getEntryFirst(id, true);
		if (entry.v == UNDEFINED) {
			size++;
			return entry;
		}
		while (true) {
			if (entry.k == id) return entry;
			if (entry.next == null) break;
			entry = entry.next;
		}

		size++;
		return entry.next = getCachedEntry(id, (V) UNDEFINED);
	}

	protected Entry<V> getCachedEntry(int id, V value) {
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
		entry.v = null;
		notUsing = entry;
	}

	int indexFor(int id) {
		return (id ^ (id >>> 16)) & (length - 1);
	}

	protected Entry<V> createEntry(int id, V value) {
		return new Entry<>(id, value);
	}

	@SuppressWarnings("unchecked")
	Entry<V> getEntryFirst(int k, boolean create) {
		int id = indexFor(k);
		if (entries == null) {
			if (!create) return null;
			entries = new Entry<?>[length];
		}
		Entry<V> entry;
		if ((entry = (Entry<V>) entries[id]) == null) {
			if (!create) return null;
			return (Entry<V>) (entries[id] = getCachedEntry(k, (V) UNDEFINED));
		}
		return entry;
	}

	public V get(int id) {
		Entry<V> entry = getEntry(id);
		return entry == null ? null : entry.getValue();
	}

	public void clear() {
		if (size == 0) return;
		size = 0;
		if (entries == null) return;

		if (notUsing == null || notUsing.k < MAX_NOT_USING) {
			for (int i = 0; i < length; i++) {
				if (entries[i] != null) {
					putRemovedEntry(Helpers.cast(entries[i]));
					entries[i] = null;
				}
			}
		} else Arrays.fill(entries, null);
	}
}