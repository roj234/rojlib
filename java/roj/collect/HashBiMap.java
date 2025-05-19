package roj.collect;

import org.jetbrains.annotations.NotNull;
import roj.math.MathUtils;
import roj.util.Helpers;

import java.util.*;

import static roj.collect.IntMap.NUMKEY_LOADFACTOR;

/**
 * @author Roj234
 * @since 2021/6/18 10:35
 */
public class HashBiMap<K, V> extends AbstractMap<K, V> implements Flippable<K, V>, _Generic_Map<MyHashMap.AbstractEntry<K, V>>, FindMap<K, V> {
	public static class Entry<K, V> extends MyHashMap.Entry<K, V> {
		protected Entry(K k, V v) { super(k, v); }
		public V setValue(V value) { throw new UnsupportedOperationException(); }

		protected Entry<K, V> valueNext;
	}

	static final class Inverse<V, K> extends AbstractMap<V, K> implements Flippable<V, K> {
		private final HashBiMap<K, V> parent;

		private Inverse(HashBiMap<K, V> parent) {this.parent = parent;}

		public int size() {return parent.size();}
		public boolean containsKey(Object key) {return parent.containsValue(key);}
		public boolean containsValue(Object value) {return parent.containsKey(value);}
		@SuppressWarnings("unchecked")
		public K get(Object key) {return parent.getByValue((V) key);}
		public K put(V key, K value) {return parent.putByValue(key, value);}
		public K forcePut(V key, K value) {return parent.forcePutByValue(key, value);}

		@SuppressWarnings("unchecked")
		public K remove(Object key) {return parent.removeByValue((V) key);}
		public void clear() {parent.clear();}

		@NotNull
		public Set<Entry<V, K>> entrySet() {return new EntrySet<>(this.parent);}

		static class EntrySet<V, K> extends AbstractSet<Map.Entry<V, K>> {
			private final HashBiMap<K, V> map;

			public EntrySet(HashBiMap<K, V> map) {this.map = map;}

			public final int size() {return map.size();}
			public final void clear() {map.clear();}

			@NotNull
			public final Iterator<Map.Entry<V, K>> iterator() {
				if (isEmpty()) return Collections.emptyIterator();
				// 不能复用parent.entrySet.iterator，否则在迭代过程中修改就会丢元素
				// putByValue尽可能保证value entry的稳定 （modification）
				return new AbstractIterator<Entry<V, K>>() {
					private int i = 0;
					private HashBiMap.Entry<?, ?> t;
					private final HashBiMap.Entry<?, ?>[] entries = map.vTab;

					@Override
					public boolean computeNext() {
						while (true) {
							if (t == null) {
								do {
									if (i >= entries.length) return false;
									t = entries[i++];
								} while (t == null);
							} else {
								t = t.valueNext;
							}

							if (t != null) {
								result = Helpers.cast(new SimpleImmutableEntry<>(t.value, t.key));
								return true;
							}
						}
					}

					@Override
					protected void remove(Entry<V, K> obj) {
						map.__remove(Helpers.cast(t));
					}
				};
			}

			@SuppressWarnings("unchecked")
			public final boolean contains(Object o) {
				if (!(o instanceof Map.Entry)) return false;
				Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
				Object key = e.getKey();
				HashBiMap.Entry<?, ?> comp = map.getValueEntry((V) key);
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

		public HashBiMap<K, V> flip() {return parent;}
	}

	protected Entry<?, ?>[] kTab, vTab;
	protected int size = 0;

	int length = 2, mask = 1;

	private final Inverse<V, K> inverse = new Inverse<>(this);

	public HashBiMap() {this(16);}
	public HashBiMap(int size) {ensureCapacity(size);}
	public HashBiMap(Map<K, V> map) {
		ensureCapacity(map.size());
		putAll(map);
	}

	@Override
	public Map.Entry<K, V> find(K k) {return getKeyEntry(k);}

	public void ensureCapacity(int size) {
		if (size < length) return;
		length = MathUtils.getMin2PowerOf(size);

		if (kTab != null) resize();
		else mask = length -1;
	}

	public Flippable<V, K> flip() {return inverse;}

