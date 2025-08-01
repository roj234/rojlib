package roj.collect;

import org.jetbrains.annotations.NotNull;
import roj.math.MathUtils;
import roj.util.Helpers;

import java.util.*;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

import static roj.collect.IntMap.UNDEFINED;

public class IntBiMap<V> extends AbstractMap<Integer, V> implements _LibMap<IntMap.Entry<V>>, ToIntFunction<V> {
	public void setNullId(int nullId) {
		this.nullId = nullId;
	}

	@Override
	public int applyAsInt(V value) {
		return getInt(value);
	}

	public static class Entry<V> extends IntMap.Entry<V> {
		protected Entry(int k, V v) { super(k, v); }

		public V setValue(V now) { throw new UnsupportedOperationException(); }

		protected Entry<V> valueNext;
		public Entry<V> __next() { return (Entry<V>) next; }
	}

	protected Entry<?>[] entries, valueEntries;

	protected int size = 0;

	int length = 2, mask = 1;

	float loadFactor = 0.8f;
	int nullId = -1;

	public IntBiMap() {
		this(16);
	}

	public IntBiMap(int size) {
		ensureCapacity(size);
	}

	public IntBiMap(Map<Integer,V> map) {
		putAll(map);
	}

	public void ensureCapacity(int size) {
		if (size < length) return;
		length = MathUtils.getMin2PowerOf(size);
		mask = length - 1;

		resize();
	}

	public V find(V v) {
		return getOrDefault(getInt(v), v);
	}

	public V getOrDefault(int key, V v) {
		Entry<V> entry = getKeyEntry(key);
		return entry == null ? v : entry.value;
	}

	public int getValueOrDefault(Object val, int def) {
		Entry<V> entry = getValueEntry(val);
		return entry == null ? def : entry.key;
	}

	public Set<Entry<V>> selfEntrySet() { return _LibEntrySet.create(this); }
	public Set<Map.Entry<Integer, V>> entrySet() { return Helpers.cast(selfEntrySet()); }

	@SuppressWarnings("unchecked")
	public void putAll(IntBiMap<V> map) {
		if (map.entries == null) return;
		this.ensureCapacity(size + map.size());
		for (int i = 0; i < map.length; i++) {
			IntMap.Entry<?> entry = map.entries[i];
			while (entry != null) {
				putInt(entry.key, (V) entry.value);

				entry = entry.next;
			}
		}
	}

	@Override
	public void putAll(Map<? extends Integer, ? extends V> m) {
		if (m instanceof IntBiMap) putAll(Helpers.cast(m));
		else super.putAll(m);
	}

	public int size() { return size; }

	@Override
	public _LibEntry[] __entries() { return entries; }

	@Override
	public void __remove(IntMap.Entry<V> vEntry) { removeEntry((Entry<V>) vEntry); }

	@NotNull
	public V computeIfAbsentInt(int k, @NotNull IntFunction<V> supplier) {
		V v = get(k);
		if (v == null) {
			putInt(k, v = supplier.apply(k));
		}
		return v;
	}

	@SuppressWarnings("unchecked")
	protected void resize() {
		if (valueEntries != null && entries != null) {
			Entry<?>[] newEntries = new Entry<?>[length];
			Entry<?>[] newValues = new Entry<?>[length];

			Entry<V> entry;
			Entry<V> next;
			int i = 0, j = entries.length;

			for (; i < j; i++) {
				entry = (Entry<V>) entries[i];
				while (entry != null) {
					next = entry.__next();
					int newIndex = indexFor(entry.key);
					Entry<V> old = (Entry<V>) newEntries[newIndex];
					newEntries[newIndex] = entry;
					entry.next = old;
					entry = next;
				}

				entry = (Entry<V>) valueEntries[i];
				while (entry != null) {
					next = entry.valueNext;

					int newIndex = indexFor(hashFor(entry.value));
					Entry<V> old = (Entry<V>) newValues[newIndex];
					newValues[newIndex] = entry;
					entry.valueNext = old;

					entry = next;
				}
			}
			this.valueEntries = newValues;
			this.entries = newEntries;

		} else if (this.valueEntries != this.entries) throw new Error();
	}

	protected int hashFor(Object v) {
		return v == null ? 0 : v.hashCode();
	}

