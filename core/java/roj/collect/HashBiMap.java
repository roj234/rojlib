package roj.collect;

import org.jetbrains.annotations.NotNull;
import roj.math.MathUtils;
import roj.util.Helpers;

import java.util.*;

import static roj.collect.IntMap.REFERENCE_LOAD_FACTOR;

/**
 * @author Roj234
 * @since 2021/6/18 10:35
 */
public final class HashBiMap<K, V> extends AbstractMap<K, V> implements BiMap<K, V>, _LibMap<HashMap.AbstractEntry<K, V>>, FindMap<K, V> {
	public static final class Entry<K, V> extends HashMap.Entry<K, V> {
		Entry(K key, V value) { super(key, value); }
		public V setValue(V value) { throw new UnsupportedOperationException(); }
		Entry<K, V> valueNext;
	}
	final class Inverse extends AbstractMap<V, K> implements BiMap<V, K> {
		public int size() {return HashBiMap.this.size();}
		public boolean containsKey(Object key) {return HashBiMap.this.containsValue(key);}
		public boolean containsValue(Object value) {return HashBiMap.this.containsKey(value);}
		@SuppressWarnings("unchecked")
		public K get(Object key) {return HashBiMap.this.getByValue((V) key);}
		public K put(V key, K value) {return HashBiMap.this.putByValue(key, value);}
		public K forcePut(V key, K value) {return HashBiMap.this.forcePutByValue(key, value);}

		@SuppressWarnings("unchecked")
		public K remove(Object key) {return HashBiMap.this.removeByValue((V) key);}
		public void clear() {HashBiMap.this.clear();}

		@NotNull
		public Set<Entry<V, K>> entrySet() {return new EntrySet<>(HashBiMap.this);}

		static final class EntrySet<V, K> extends AbstractSet<Map.Entry<V, K>> {
			private final HashBiMap<K, V> map;

			public EntrySet(HashBiMap<K, V> map) {this.map = map;}

			public final int size() {return map.size();}
			public final void clear() {map.clear();}

			@NotNull
			public final Iterator<Map.Entry<V, K>> iterator() {
				if (isEmpty()) return Collections.emptyIterator();
				// 不能复用HashBiMap.this.entrySet.iterator，否则在迭代过程中修改就会丢元素
				// putByValue尽可能保证value entry的稳定 （modification）
				return new AbstractIterator<>() {
					private int i;
					private HashBiMap.Entry<?, ?> entry;
					private final HashBiMap.Entry<?, ?>[] entries = map.valTab;

					@Override
					public boolean computeNext() {
						while (true) {
							if (entry == null) {
								do {
									if (i >= entries.length) return false;
									entry = entries[i++];
								} while (entry == null);
							} else {
								entry = entry.valueNext;
							}

							if (entry != null) {
								result = Helpers.cast(new SimpleImmutableEntry<>(entry.value, entry.key));
								return true;
							}
						}
					}

					@Override
					protected void remove(Entry<V, K> obj) {map.__remove(Helpers.cast(entry));}
				};
			}

			@SuppressWarnings("unchecked")
			public final boolean contains(Object o) {
				if (!(o instanceof Entry<?, ?> e)) return false;
				Object key = e.getKey();
				HashBiMap.Entry<?, ?> comp = map.vtGet((V) key);
				return comp != null && comp.value == e.getValue();
			}

			public final boolean remove(Object o) {
				if (o instanceof Map.Entry) {
					HashBiMap.Entry<?, ?> e = (HashBiMap.Entry<?, ?>) o;
					return map.remove(e.value) != null;
				}
				return false;
			}
		}

		public HashBiMap<K, V> inverse() {return HashBiMap.this;}
	}

	public HashBiMap() {this(16);}
	public HashBiMap(int size) {ensureCapacity(size);}
	public HashBiMap(Map<K, V> map) {putAll(map);}

	private final Inverse inverse = new Inverse();

	private Entry<?, ?>[] keyTab, valTab;
	private int size;
	private int nextResize, mask;

	static int hashCode(Object o) {
		if (o == null) return 0x9e3779b9;
		int h = o.hashCode();
		return h ^ (h >>> 16);
	}

