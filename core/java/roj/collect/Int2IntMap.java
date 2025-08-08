package roj.collect;

import roj.math.MathUtils;
import roj.util.Helpers;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import static roj.collect.IntMap.*;

public final class Int2IntMap extends AbstractMap<Integer, Integer> implements _LibMap<Int2IntMap.Entry> {
	public static sealed class Entry implements _LibEntry, Map.Entry<Integer, Integer> permits Int2IntBiMap.Entry {
		int key;
		public int value;

		public Entry(int key, int value) {
			this.key = key;
			this.value = value;
		}

		@Deprecated public Integer getKey() {return key;}
		@Deprecated public Integer getValue() {return value;}
		@Deprecated public Integer setValue(Integer value) {return setIntValue(value);}
		public int getIntKey() {return key;}
		public int getIntValue() {return value;}
		public int setIntValue(int i) {
			int v = this.value;
			this.value = i;
			return v;
		}

		Entry next;
		@Override public Entry __next() {return next;}
		@Override public String toString() {return String.valueOf(key)+'='+value;}
	}

	private Entry[] entries;
	private int size;
	private int nextResize, mask;

	public Int2IntMap() {this(16);}
	public Int2IntMap(int size) {ensureCapacity(size);}
	public Int2IntMap(Int2IntMap other) {putAll(other);}
	public void ensureCapacity(int size) {
		if (size <= mask) return;
		int length = MathUtils.getMin2PowerOf(size);
		mask = length-1;

		if (entries != null) resize();
		else this.nextResize = (int) (length * PRIMITIVE_LOAD_FACTOR);
	}

	// GenericMap interface
	public _LibEntry[] __entries() {return entries;}
	public void __remove(Entry entry) {remove(entry.key);}
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
				if (v == entry.value) return entry;
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
			if (entry.key == id) return entry;
			entry = entry.next;
		}
		return null;
	}

	@Override
	@Deprecated
	public final Integer put(Integer key, Integer value) {return put((int)key, (int)value);}
	public Integer put(int key, int val) {
		Entry entry = getOrCreateEntry(key);
		if (entry.key != key) {
			entry.key = key;
			entry.value = val;
			return null;
		}

		int oldV = entry.value;
		entry.value = val;
		return oldV;
	}
	public int putInt(int key, int val) {
		Entry entry = getOrCreateEntry(key);
		if (entry.key != key) {
			entry.key = key;
			entry.value = val;
			return -1;
		}

		int oldV = entry.value;
		entry.value = val;
		return oldV;
	}

	@Override
	@Deprecated
	public final Integer remove(Object key) {return remove((int) key);}
	public Integer remove(int key) {
		Entry entry = getFirst(key, false);
		if (entry == null) return null;

		if (entry.key == key) {
			size--;
			entries[IntMap.hashCode(key)&mask] = entry.next;
			return entry.value;
		}

		Entry prev = entry;
		while (true) {
			entry = entry.next;
			if (entry == null) return null;

			if (entry.key == key) {
				size--;
				prev.next = entry.next;
				return entry.value;
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
				Entry myEntry = getOrCreateEntry(entry.key);
				myEntry.key = entry.key;
				myEntry.value = entry.value;
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
	public Set<Entry> selfEntrySet() {return _LibEntrySet.create(this);}

	@Override
	@Deprecated
	public final Integer getOrDefault(Object key, Integer def) {return getOrDefaultInt((int) key, def);}
	public final int getOrDefaultInt(int key, int def) {
		Entry entry = getEntry(key);
		return entry == null ? def : entry.value;
	}

	public Entry getEntryOrCreate(int key) {return getEntryOrCreate(key, 0);}
	public Entry getEntryOrCreate(int key, int def) {
		Entry entry = getOrCreateEntry(key);
		if (entry.key != key) {
			entry.key = key;
			entry.value = def;
			return entry;
		}
		return entry;
	}

	// return v if new
	public int putIntIfAbsent(int k, int v) {
		Entry entry = getOrCreateEntry(k);
		if (entry.key != k) {
			entry.key = k;
			entry.value = v;
		}
		return entry.value;
	}

	private void resize() {
		int length = MathUtils.getMin2PowerOf(this.nextResize) << 1;
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

				int newKey = IntMap.hashCode(entry.key)&newMask;
				entry.next = newEntries[newKey];
				newEntries[newKey] = entry;

				entry = next;
			}
		}

		this.entries = newEntries;
		this.mask = newMask;
		this.nextResize = (int) (length * PRIMITIVE_LOAD_FACTOR);
	}

	private Entry getOrCreateEntry(int key) {
		restart:
		for(;;) {
			Entry entry = getFirst(key, true);
			if (entry.key == ~key) {
				size++;
				return entry;
			}

			int loop = 0;
			while (true) {
				if (entry.key == key) return entry;
				if (entry.next == null) {
					entry = entry.next = new Entry(key, 0);

					size++;
					if (loop > PRIMITIVE_CHAIN_THRESHOLD && size > nextResize) {
						resize();
						continue restart;
					}

					entry.key = ~key;
					return entry;
				}

				loop++;
				entry = entry.next;
			}
		}
	}
	private Entry getFirst(int key, boolean create) {
		int key1 = ~key;
		key = IntMap.hashCode(key) & mask;
		if (entries == null) {
			if (!create) return null;
			entries = new Entry[nextResize];
		}
		Entry entry;
		if ((entry = entries[key]) == null) {
			if (!create) return null;
			entries[key] = entry = new Entry(key1, 0);
		}
		return entry;
	}
}