	@NotNull
	public Set<Map.Entry<K, V>> entrySet() { return _Generic_EntrySet.create(this); }

	public int size() {return size;}

	@SuppressWarnings("unchecked")
	private void resize() {
		if (kTab != null) {
			Entry<?, ?>[] kTab1 = new Entry<?, ?>[length];
			Entry<?, ?>[] vTab1 = new Entry<?, ?>[length];

			Entry<K, V> entry, next;
			int i = 0, j = kTab.length;
			int mask1 = length-1;

			for (; i < j; i++) {
				entry = (Entry<K, V>) kTab[i];
				while (entry != null) {
					next = (Entry<K, V>) entry.__next();
					int newIndex = indexFor(entry.key)&mask1;
					Entry<K, V> old = (Entry<K, V>) kTab1[newIndex];
					kTab1[newIndex] = entry;
					entry.next = old;
					entry = next;
				}

				entry = (Entry<K, V>) vTab[i];
				while (entry != null) {
					next = entry.valueNext;

					int newIndex = indexFor(entry.value)&mask1;
					Entry<K, V> old = (Entry<K, V>) vTab1[newIndex];
					vTab1[newIndex] = entry;
					entry.valueNext = old;

					entry = next;
				}
			}

			kTab = kTab1;
			vTab = vTab1;
			mask = mask1;
		}
	}

	private int indexFor(Object v) {
		if (v == null) return 216378912;
		int h = v.hashCode();
		return h ^ (h >>> 16);
	}

	public V put(K key, V e) {
		return put0(key, e, false);
	}
	public V forcePut(K key, V e) {
		return put0(key, e, true);
	}

	public K putByValue(V e, K key) {
		return putByValue0(e, key, false);
	}
	public K forcePutByValue(V e, K key) {
		return putByValue0(e, key, true);
	}