	public void ensureCapacity(int size) {
		if (size <= mask) return;
		int length = MathUtils.getMin2PowerOf(size);

		if (keyTab != null) {
			mask = (length>>1) - 1;
			resize();
		} else {
			mask = length-1;
			nextResize = (int) (length * REFERENCE_LOAD_FACTOR);
		}
	}

	@Override public _LibEntry[] __entries() {return keyTab;}
	@Override public void __remove(HashMap.AbstractEntry<K, V> entry) {removeEntry((Entry<K, V>) entry);}

	public int size() {return size;}

	@SuppressWarnings("unchecked")
	public boolean containsKey(Object key) {return ktGet((K) key) != null;}
	@Override
	public Map.Entry<K, V> find(K key) {return ktGet(key);}
	@SuppressWarnings("unchecked")
	public V get(Object key) {
		Entry<K, V> entry = ktGet((K) key);
		return entry == null ? null : entry.value;
	}
	public V put(K key, V value) {return put(key, value, false);}
	public V forcePut(K key, V value) {return put(key, value, true);}
	private V put(K key, V value, boolean replace) {
		Entry<K, V> keyEntry = ktGet(key), valueEntry = vtGet(value);

		if (keyEntry != null) {
			if (keyEntry == valueEntry) return value;
			if (valueEntry != null) removeEntry(valueEntry);

			V old = keyEntry.value;
			vtRemove(keyEntry, old);

			keyEntry.value = value;
			vtAdd(keyEntry);

			return old;
		} else if (valueEntry != null) {
			if (!replace) throw new IllegalArgumentException("BiMap出现引用冲突: "+key+"="+value+" | "+valueEntry+", 请使用forcePut");

			ktRemove(valueEntry, valueEntry.key);
			valueEntry.key = key;
			ktAdd(valueEntry);

			return value;
		} else {
			addEntry(key, value);
			return null;
		}
	}
	@SuppressWarnings("unchecked")
	public V remove(Object key) {
		Entry<K, V> entry = ktGet((K) key);
		if (entry == null) return null;
		removeEntry(entry);
		return entry.value;
	}

	@SuppressWarnings("unchecked")
	public boolean containsValue(Object value) {return vtGet((V) value) != null;}
	public K getByValue(V value) {return getByValueOrDefault(value, null);}
	public K getByValueOrDefault(V value, K def) {
		Entry<K, V> entry = vtGet(value);
		return entry == null ? def : entry.key;
	}
	public K putByValue(V value, K key) {return putByValue(value, key, false);}
	public K forcePutByValue(V value, K key) {return putByValue(value, key, true);}
	private K putByValue(V value, K key, boolean replace) {
		Entry<K, V> keyEntry = ktGet(key), valueEntry = vtGet(value);

		if (valueEntry != null) {
			if (keyEntry == valueEntry) return key;

			if (keyEntry != null) {
				if (!replace) throw new IllegalArgumentException("BiMap出现引用冲突: "+keyEntry+" | "+valueEntry+", 请使用forcePut");
				removeEntry(keyEntry);
			}

			K old = valueEntry.key;
			ktRemove(valueEntry, old);

			valueEntry.key = key;
			ktAdd(valueEntry);

			return old;
		}

		if (keyEntry != null) {
			if (!replace) throw new IllegalArgumentException("BiMap出现引用冲突: "+key+"="+value+" | "+valueEntry+", 请使用forcePut");

			vtRemove(keyEntry, keyEntry.value);
			keyEntry.value = value;
			vtAdd(keyEntry);

			return key;
		} else {
			addEntry(key, value);
			return null;
		}
	}
	public K removeByValue(V value) {
		Entry<K, V> entry = vtGet(value);
		if (entry == null) return null;
		removeEntry(entry);
		return entry.key;
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> m) {
		ensureCapacity(size + m.size());
		super.putAll(m);
	}

	public void clear() {
		if (size == 0) return;
		size = 0;
		Arrays.fill(keyTab, null);
		Arrays.fill(valTab, null);
	}

	@NotNull
	public Set<Map.Entry<K, V>> entrySet() {return _LibEntrySet.create(this);}
	public BiMap<V, K> inverse() {return inverse;}