	@Override
	@Deprecated
	public V put(Integer key, V e) { return put0(key, e, true); }
	public V putInt(int key, V e) { return put0(key, e, false); }
	public V forcePut(int key, V e) { return put0(key, e, true); }
	public int putByValue(int key, V e) { return putByValue0(e, key, false); }
	public int forcePutByValue(int key, V e) { return putByValue0(e, key, true); }

	private int putByValue0(V v, int key, boolean replace) {
		if (size > length * loadFactor) {
			length <<= 1;
			mask = length - 1;
			resize();
		}

		Entry<V> keyEntry = getKeyEntry(key);
		Entry<V> valueEntry = getValueEntry(v);

		if (keyEntry != null) {
			if (keyEntry == valueEntry) {
				return key;
			}

			if (valueEntry != null) { // value 替换key
				removeEntry(keyEntry);
				// keyEntry will be deleted

				removeKeyEntry(valueEntry, valueEntry.key);

				int old = valueEntry.key;
				valueEntry.key = key;

				putKeyEntry(valueEntry);

				return old;
			} else {
				if (!replace) throw new IllegalArgumentException("Multiple value(" + v + ", " + keyEntry.value + ") bind to same key(" + keyEntry.key + ")! use forcePut()!");

				// key找到, 没找到value
				removeValueEntry(keyEntry, keyEntry.value);

				keyEntry.value = v;

				putValueEntry(keyEntry);

				return key;
			}
		} else {
			if (valueEntry != null) {

				// key没找到, 找到value
				int oldKey = valueEntry.key;
				removeKeyEntry(valueEntry, oldKey);

				valueEntry.key = key;

				putKeyEntry(valueEntry);

				return oldKey;
			} else {
				// 全为空
				putValueEntry(createEntry(key, v));

				return nullId;
			}
		}
	}

	private V put0(int key, V v, boolean replace) {
		if (size > length * loadFactor) {
			length <<= 1;
			mask = length - 1;
			resize();
		}

		Entry<V> keyEntry = getKeyEntry(key);
		Entry<V> valueEntry = getValueEntry(v);

		// key替换value

		if (keyEntry != null) {
			if (keyEntry == valueEntry) {
				return v;
			}


			if (valueEntry != null) {
				// key和value都找到了
				removeEntry(valueEntry);
			}
			// key找到, 没找到value
			V oldV = keyEntry.value;

			removeValueEntry(keyEntry, oldV);

			keyEntry.value = v;
			putValueEntry(keyEntry);

			return oldV;
		} else {
			if (valueEntry != null) {
				if (!replace) throw new IllegalArgumentException("Multiple key(" + key + ", " + valueEntry.key + ") bind to same value(" + valueEntry.value + ")! use forcePut()!");

				// key没找到, 找到value
				removeKeyEntry(valueEntry, valueEntry.key);

				valueEntry.key = key;

				putKeyEntry(valueEntry);

				return v;
			} else {
				// 全为空
				putValueEntry(createEntry(key, v));
				return null;
			}
		}
	}

	public V remove(int id) {
		Entry<V> entry = getKeyEntry(id);
		if (entry == null) return null;
		removeEntry(entry);
		return entry.value;
	}

	public int removeByValue(V v) {
		Entry<V> entry = getValueEntry(v);
		if (entry == null) return nullId;
		removeEntry(entry);
		return entry.key;
	}

	public int getInt(V key) {
		Entry<V> entry = getValueEntry(key);
		return entry == null ? nullId : entry.key;
	}

	public V get(int id) {
		Entry<V> entry = getKeyEntry(id);
		return entry == null ? null : entry.value;
	}

	public boolean containsKey(int i) {
		return getKeyEntry(i) != null;
	}

	@SuppressWarnings("unchecked")
	public boolean containsValue(Object v) {
		return getValueEntry((V) v) != null;
	}

	public String toString() {
		StringBuilder sb = new StringBuilder("IntBiMap").append('{');
		for (Entry<V> entry : selfEntrySet()) {
			sb.append(entry.getIntKey()).append('=').append(entry.getValue()).append(',');
		}
		if (!isEmpty()) {
			sb.deleteCharAt(sb.length() - 1);
		}
		return sb.append('}').toString();
	}

	public void clear() {
		if (size == 0) return;
		size = 0;
		if (entries != null && valueEntries != null && entries.length < 128) {
			Arrays.fill(entries, null);
			Arrays.fill(valueEntries, null);
		} else {
			entries = valueEntries = null;
			length = 2;
			mask = 1;
		}
	}

