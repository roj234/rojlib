package roj.collect;

import roj.math.MathUtils;
import roj.util.Helpers;

import java.util.*;

public final class IntBiMap<V> extends AbstractMap<Integer, V> implements _LibMap<IntMap.Entry<V>> {
	static final float LOAD_FACTOR = 0.75f;

	public static final class Entry<V> extends IntMap.Entry<V> {
		Entry<V> valueNext;
		public Entry(int key, V value) {super(key, value);}
		public V setValue(V now) {throw new UnsupportedOperationException();}
		public Entry<V> __next() {return (Entry<V>) next;}
	}

	public IntBiMap() {this(16);}
	public IntBiMap(int size) {ensureCapacity(size);}
	public IntBiMap(Map<Integer, V> map) {putAll(map);}

	private Entry<?>[] keyTab, valTab;
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
	@Override public void __remove(IntMap.Entry<V> vEntry) {removeEntry((Entry<V>) vEntry);}

	public int size() {return size;}

	public boolean containsKey(int key) {return ktGet(key) != null;}
	@Override
	@Deprecated
	public V get(Object key) {return get((int)key);}
	public V get(int key) {return getOrDefault(key, null);}
	public V getOrDefault(int key, V def) {
		Entry<V> entry = ktGet(key);
		return entry == null ? def : entry.value;
	}
	@Override
	@Deprecated
	public V put(Integer key, V value) {return put(key, value, false);}
	public V put(int key, V value) {return put(key, value, false);}
	public V forcePut(int key, V value) {return put(key, value, true);}
	private V put(int key, V value, boolean replace) {
		Entry<V> keyEntry = ktGet(key);
		Entry<V> valueEntry = vtGet(value);

		if (keyEntry != null) {
			// same
			if (keyEntry == valueEntry) return value;

			// replace value
			if (valueEntry != null) removeEntry(valueEntry);

			V oldV = keyEntry.value;
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
			addEntry(new Entry<>(key, value));
			return null;
		}
	}
	@Override
	@Deprecated
	public V remove(Object key) {return remove((int)key);}
	public V remove(int key) {
		Entry<V> entry = ktGet(key);
		if (entry == null) return null;
		removeEntry(entry);
		return entry.value;
	}

