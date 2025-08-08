package roj.collect;

import org.jetbrains.annotations.NotNull;
import roj.math.MathUtils;
import roj.util.Helpers;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

public final class Int2IntBiMap extends AbstractMap<Integer, Integer> implements _LibMap<Int2IntMap.Entry> {
	static final float LOAD_FACTOR = 0.75f;

	public static final class Entry extends Int2IntMap.Entry {
		Entry(int k, int v) {super(k, v);}
		@Override public Entry __next() {return (Entry) next;}
		Entry valueNext;
	}

	public Int2IntBiMap() {this(16);}
	public Int2IntBiMap(int size) {ensureCapacity(size);}
	public Int2IntBiMap(Map<Integer, Integer> map) {putAll(map);}

	private Entry[] keyTab, valTab;
	private int size;
	private int nextResize, mask;

	private int nullId = -1;
	public void setNullId(int nullId) {this.nullId = nullId;}

	public void ensureCapacity(int size) {
		if (size <= mask) return;
		int length = MathUtils.getMin2PowerOf(size);

		if (keyTab != null) {
			mask = (length>>1) - 1;
			resize();
		} else {
			mask = length-1;
			nextResize = (int) (length * LOAD_FACTOR);
		}
	}

	@Override public _LibEntry[] __entries() {return keyTab;}
	@Override public void __remove(Int2IntMap.Entry vEntry) {removeEntry((Entry) vEntry);}

	public int size() {return size;}

	public boolean containsKey(int key) {return ktGet(key) != null;}
	@Override
	@Deprecated
	public Integer get(Object key) {return get((int)key);}
	public int get(int key) {return getOrDefault(key, null);}
	public int getOrDefault(int key, int def) {
		Entry entry = ktGet(key);
		return entry == null ? def : entry.value;
	}
	@Override
	@Deprecated
	public Integer put(Integer key, Integer value) {return put(key, value, false);}
	public int putInt(int key, int value) {return put(key, value, false);}
	public int forcePut(int key, int value) {return put(key, value, true);}
	private int put(int key, int value, boolean replace) {
		Entry keyEntry = ktGet(key);
		Entry valueEntry = vtGet(value);

		if (keyEntry != null) {
			// same
			if (keyEntry == valueEntry) return value;

			// replace value
			if (valueEntry != null) {
				if (!replace) throw new IllegalArgumentException("BiMap出现引用冲突: "+keyEntry+" | "+valueEntry+", 请使用forcePut");
				removeEntry(valueEntry);
			}

			var oldV = keyEntry.value;
			vtRemove(keyEntry, oldV);

			keyEntry.value = value;
			vtAdd(keyEntry);

			return oldV;
		} else if (valueEntry != null) {
			if (!replace) throw new IllegalArgumentException("BiMap出现引用冲突: "+key+"="+value+" | "+valueEntry+", 请使用forcePut");

			// replace key
			ktRemove(valueEntry, valueEntry.key);
			valueEntry.key = key;
			ktAdd(valueEntry);

			return value;
		} else {
			// 全为空
			addEntry(new Entry(key, value));
			return nullId;
		}
	}
	@Override
	@Deprecated
	public Integer remove(Object key) {return remove((int)key);}
	public int remove(int key) {
		Entry entry = ktGet(key);
		if (entry == null) return nullId;
		removeEntry(entry);
		return entry.value;
	}

	public boolean containsValue(int value) {return vtGet(value) != null;}
	public int getByValue(int value) {return getByValueOrDefault(value, nullId);}
	public int getByValueOrDefault(int value, int def) {
		Entry entry = vtGet(value);
		return entry == null ? def : entry.key;
	}
	public int putByValue(int value, int key) {return putByValue(value, key, false);}
	public int forcePutByValue(int value, int key) {return putByValue(value, key, true);}
	private int putByValue(int value, int key, boolean replace) {
		Entry keyEntry = ktGet(key);
		Entry valueEntry = vtGet(value);

		if (valueEntry != null) {
			// same
			if (keyEntry == valueEntry) return key;

			// replace value
			if (keyEntry != null) {
				if (!replace) throw new IllegalArgumentException("BiMap出现引用冲突: "+valueEntry+" | "+keyEntry+", 请使用forcePut");
				removeEntry(keyEntry);
			}

			var oldK = valueEntry.key;
			ktRemove(valueEntry, oldK);

			valueEntry.key = key;
			ktAdd(valueEntry);

			return oldK;
		} else if (keyEntry != null) {
			if (!replace) throw new IllegalArgumentException("BiMap出现引用冲突: "+key+"="+value+" | "+keyEntry+", 请使用forcePut");

			// replace key
			vtRemove(keyEntry, keyEntry.value);
			keyEntry.value = value;
			vtAdd(keyEntry);

			return key;
		} else {
			// 全为空
			addEntry(new Entry(key, value));
			return nullId;
		}
	}
	public int removeByValue(int value) {
		Entry entry = vtGet(value);
		if (entry == null) return nullId;
		removeEntry(entry);
		return entry.key;
	}