	@SuppressWarnings("unchecked")
	private void resize() {
		int length = (mask+1) << 1;
		if (length <= 0) return;

		Entry<?,?>[] newKeys = new Entry<?,?>[length];
		Entry<?,?>[] newValues = new Entry<?,?>[length];
		int newMask = length-1;

		Entry<K, V> entry, next;
		int i = 0, j = keyTab.length;

		for (; i < j; i++) {
			entry = (Entry<K, V>) keyTab[i];
			while (entry != null) {
				next = (Entry<K, V>) entry.next;
				int newIndex = hashCode(entry.key) & newMask;
				Entry<K, V> old = (Entry<K, V>) newKeys[newIndex];
				newKeys[newIndex] = entry;
				entry.next = old;
				entry = next;
			}

			entry = (Entry<K, V>) valTab[i];
			while (entry != null) {
				next = entry.valueNext;

				int newIndex = hashCode(entry.value) & newMask;
				Entry<K, V> old = (Entry<K, V>) newValues[newIndex];
				newValues[newIndex] = entry;
				entry.valueNext = old;

				entry = next;
			}
		}
		this.valTab = newValues;
		this.keyTab = newKeys;
		this.mask = newMask;
		this.nextResize = (int) (length * REFERENCE_LOAD_FACTOR);
	}

	private void addEntry(K key, V value) {
		if (keyTab == null) {
			keyTab = new Entry<?, ?>[mask+1];
			valTab = new Entry<?, ?>[mask+1];
		}
		if (size > nextResize * REFERENCE_LOAD_FACTOR) resize();

		Entry<K, V> entry = new Entry<>(key, value);
		ktAdd(entry);
		vtAdd(entry);
		size++;
	}
	private void removeEntry(Entry<K, V> toRemove) {
		ktRemove(toRemove, toRemove.key);
		vtRemove(toRemove, toRemove.value);
		size--;
	}

	@SuppressWarnings("unchecked")
	private Entry<K, V> ktGet(K key) {
		if (keyTab == null) return null;
		int index = hashCode(key) & mask;
		var entry = (Entry<K, V>) keyTab[index];
		while (entry != null) {
			if (Objects.equals(key, entry.key)) return entry;
			entry = (Entry<K, V>) entry.__next();
		}
		return null;
	}
	@SuppressWarnings("unchecked")
	private void ktAdd(Entry<K, V> entry) {
		int index = hashCode(entry.key) & mask;
		var prev = (Entry<K, V>) keyTab[index];
		keyTab[index] = entry;
		entry.next = prev;
	}
	@SuppressWarnings("unchecked")
	private boolean ktRemove(Entry<K, V> entry, K key) {
		if (keyTab == null) return false;

		int h = hashCode(key) & mask;
		Entry<K, V> curr;
		if ((curr = (Entry<K, V>) keyTab[h]) == null) {
			return false;
		}

		if (curr == entry) {
			keyTab[h] = (Entry<?, ?>) curr.__next();
			entry.next = null;
			return true;
		}

		while (curr.next != null) {
			Entry<K, V> prev = curr;
			curr = (Entry<K, V>) curr.__next();
			if (curr == entry) {
				prev.next = entry.next;
				entry.next = null;
				return true;
			}
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	private Entry<K, V> vtGet(V v) {
		if (valTab == null) return null;
		int index = hashCode(v) & mask;
		var entry = (Entry<K, V>) valTab[index];
		while (entry != null) {
			if (Objects.equals(v, entry.value)) return entry;
			entry = entry.valueNext;
		}
		return null;
	}
	@SuppressWarnings("unchecked")
	private void vtAdd(Entry<K, V> entry) {
		int index = hashCode(entry.value) & mask;
		var prev = (Entry<K, V>) valTab[index];
		valTab[index] = entry;
		entry.valueNext = prev;
	}
	@SuppressWarnings("unchecked")
	private boolean vtRemove(Entry<K, V> toRemove, V value) {
		if (valTab == null) return false;
		int index = hashCode(value) & mask;
		var entry = (Entry<K, V>) valTab[index];
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