	public boolean containsValue(Object value) {return vtGet(value) != null;}
	public int getByValue(Object value) {return getByValueOrDefault(value, nullId);}
	public int getByValueOrDefault(Object value, int def) {
		Entry<V> entry = vtGet(value);
		return entry == null ? def : entry.key;
	}
	public int putByValue(V value, int key) {return putByValue(value, key, false);}
	public int forcePutByValue(V value, int key) {return putByValue(value, key, true);}
	private int putByValue(V value, int key, boolean replace) {
		Entry<V> keyEntry = ktGet(key);
		Entry<V> valueEntry = vtGet(value);

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
			addEntry(new Entry<>(key, value));
			return nullId;
		}
	}
	public int removeByValue(V value) {
		Entry<V> entry = vtGet(value);
		if (entry == null) return nullId;
		removeEntry(entry);
		return entry.key;
	}

	@SuppressWarnings("unchecked")
	public void putAll(IntBiMap<V> map) {
		if (map.keyTab == null) return;
		for (var entry : map.keyTab) {
			while (entry != null) {
				put(entry.key, (V) entry.value);
				entry = entry.__next();
			}
		}
	}

	@Override
	public void putAll(Map<? extends Integer, ? extends V> m) {
		ensureCapacity(size + m.size());
		if (m instanceof IntBiMap) putAll(Helpers.cast(m));
		else super.putAll(m);
	}

	public void clear() {
		if (size == 0) return;
		size = 0;
		Arrays.fill(keyTab, null);
		Arrays.fill(valTab, null);
	}

	public Set<Entry<V>> selfEntrySet() {return _LibEntrySet.create(this);}
	public Set<Map.Entry<Integer, V>> entrySet() {return Helpers.cast(selfEntrySet());}

	@SuppressWarnings("unchecked")
	private void resize() {
		int length = (mask+1) << 1;
		if (length <= 0) return;

		Entry<?>[] newKeys = new Entry<?>[length];
		Entry<?>[] newValues = new Entry<?>[length];
		int newMask = length-1;

		Entry<V> entry, next;
		int i = 0, j = keyTab.length;

		for (; i < j; i++) {
			entry = (Entry<V>) keyTab[i];
			while (entry != null) {
				next = entry.__next();
				int newIndex = IntMap.hashCode(entry.key) & newMask;
				Entry<V> old = (Entry<V>) newKeys[newIndex];
				newKeys[newIndex] = entry;
				entry.next = old;
				entry = next;
			}

			entry = (Entry<V>) valTab[i];
			while (entry != null) {
				next = entry.valueNext;

				int newIndex = HashBiMap.hashCode(entry.value) & newMask;
				Entry<V> old = (Entry<V>) newValues[newIndex];
				newValues[newIndex] = entry;
				entry.valueNext = old;

				entry = next;
			}
		}

		this.keyTab = newKeys;
		this.valTab = newValues;
		this.mask = newMask;
		this.nextResize = (int) (length * LOAD_FACTOR);
	}

	private void addEntry(Entry<V> entry) {
		if (size > nextResize) resize();
		if (keyTab == null) {
			keyTab = new Entry<?>[mask+1];
			valTab = new Entry<?>[mask+1];
		}

		ktAdd(entry);
		vtAdd(entry);
		size++;
	}
	private void removeEntry(Entry<V> entry) {
		ktRemove(entry, entry.key);
		vtRemove(entry, entry.value);
		size--;
	}

	@SuppressWarnings("unchecked")
	private Entry<V> ktGet(int key) {
		if (keyTab == null) return null;
		int index = IntMap.hashCode(key) & mask;
		var entry = (Entry<V>) keyTab[index];
		while (entry != null) {
			if (entry.key == key) return entry;
			entry = entry.__next();
		}
		return null;
	}
	@SuppressWarnings("unchecked")
	private void ktAdd(Entry<V> entry) {
		int index = IntMap.hashCode(entry.key) & mask;
		var prev = (Entry<V>) keyTab[index];
		keyTab[index] = entry;
		entry.next = prev;
	}
	@SuppressWarnings("unchecked")
	private boolean ktRemove(Entry<V> toRemove, int key) {
		if (keyTab == null) return false;
		key = IntMap.hashCode(key) & mask;
		Entry<V> entry = (Entry<V>) keyTab[key];
		if (entry == null) return false;

		if (entry == toRemove) {
			keyTab[key] = entry.__next();
			entry.next = null;
			return true;
		}

		var prev = entry;
		while (true) {
			entry = entry.__next();
			if (entry == null) return false;

			if (entry == toRemove) {
				prev.next = entry.next;
				entry.next = null;
				return true;
			}

			prev = entry;
		}
	}

	@SuppressWarnings("unchecked")
	private Entry<V> vtGet(Object value) {
		if (valTab == null) return null;
		int index = HashBiMap.hashCode(value) & mask;
		var entry = (Entry<V>) valTab[index];
		while (entry != null) {
			if (Objects.equals(value, entry.value)) return entry;
			entry = entry.valueNext;
		}
		return null;
	}
	@SuppressWarnings("unchecked")
	private void vtAdd(Entry<V> entry) {
		int index = HashBiMap.hashCode(entry.value) & mask;
		var prev = (Entry<V>) valTab[index];
		valTab[index] = entry;
		entry.valueNext = prev;
	}
	@SuppressWarnings("unchecked")
	private boolean vtRemove(Entry<V> toRemove, V value) {
		if (valTab == null) return false;
		int index = HashBiMap.hashCode(value) & mask;
		var entry = (Entry<V>) valTab[index];
		if (entry == null) return false;

		if (entry == toRemove) {
			valTab[index] = entry.valueNext;
			entry.valueNext = null;
			return true;
		}

		var prev = entry;
		while (true) {
			entry = entry.valueNext;
			if (entry == null) return false;

			if (entry == toRemove) {
				prev.valueNext = entry.valueNext;
				entry.valueNext = null;
				return true;
			}

			prev = entry;
		}
	}
}