	private V put0(K key, V v, boolean replace) {
		if (size > length * NUMKEY_LOADFACTOR) {
			length <<= 1;
			resize();
		}

		Entry<K, V> kEntry = getKeyEntry(key), vEntry = getValueEntry(v);

		// key替换value

		if (kEntry != null) {
			if (vEntry != null) {
				if (kEntry == vEntry) return v;

				removeEntry(vEntry);
			}

			V old = kEntry.value;
			removeValueEntry(kEntry, old);

			kEntry.value = v;
			putValueEntry(kEntry);

			return old;
		}

		if (vEntry != null) {
			if (!replace) throw new IllegalArgumentException("Multiple key(" + key + ", " + vEntry.key + ") bind to same value(" + vEntry.value + ")! use forcePut()!");

			removeKeyEntry(vEntry, vEntry.key);

			vEntry.key = key;
			putKeyEntry(vEntry);

			return v;
		} else {
			createEntry(key, v);
			return null;
		}
	}
	private K putByValue0(V v, K key, boolean replace) {
		if (size > length * NUMKEY_LOADFACTOR) {
			length <<= 1;
			resize();
		}

		Entry<K, V> kEntry = getKeyEntry(key), vEntry = getValueEntry(v);

		if (vEntry != null) {
			if (kEntry != null) {
				if (kEntry == vEntry) return key;

				removeEntry(kEntry);
			}

			K old = vEntry.key;
			removeKeyEntry(vEntry, old);

			vEntry.key = key;
			putKeyEntry(vEntry);

			return old;
		}

		if (kEntry != null) {
			if (!replace) throw new IllegalArgumentException("Multiple value(" + v + ", " + kEntry.value + ") bind to same key(" + kEntry.key + ")! use forcePut()!");

			removeValueEntry(kEntry, kEntry.value);

			kEntry.value = v;
			putValueEntry(kEntry);

			return key;
		} else {
			createEntry(key, v);
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	public V remove(Object id) {
		Entry<K, V> entry = getKeyEntry((K) id);
		if (entry == null) return null;
		removeEntry(entry);
		return entry.value;
	}
	public K removeByValue(V v) {
		Entry<K, V> entry = getValueEntry(v);
		if (entry == null) return null;
		removeEntry(entry);
		return entry.key;
	}

	@SuppressWarnings("unchecked")
	public V get(Object id) {
		Entry<K, V> entry = getKeyEntry((K) id);
		return entry == null ? null : entry.value;
	}
	public K getByValue(V key) {
		Entry<K, V> entry = getValueEntry(key);
		return entry == null ? null : entry.key;
	}

	@SuppressWarnings("unchecked")
	public boolean containsKey(Object i) {
		return getKeyEntry((K) i) != null;
	}
	@SuppressWarnings("unchecked")
	public boolean containsValue(Object v) {
		return getValueEntry((V) v) != null;
	}

	public void clear() {
		if (size == 0) return;
		size = 0;
		Arrays.fill(kTab, null);
		Arrays.fill(vTab, null);
	}

	@Override
	public _Generic_Entry[] __entries() { return kTab; }
	public void __remove(MyHashMap.AbstractEntry<K, V> entry) { removeEntry((Entry<K, V>) entry); }

	protected void removeEntry(Entry<K, V> toRemove) {
		removeKeyEntry(toRemove, toRemove.key);
		removeValueEntry(toRemove, toRemove.value);
		this.size--;
	}

	@SuppressWarnings("unchecked")
	private boolean removeKeyEntry(Entry<K, V> entry, K k) {
		if (kTab == null) return false;

		int h = indexFor(k)&mask;
		Entry<K, V> curr;
		if ((curr = (Entry<K, V>) kTab[h]) == null) {
			return false;
		}

		if (curr == entry) {
			kTab[h] = (Entry<?, ?>) curr.__next();
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
	private boolean removeValueEntry(Entry<K, V> entry, V v) {
		if (vTab == null) return false;

		int index = indexFor(v)&mask;
		Entry<K, V> curr;
		if ((curr = (Entry<K, V>) vTab[index]) == null) {
			return false;
		}

		if (curr == entry) {
			vTab[index] = entry.valueNext;
			entry.valueNext = null;
			return true;
		}

		while (curr.valueNext != null) {
			Entry<K, V> prev = curr;
			curr = curr.valueNext;
			if (curr == entry) {
				prev.valueNext = entry.valueNext;
				entry.valueNext = null;
				return true;
			}
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	private void putKeyEntry(Entry<K, V> entry) {
		int h = indexFor(entry.key)&mask;
		if (kTab == null) kTab = new Entry<?, ?>[length];
		Entry<K, V> curr;
		if ((curr = (Entry<K, V>) kTab[h]) == null) {
			kTab[h] = entry;
		} else {
			while (curr.next != null) {
				if (curr == entry) return;
				curr = (Entry<K, V>) curr.__next();
			}
			curr.next = entry;
		}
	}
	@SuppressWarnings("unchecked")
	private void putValueEntry(Entry<K, V> entry) {
		int h = indexFor(entry.value)&mask;
		if (vTab == null) vTab = new Entry<?, ?>[length];
		Entry<K, V> curr;
		if ((curr = (Entry<K, V>) vTab[h]) == null) {
			vTab[h] = entry;
		} else {
			while (curr.valueNext != null) {
				if (curr == entry) return;
				curr = curr.valueNext;
			}
			curr.valueNext = entry;
		}
	}

	@SuppressWarnings("unchecked")
	protected Entry<K, V> getKeyEntry(K k) {
		if (kTab == null) return null;
		int h = indexFor(k)&mask;
		Entry<K, V> entry = (Entry<K, V>) kTab[h];
		while (entry != null) {
			if (Objects.equals(k, entry.key)) {
				return entry;
			}
			entry = (Entry<K, V>) entry.__next();
		}
		return null;
	}
	@SuppressWarnings("unchecked")
	protected Entry<K, V> getValueEntry(V v) {
		if (vTab == null) return null;
		int h = indexFor(v)&mask;
		Entry<K, V> entry = (Entry<K, V>) vTab[h];
		while (entry != null) {
			if (Objects.equals(v, entry.value)) {
				return entry;
			}
			entry = entry.valueNext;
		}
		return null;
	}

	protected void createEntry(K id, V v) {
		Entry<K, V> entry = new Entry<>(id, v);
		putKeyEntry(entry);
		putValueEntry(entry);
		size++;
	}
}