	public void putAll(Int2IntBiMap map) {
		if (map.keyTab == null) return;
		for (var entry : map.keyTab) {
			while (entry != null) {
				putInt(entry.key, entry.value);

				entry = entry.__next();
			}
		}
	}

	@Override
	public void putAll(Map<? extends Integer, ? extends Integer> m) {
		ensureCapacity(size + m.size());
		if (m instanceof Int2IntBiMap) putAll(Helpers.cast(m));
		else super.putAll(m);
	}

	public void clear() {
		if (size == 0) return;
		size = 0;
		Arrays.fill(keyTab, null);
		Arrays.fill(valTab, null);
	}

	public Set<Entry> selfEntrySet() {return _LibEntrySet.create(this);}
	public @NotNull Set<Map.Entry<Integer, Integer>> entrySet() {return Helpers.cast(selfEntrySet());}

	private void resize() {
		int length = (mask+1) << 1;
		if (length <= 0) return;

		Entry[] newKeys = new Entry[length];
		Entry[] newValues = new Entry[length];
		int newMask = length-1;

		Entry entry, next;
		int i = 0, j = keyTab.length;

		for (; i < j; i++) {
			entry = keyTab[i];
			while (entry != null) {
				next = entry.__next();
				int newIndex = IntMap.hashCode(entry.key) & newMask;
				Entry old = newKeys[newIndex];
				newKeys[newIndex] = entry;
				entry.next = old;
				entry = next;
			}

			entry = valTab[i];
			while (entry != null) {
				next = entry.valueNext;

				int newIndex = IntMap.hashCode(entry.value) & newMask;
				Entry old = newValues[newIndex];
				newValues[newIndex] = entry;
				entry.valueNext = old;

				entry = next;
			}
		}
		this.valTab = newValues;
		this.keyTab = newKeys;
		this.mask = newMask;
		this.nextResize = (int) (length * LOAD_FACTOR);
	}

	private void addEntry(Entry entry) {
		if (size > nextResize) resize();
		if (keyTab == null) {
			keyTab = new Entry[mask+1];
			valTab = new Entry[mask+1];
		}

		ktAdd(entry);
		vtAdd(entry);
		size++;
	}
	private void removeEntry(Entry entry) {
		ktRemove(entry, entry.key);
		vtRemove(entry, entry.value);
		size--;
	}

	private Entry ktGet(int key) {
		if (keyTab == null) return null;
		int index = IntMap.hashCode(key) & mask;
		var entry = keyTab[index];
		while (entry != null) {
			if (entry.key == key) return entry;
			entry = entry.__next();
		}
		return null;
	}
	private void ktAdd(Entry entry) {
		int index = IntMap.hashCode(entry.key) & mask;
		var prev = keyTab[index];
		keyTab[index] = entry;
		entry.next = prev;
	}
	private boolean ktRemove(Entry toRemove, int key) {
		if (keyTab == null) return false;
		key = IntMap.hashCode(key) & mask;
		Entry entry = keyTab[key];
		if (entry == null) return false;

		if (entry == toRemove) {
			keyTab[key] = entry.__next();
			toRemove.next = null;
			return true;
		}

		var prev = entry;
		while (true) {
			entry = entry.__next();
			if (entry == null) return false;

			if (entry == toRemove) {
				prev.next = toRemove.next;
				toRemove.next = null;
				return true;
			}

			prev = entry;
		}
	}

	private Entry vtGet(int value) {
		if (valTab == null) return null;
		int index = IntMap.hashCode(value) & mask;
		var entry = valTab[index];
		while (entry != null) {
			if (value == entry.value) return entry;
			entry = entry.valueNext;
		}
		return null;
	}
	private void vtAdd(Entry entry) {
		int index = IntMap.hashCode(entry.value) & mask;
		var prev = valTab[index];
		valTab[index] = entry;
		entry.valueNext = prev;
	}
	private boolean vtRemove(Entry toRemove, int value) {
		if (valTab == null) return false;
		int index = IntMap.hashCode(value) & mask;
		var entry = valTab[index];
		if (entry == null) return false;

		if (entry == toRemove) {
			valTab[index] = toRemove.valueNext;
			toRemove.valueNext = null;
			return true;
		}

		var prev = entry;
		while (true) {
			entry = entry.valueNext;
			if (entry == null) return false;

			if (entry == toRemove) {
				prev.valueNext = toRemove.valueNext;
				toRemove.valueNext = null;
				return true;
			}

			prev = entry;
		}
	}
}