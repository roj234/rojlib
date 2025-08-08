package roj.collect;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import roj.math.MathUtils;
import roj.util.Helpers;

import java.util.*;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public final class IntMap<V> extends AbstractMap<Integer, V> implements _LibMap<IntMap.Entry<V>> {
	public static final Object UNDEFINED = new Object() {
		public String toString() {return "roj.collect.UNDEFINED";}
	};

	static final float REFERENCE_LOAD_FACTOR = 0.75f;
	static final int REFERENCE_CHAIN_THRESHOLD = 5;

	static final float PRIMITIVE_LOAD_FACTOR = 1f;
	static final float PRIMITIVE_CHAIN_THRESHOLD = 5;

	public static sealed class Entry<V> implements _LibEntry, Map.Entry<Integer, V> permits IntBiMap.Entry {
		int key;
		V value;

		public Entry(int key, V value) {this.key = key;this.value = value;}

		@Override
		@Deprecated
		public Integer getKey() {return key;}
		public int getIntKey() {return key;}

		public V getValue() {return value;}
		public V setValue(V now) {
			V v = value;
			value = now;
			return v;
		}

		Entry<V> next;
		@Override public _LibEntry __next() {return next;}
		@Override public String toString() {return key+"="+value;}
	}

	private Entry<?>[] entries;
	private int size;
	private int nextResize, mask;

	public IntMap() {this(16);}
	public IntMap(int size) {ensureCapacity(size);}
	public IntMap(IntMap<? extends V> map) {
		nextResize = map.nextResize;
		mask = map.mask;
		putAll(map);
	}

	public void ensureCapacity(int size) {
		if (size <= mask) return;
		int length = MathUtils.getMin2PowerOf(size);

		if (entries != null) {
			mask = (length>>1) - 1;
			resize();
		} else {
			mask = length-1;
			nextResize = (int) (length * REFERENCE_LOAD_FACTOR);
		}
	}

	public static int hashCode(int i) {return (i ^ (i >>> 16) ^ (i >> 25));}

	// GenericMap interface
	@Override public _LibEntry[] __entries() {return entries;}
	@Override public void __remove(Entry<V> vEntry) {remove(vEntry.key);}
	// GenericMap interface

	public int size() {return size;}

	@SuppressWarnings("unchecked")
	public final boolean containsValue(Object v) {return getValueEntry((V) v) != null;}
	@SuppressWarnings("unchecked")
	public final Entry<V> getValueEntry(V v) {
		if (entries == null) return null;
		for (Entry<?> entry : entries) {
			if (entry == null) continue;
			while (entry != null) {
				if (Objects.equals(v, entry.getValue())) return (Entry<V>) entry;
				entry = entry.next;
			}
		}
		return null;
	}

	@Override
	@Deprecated
	public final boolean containsKey(Object key) {return containsKey((int) key);}
	public final boolean containsKey(int key) {return getEntry(key) != null;}

	@Override
	@Deprecated
	public final V get(Object key) {return get((int) key);}
	public final V get(int key) {
		Entry<V> entry = getEntry(key);
		return entry == null ? null : entry.getValue();
	}
	private Entry<V> getEntry(int key) {
		Entry<V> entry = getFirst(key, false);
		while (entry != null) {
			if (entry.key == key) return entry;
			entry = entry.next;
		}
		return null;
	}

	@Override
	@Deprecated
	public final V put(Integer key, V value) {return put((int)key, value);}
	public V put(int key, V e) {
		Entry<V> entry = getOrCreateEntry(key);
		V oldV = entry.setValue(e);
		return oldV == UNDEFINED ? null : oldV;
	}

	public final V remove(Object key) {return remove((int) key);}
	public V remove(int key) {
		Entry<V> entry = getFirst(key, false);
		if (entry == null) return null;

		if (entry.key == key) {
			size--;
			entries[hashCode(key)&mask] = entry.next;
			return entry.value;
		}

		Entry<V> prev = entry;
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

	@Override
	@SuppressWarnings("unchecked")
	public final void putAll(@NotNull Map<? extends Integer, ? extends V> m) {
		if (m instanceof IntMap) putAll((IntMap<V>) m);
		else super.putAll(m);
	}
	@SuppressWarnings("unchecked")
	public void putAll(IntMap<? extends V> map) {
		if (map.entries == null) return;
		ensureCapacity(size + map.size());
		for (int i = 0; i <= map.mask; i++) {
			Entry<?> entry = map.entries[i];
			while (entry != null) {
				getOrCreateEntry(entry.key).value = (V) entry.value;
				entry = entry.next;
			}
		}
	}

	public void clear() {
		if (size == 0) return;
		size = 0;
		Arrays.fill(entries, null);
	}

	public final Set<Map.Entry<Integer, V>> entrySet() {return Helpers.cast(selfEntrySet());}
	public final Set<Entry<V>> selfEntrySet() {return _LibEntrySet.create(this);}

	public V getOrDefault(Object key, V def) {return getOrDefault((int) key, def);}
	public V getOrDefault(int key, V def) {
		Entry<V> entry = getEntry(key);
		return entry == null ? def : entry.value;}

	public V computeIfAbsentI(int key, @NotNull IntFunction<V> function) {
		Entry<V> entry = getEntry(key);
		V v;
		if (entry == null) put(key, v = function.apply(key));
		else v = entry.value;
		return v;
	}
	public V computeIfAbsentS(int key, @NotNull Supplier<V> supplier) {
		Entry<V> entry = getEntry(key);
		V v;
		if (entry == null) put(key, v = supplier.get());
		else v = entry.value;
		return v;
	}

	@SuppressWarnings("unchecked")
	private void resize() {
		int length = (mask+1) << 1;
		if (length <= 0) return;

		Entry<?>[] newEntries = new Entry<?>[length];
		int newMask = length-1;

		Entry<V> entry, next;
		int i = 0, j = entries.length;
		for (; i < j; i++) {
			entry = (Entry<V>) entries[i];
			while (entry != null) {
				next = entry.next;
				int newKey = hashCode(entry.key)&newMask;
				entry.next = (Entry<V>) newEntries[newKey];
				newEntries[newKey] = entry;
				entry = next;
			}
		}

		this.entries = newEntries;
		this.mask = newMask;
		this.nextResize = (int) (length * PRIMITIVE_LOAD_FACTOR);
	}

	@SuppressWarnings("unchecked")
	private Entry<V> getOrCreateEntry(int key) {
		restart:
		for (;;) {
			Entry<V> entry = getFirst(key, true);

			int loop = 0;
			while (true) {
				if (entry.key == key) return entry;
				if (entry.next == null) {
					entry = entry.next = new Entry<>(key, (V) UNDEFINED);

					size++;
					if (loop > PRIMITIVE_CHAIN_THRESHOLD && size > nextResize) {
						resize();
						continue restart;
					}

					return entry;
				}

				loop++;
				entry = entry.next;
			}
		}
	}
	@SuppressWarnings("unchecked")
	@Contract("_, true -> !null")
	private Entry<V> getFirst(int key, boolean create) {
		int id = hashCode(key) & mask;
		if (entries == null) {
			if (!create) return null;
			entries = new Entry<?>[mask+1];
		}
		Entry<V> entry;
		if ((entry = (Entry<V>) entries[id]) == null) {
			if (!create) return null;
			size++;
			return (Entry<V>) (entries[id] = new Entry<>(key, UNDEFINED));
		}
		return entry;
	}
}