	protected void removeEntry(Entry<V> toRemove) {
		removeKeyEntry(toRemove, toRemove.key);
		removeValueEntry(toRemove, toRemove.value);
		this.size--;
	}

	@SuppressWarnings("unchecked")
	boolean removeKeyEntry(Entry<V> entry, int index) {
		index = indexFor(index);
		if (entries == null) return false;
		Entry<V> currentEntry;
		Entry<V> prevEntry;
		if ((currentEntry = (Entry<V>) entries[index]) == null) {
			return false;
		}

		if (currentEntry == entry) {
			entries[index] = currentEntry.__next();
			entry.next = null;
			return true;
		}

		while (currentEntry.next != null) {
			prevEntry = currentEntry;
			currentEntry = currentEntry.__next();
			if (currentEntry == entry) {
				prevEntry.next = entry.next;
				entry.next = null;
				return true;
			}
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	boolean removeValueEntry(Entry<V> entry, V v) {
		int index = indexFor(hashFor(v));
		if (valueEntries == null) return false;
		Entry<V> currentEntry;
		Entry<V> prevEntry;
		if ((currentEntry = (Entry<V>) valueEntries[index]) == null) {
			return false;
		}

		if (currentEntry == entry) {
			valueEntries[index] = entry.valueNext;
			entry.valueNext = null;
			return true;
		}

		while (currentEntry.valueNext != null) {
			prevEntry = currentEntry;
			currentEntry = currentEntry.valueNext;
			if (currentEntry == entry) {
				prevEntry.valueNext = entry.valueNext;
				entry.valueNext = null;
				return true;
			}
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	void putValueEntry(Entry<V> entry) {
		int index = indexFor(hashFor(entry.value));
		if (valueEntries == null) valueEntries = new Entry<?>[length];
		Entry<V> currentEntry;
		if ((currentEntry = (Entry<V>) valueEntries[index]) == null) {
			valueEntries[index] = entry;
		} else {
			while (currentEntry.valueNext != null) {
				if (currentEntry == entry) return;
				currentEntry = currentEntry.valueNext;
			}
			currentEntry.valueNext = entry;
		}
	}

	@SuppressWarnings("unchecked")
	void putKeyEntry(Entry<V> entry) {
		int index = indexFor(entry.key);
		if (entries == null) entries = new Entry<?>[length];
		Entry<V> currentEntry;
		if ((currentEntry = (Entry<V>) entries[index]) == null) {
			entries[index] = entry;
		} else {
			while (currentEntry.next != null) {
				if (currentEntry == entry) return;
				currentEntry = currentEntry.__next();
			}
			currentEntry.next = entry;
		}
	}

	@SuppressWarnings("unchecked")
	protected Entry<V> getValueEntry(Object v) {
		if (valueEntries == null) return null;
		int id = indexFor(hashFor(v));

		Entry<V> entry = (Entry<V>) valueEntries[id];

		while (entry != null) {
			if (Objects.equals(v, entry.value)) {
				return entry;
			}
			entry = entry.valueNext;
		}
		return null;
	}

	protected Entry<V> getKeyEntry(int id) {
		Entry<V> entry = getEntryFirst(id, false);
		if (entry == null) return null;
		while (entry != null) {
			if (entry.key == id) return entry;
			entry = entry.__next();
		}
		return null;
	}

	protected Entry<V> createEntry(int id, V v) {
		Entry<V> entry = getEntryFirst(id, true);
		size++;
		if (entry.value == UNDEFINED) {
			entry.value = v;
			return entry;
		}
		while (entry.next != null) {
			entry = entry.__next();
		}
		Entry<V> subEntry = new Entry<>(id, v);
		entry.next = subEntry;
		return subEntry;
	}

	int indexFor(int id) {
		return (id ^ (id >>> 16)) & mask;
	}

	@SuppressWarnings("unchecked")
	Entry<V> getEntryFirst(int id, boolean create) {
		int id1 = indexFor(id);
		if (entries == null) {
			if (!create) return null;
			entries = new Entry<?>[length];
		}
		Entry<V> entry;
		if ((entry = (Entry<V>) entries[id1]) == null) {
			if (!create) return null;
			entries[id1] = entry = new Entry<>(id, (V) UNDEFINED);
		}
		return entry;
	}
}