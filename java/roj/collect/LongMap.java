package roj.collect;

import org.jetbrains.annotations.NotNull;
import roj.math.MathUtils;
import roj.util.Helpers;

import java.util.*;
import java.util.function.Supplier;

import static roj.collect.IntMap.MAX_NOT_USING;
import static roj.collect.IntMap.UNDEFINED;

public class LongMap<V> extends AbstractMap<Long, V> implements _Generic_Map<LongMap.Entry<V>> {
	@SuppressWarnings("unchecked")
	public void putAll(LongMap<V> map) {
		if (map.entries == null) return;
		this.ensureCapacity(size + map.size());
		for (int i = 0; i < map.length; i++) {
			Entry<?> entry = map.entries[i];
			while (entry != null) {
				putLong(entry.k, (V) entry.v);

				entry = entry.next;
			}
		}
	}

	public V getOrDefault(long k, V def) {
		Entry<V> entry = getEntry(k);
		return entry == null ? def : entry.v;
	}

	public static class Entry<V> implements _Generic_Entry, Map.Entry<Long, V> {
		protected long k;
		protected V v;

		protected Entry(long k, V v) {
			this.k = k;
			this.v = v;
		}

		@Override
		@Deprecated
		public Long getKey() { return k; }
		public long getLongKey() { return k; }

		public V getValue() { return v; }
		public V setValue(V now) {
			V v = this.v;
			this.v = now;
			return v;
		}

		protected Entry<V> next;
		@Override
		public Entry<V> __next() { return next; }
	}

	protected Entry<?>[] entries;
	protected int size = 0;

	protected Entry<V> notUsing = null;

	int length = 2;
	float loadFactor = 0.8f;

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

	public Set<Map.Entry<Long, V>> entrySet() { return Helpers.cast(selfEntrySet()); }
	public Set<Entry<V>> selfEntrySet() { return _Generic_EntrySet.create(this); }
	public _Generic_Entry[] __entries() { return entries; }
	public void __remove(Entry<V> vEntry) { remove(vEntry.k); }

	@NotNull
	public V computeIfAbsent(long k, @NotNull Supplier<V> supplier) {
		V v = get(k);
		if (v == null) {
			putLong(k, v = supplier.get());
		}
		return v;
	}

	public V remove(Object key) { return remove((long) key); }
	public V get(Object key) { return get((long) key); }
	public boolean containsKey(Object key) { return containsKey((long) key); }
	public V put(Long key, V value) { return putLong(key, value); }
	public V getOrDefault(Object key, V def) { return getOrDefault((long) key, def); }

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

	public V putLong(long key, V e) {
		if (size > length * loadFactor) {
			length <<= 1;
			resize();
		}

		Entry<V> entry = getOrCreateEntry(key);
		V oldV = entry.setValue(e);
		if (oldV == UNDEFINED) {
			oldV = null;
			afterPut(entry);
			size++;
		}
		return oldV;
	}

	void afterPut(Entry<V> entry) {}
	void afterRemove(Entry<V> entry) {}

	public V remove(long id) {
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

	public boolean containsKey(long i) {
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

	protected Entry<V> getEntry(long id) {
		Entry<V> entry = getEntryFirst(id, false);
		while (entry != null) {
			if (entry.k == id) return entry;
			entry = entry.next;
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	Entry<V> getOrCreateEntry(long id) {
		Entry<V> entry = getEntryFirst(id, true);
		if (entry.v == UNDEFINED) return entry;
		while (true) {
			if (entry.k == id) return entry;
			if (entry.next == null) break;
			entry = entry.next;
		}

		return entry.next = getCachedEntry(id, (V) UNDEFINED);
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

		return new Entry<>(id, value);
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

	@SuppressWarnings("unchecked")
	Entry<V> getEntryFirst(long k, boolean create) {
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

	public V get(long id) {
		Entry<V> entry = getEntry(id);
		return entry == null ? null : entry.getValue();
	}

	public void clear() {
		if (size == 0) return;
		size = 0;
		if (entries != null) {
			if (notUsing == null || notUsing.k < MAX_NOT_USING) {
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