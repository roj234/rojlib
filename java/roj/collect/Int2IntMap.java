package roj.collect;

import roj.math.MathUtils;
import roj.util.Helpers;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import static roj.collect.IntMap.NUMKEY_LOADFACTOR;
import static roj.collect.IntMap.intHash;

public class Int2IntMap extends AbstractMap<Integer, Integer> implements _Generic_Map<Int2IntMap.Entry> {
	public static class Entry implements _Generic_Entry, Map.Entry<Integer, Integer> {
		public int v;
		protected int k;
		protected Entry next;

		public Entry(int k, int v) {
			this.k = k;
			this.v = v;
		}

		@Deprecated
		public Integer getKey() {return k;}

		@Deprecated
		public Integer getValue() {return v;}

		@Deprecated
		public Integer setValue(Integer value) {return setIntValue(value);}

		public int setIntValue(int i) {
			int v = this.v;
			this.v = i;
			return v;
		}

		public int getIntKey() {return k;}

		public int getIntValue() {return v;}

		@Override
		public Entry __next() {return next;}

		@Override
		public String toString() {return "Entry{" + k + '=' + v + '}';}
	}

	Entry[] entries;
	int size;

	int length, mask;

	public Int2IntMap() {this(16);}
	public Int2IntMap(int size) {ensureCapacity(size);}
	public Int2IntMap(Int2IntMap other) {putAll(other);}
	public void ensureCapacity(int size) {
		if (size <= mask) return;
		int length = MathUtils.getMin2PowerOf(size);
		mask = length-1;

		if (entries != null) resize();
		else this.length = (int) (length * NUMKEY_LOADFACTOR);
	}

	// GenericMap interface
	public _Generic_Entry[] __entries() {return entries;}
	public void __remove(Entry entry) {remove(entry.k);}
	// GenericMap interface

	public final int size() {return size;}

	@Override
	@Deprecated
	public final boolean containsValue(Object v) {return getValueEntry((Integer) v) != null;}
	public final boolean containsValue(int v) {return getValueEntry(v) != null;}
	public final Entry getValueEntry(int v) {
		if (entries == null) return null;
		for (int i = 0; i <= mask; i++) {
			Entry entry = entries[i];
			if (entry == null) continue;
			while (entry != null) {
				if (v == entry.v) return entry;
				entry = entry.next;
			}
		}
		return null;
	}

	@Override
	@Deprecated
	public final boolean containsKey(Object key) {return containsKey((int) key);}
	public final boolean containsKey(int i) {return getEntry(i) != null;}

	@Override
	@Deprecated
	public final Integer get(Object key) {return get((int) key);}
	public final Integer get(int id) {
		Entry entry = getEntry(id);
		return entry == null ? null : entry.getIntValue();
	}
	public final Entry getEntry(int id) {
		Entry entry = getFirst(id, false);
		while (entry != null) {
			if (entry.k == id) return entry;
			entry = entry.next;
		}
		return null;
	}

	@Override
	@Deprecated
	public final Integer put(Integer key, Integer value) {return put((int)key, (int)value);}
	public Integer put(int key, int val) {
		Entry entry = getOrCreateEntry(key);
		if (entry.k != key) {
			entry.k = key;
			entry.v = val;
			return null;
		}

		int oldV = entry.v;
		entry.v = val;
		return oldV;
	}
	public int putInt(int key, int val) {
		Entry entry = getOrCreateEntry(key);
		if (entry.k != key) {
			entry.k = key;
			entry.v = val;
			return -1;
		}

		int oldV = entry.v;
		entry.v = val;
		return oldV;
	}

	@Override
	@Deprecated
	public final Integer remove(Object key) {return remove((int) key);}
	public Integer remove(int key) {
		Entry entry = getFirst(key, false);
		if (entry == null) return null;

		if (entry.k == key) {
			size--;
			entries[intHash(key)&mask] = entry.next;
			return entry.v;
		}

		Entry prev = entry;
		while (true) {
			entry = entry.next;
			if (entry == null) return null;

			if (entry.k == key) {
				size--;
				prev.next = entry.next;
				return entry.v;
			}

			prev = entry;
		}
	}

	public void putAll(Int2IntMap map) {
		if (map.entries == null) return;
		ensureCapacity(size + map.size());
		for (int i = 0; i <= map.mask; i++) {
			Entry entry = map.entries[i];
			while (entry != null) {
				Entry myEntry = getOrCreateEntry(entry.k);
				myEntry.k = entry.k;
				myEntry.v = entry.v;
				entry = entry.next;
			}
		}
	}

	public void clear() {
		if (size == 0) return;
		size = 0;
		Arrays.fill(entries, null);
	}

	public Set<Map.Entry<Integer, Integer>> entrySet() {return Helpers.cast(selfEntrySet());}
	public Set<Entry> selfEntrySet() {return _Generic_EntrySet.create(this);}

	@Override
	@Deprecated
	public final Integer getOrDefault(Object key, Integer def) {return getOrDefaultInt((int) key, def);}
	public final int getOrDefaultInt(int key, int def) {
		Entry entry = getEntry(key);
		return entry == null ? def : entry.v;
	}

	public Entry getEntryOrCreate(int key) {return getEntryOrCreate(key, 0);}
	public Entry getEntryOrCreate(int key, int def) {
		Entry entry = getOrCreateEntry(key);
		if (entry.k != key) {
			entry.k = key;
			entry.v = def;
			return entry;
		}
		return entry;
	}

	// return v if new
	public int putIntIfAbsent(int k, int v) {
		Entry entry = getOrCreateEntry(k);
		if (entry.k != k) {
			entry.k = k;
			entry.v = v;
		}
		return entry.v;
	}

	private void resize() {
		int length = MathUtils.getMin2PowerOf(this.length) << 1;
		if (length <= 0) return;

		Entry[] newEntries = new Entry[length];
		int newMask = length-1;

		Entry entry;
		Entry next;
		int i = 0, j = entries.length;
		for (; i < j; i++) {
			entry = entries[i];
			while (entry != null) {
				next = entry.next;

				int newKey = intHash(entry.k)&newMask;
				entry.next = newEntries[newKey];
				newEntries[newKey] = entry;

				entry = next;
			}
		}

		this.entries = newEntries;
		this.mask = newMask;
		this.length = (int) (length * NUMKEY_LOADFACTOR);
	}

	private Entry getOrCreateEntry(int key) {
		Entry entry = getFirst(key, true);
		if (entry.k == ~key) {
			size++;
			return entry;
		}

		while (true) {
			if (entry.k == key) return entry;
			if (entry.next == null) {
				entry = entry.next = new Entry(key, 0);

				if (++size > length) resize();

				entry.k = ~key;
				return entry;
			}

			entry = entry.next;
		}
	}
	private Entry getFirst(int key, boolean create) {
		int key1 = ~key;
		key = intHash(key) & mask;
		if (entries == null) {
			if (!create) return null;
			entries = new Entry[length];
		}
		Entry entry;
		if ((entry = entries[key]) == null) {
			if (!create) return null;
			entries[key] = entry = new Entry(key1, 0);
		}
		return entry;
	}
}