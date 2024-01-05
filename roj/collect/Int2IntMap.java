package roj.collect;

import roj.math.MathUtils;
import roj.util.Helpers;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import static roj.collect.IntMap.MAX_NOT_USING;

public class Int2IntMap extends AbstractMap<Integer, Integer> implements _Generic_Map<Int2IntMap.Entry> {
	public int getOrDefaultInt(int key, int def) {
		Entry entry = getEntry(key);
		return entry == null ? def : entry.v;
	}

	// return v if new
	public int putIntIfAbsent(int k, int v) {
		if (size > length * loadFactor) {
			length <<= 1;
			resize();
		}

		Entry entry = getOrCreateEntry(k, k-1);
		if (entry.k == k-1) {
			entry.k = k;
			size++;
		}
		int oldV = entry.v;
		entry.v = v;

		return oldV;
	}

	public static class Entry implements _Generic_Entry, Map.Entry<Integer, Integer> {
		protected int k;
		public int v;

		public Entry(int k, int v) {
			this.k = k;
			this.v = v;
		}

		@Deprecated
		public Integer getKey() { return k; }
		public int getIntKey() { return k; }
		@Deprecated
		public Integer getValue() { return v; }
		@Deprecated
		public Integer setValue(Integer value) { return setIntValue(value); }

		public void _SetKey(int k) { this.k = k; }

		public int getIntValue() { return v; }
		public int setIntValue(int i) {
			int v = this.v;
			this.v = i;
			return v;
		}

		protected Entry next;
		@Override
		public Entry __next() { return next; }

		@Override
		public String toString() { return "Entry{"+k+'='+v+'}'; }
	}

	protected Entry[] entries;
	protected int size = 0;

	int length = 2, mask = 1;
	float loadFactor = 0.8f;

	public Int2IntMap() {
		this(16);
	}

	public Int2IntMap(int size) {
		ensureCapacity(size);
	}

	public void ensureCapacity(int size) {
		if (size < length) return;
		length = MathUtils.getMin2PowerOf(size);

		if (this.entries != null) resize();
		else mask = length - 1;
	}

	public Set<Entry> selfEntrySet() { return _Generic_EntrySet.create(this); }
	public Set<Map.Entry<Integer, Integer>> entrySet() { return Helpers.cast(selfEntrySet()); }

	public int size() { return size; }

	public Integer remove(Object key) { return remove((int) key); }
	public Integer get(Object key) { return get((int) key); }
	public boolean containsKey(Object key) { return containsKey((int) key); }
	public Integer put(Integer key, Integer value) { return putInt(key, value); }
	public Integer getOrDefault(Object key, Integer def) { return getOrDefaultInt((int) key, def); }

	public _Generic_Entry[] __entries() { return entries; }
	public void __remove(Entry entry) { remove(entry.k); }

	protected void resize() {
		Entry[] newEntries = new Entry[length];
		Entry entry;
		Entry next;
		int i = 0, j = entries.length;
		int mask1 = length - 1;
		for (; i < j; i++) {
			entry = entries[i];
			while (entry != null) {
				next = entry.next;
				int newKey = hash(entry.k)&mask1;
				Entry entry2 = newEntries[newKey];
				newEntries[newKey] = entry;
				entry.next = entry2;
				entry = next;
			}
		}

		this.entries = newEntries;
		this.mask = mask1;
	}

	public Integer put(int key, int e) {
		if (size > length * loadFactor) {
			length <<= 1;
			resize();
		}

		int k2 = key - 1;

		Entry entry = getOrCreateEntry(key, k2);
		if (entry.k == k2) {
			entry.k = key;
			size++;
			entry.v = e;
			return null;
		}
		Integer oldV = entry.v;
		entry.v = e;
		return oldV;
	}

	public void putAll(Int2IntMap m) {
		Entry[] ent = m.entries;
		if (ent == null) return;
		for (int i = 0; i < m.length; i++) {
			Entry entry = ent[i];
			if (entry == null) continue;
			while (entry != null) {
				putInt(entry.k, entry.v);
				entry = entry.next;
			}
		}
	}

	public int putInt(int key, int e) {
		if (size > length * loadFactor) {
			length <<= 1;
			resize();
			mask = length - 1;
		}

		int k2 = key - 1;

		Entry entry = getOrCreateEntry(key, k2);
		if (entry.k == k2) {
			entry.k = key;
			size++;
			entry.v = e;
			return -1;
		}
		int oldV = entry.v;
		entry.v = e;
		return oldV;
	}

	public Integer remove(int id) {
		Entry prevEntry = null;
		Entry toRemove = null;
		{
			Entry entry = getEntryFirst(id, -1, false);
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

		this.size--;

		if (prevEntry != null) {
			prevEntry.next = toRemove.next;
		} else {
			this.entries[hash(id)&mask] = toRemove.next;
		}

		putRemovedEntry(toRemove);

		return toRemove.v;
	}

	public boolean containsKey(int i) {
		return getEntry(i) != null;
	}

	public Entry getEntry(int id) {
		Entry entry = getEntryFirst(id, -1, false);
		while (entry != null) {
			if (entry.k == id) return entry;
			entry = entry.next;
		}
		return null;
	}

	public Entry getEntryOrCreate(int key, int def) {
		Entry entry = getOrCreateEntry(key, key - 1);
		if (entry.k == key - 1) {
			entry.k = key;
			entry.v = def;
			size++;
			return entry;
		}
		return entry;
	}

	public Entry getEntryOrCreate(int key) {
		return getEntryOrCreate(key, 0);
	}

	Entry getOrCreateEntry(int id, int def) {
		Entry entry = getEntryFirst(id, def, true);
		if (entry.k == def) return entry;
		while (true) {
			if (entry.k == id) return entry;
			if (entry.next == null) break;
			entry = entry.next;
		}
		Entry firstUnused = getCachedEntry(def, -1);
		entry.next = firstUnused;
		return firstUnused;
	}

	int hash(int id) {
		return (id ^ (id >>> 16));
	}

	protected Entry notUsing = null;

	protected Entry getCachedEntry(int id, int val) {
		Entry cached = this.notUsing;
		if (cached != null) {
			cached.k = id;
			cached.v = val;
			this.notUsing = cached.next;
			cached.next = null;
			return cached;
		}

		return new Entry(id, val);
	}

	protected void putRemovedEntry(Entry entry) {
		if (notUsing != null && notUsing.k > MAX_NOT_USING) {
			return;
		}
		entry.next = notUsing;
		entry.k = notUsing == null ? 1 : notUsing.k + 1;
		notUsing = entry;
	}

	Entry getEntryFirst(int id, int def, boolean create) {
		id = hash(id) & mask;
		if (entries == null) {
			if (!create) return null;
			entries = new Entry[length];
		}
		Entry entry;
		if ((entry = entries[id]) == null) {
			if (!create) return null;
			entries[id] = entry = getCachedEntry(def, 0);
		}
		return entry;
	}

	public Integer get(int id) {
		Entry entry = getEntry(id);
		return entry == null ? null : entry.getIntValue();
	}

	Entry getValueEntry(int value) {
		if (entries == null) return null;
		for (int i = 0; i < length; i++) {
			Entry entry = entries[i];
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

	@Override
	public boolean containsValue(Object value) {
		return getValueEntry((Integer) value) != null;
	}

	public boolean containsValue(int o) {
		return getValueEntry(o) != null;
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