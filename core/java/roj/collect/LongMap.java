package roj.collect;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import roj.math.MathUtils;
import roj.util.Helpers;

import java.util.*;
import java.util.function.LongFunction;
import java.util.function.Supplier;

import static roj.collect.IntMap.*;

public final class LongMap<V> extends AbstractMap<Long, V> implements _LibMap<LongMap.Entry<V>> {
	public static final class Entry<V> implements _LibEntry, Map.Entry<Long, V> {
		final long key;
		V value;

		public Entry(long key, V value) {this.key = key;this.value = value;}

		@Deprecated public Long getKey() {return key;}
		public long getLongKey() {return key;}
		public V getValue() {return value;}
		public V setValue(V now) {
			V v = this.value;
			this.value = now;
			return v;
		}

		Entry<V> next;
		@Override public _LibEntry __next() {return next;}
		@Override public String toString() {return key+"="+value;}
	}

	private Entry<?>[] entries;
	private int size;
	private int nextResize, mask;

	public LongMap() {this(16);}
	public LongMap(int size) {ensureCapacity(size);}
	public LongMap(LongMap<? extends V> map) {
		nextResize = map.nextResize;
		mask = map.mask;
		putAll(map);
	}

	public final void ensureCapacity(int size) {
		if (size <= mask) return;
		int length = MathUtils.nextPowerOfTwo(size);

		if (entries != null) {
			mask = (length>>1) - 1;
			resize();
		} else {
			mask = length-1;
			nextResize = (int) (length * REFERENCE_LOAD_FACTOR);
		}
	}

	private static int hashCode(long i) {return Long.hashCode(i);}

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
	public final boolean containsKey(Object key) {return containsKey((long) key);}
	public final boolean containsKey(long key) {return getEntry(key) != null;}

	@Override
	@Deprecated
	public final V get(Object key) {return get((long) key);}
	public final V get(long key) {
		Entry<V> entry = getEntry(key);
		return entry == null ? null : entry.getValue();
	}
	private Entry<V> getEntry(long Key) {
		Entry<V> entry = getFirst(Key, false);
		while (entry != null) {
			if (entry.key == Key) return entry;
			entry = entry.next;
		}
		return null;
	}

	@Override
	@Deprecated
	public final V put(Long key, V value) {return put((long)key, value);}
	public final V put(long key, V value) {
		Entry<V> entry = getOrCreateEntry(key);
		V oldV = entry.setValue(value);
		return oldV == UNDEFINED ? null : oldV;
	}

	@Override
	@Deprecated
	public final V remove(Object key) {return remove((long) key);}
	public final V remove(long key) {
		Entry<V> entry = getFirst(key, false);
		if (entry == null) return null;

		if (entry.key == key) {
			size--;
			entries[hashCode(key) &mask] = entry.next;
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
	public final void putAll(@NotNull Map<? extends Long, ? extends V> m) {
		if (m instanceof LongMap) putAll((LongMap<V>) m);
		else super.putAll(m);
	}
	@SuppressWarnings("unchecked")
	public final void putAll(LongMap<V> map) {
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

	@NotNull
	@Override
	public final Set<Map.Entry<Long, V>> entrySet() {return Helpers.cast(selfEntrySet());}
	public final Set<Entry<V>> selfEntrySet() {return _LibEntrySet.create(this);}

	@Override
	@Deprecated
	public final V getOrDefault(Object key, V def) {return getOrDefault((long) key, def);}
	public final V getOrDefault(long key, V def) {Entry<V> entry = getEntry(key);
		return entry == null ? def : entry.value;}

	public final V computeIfAbsentL(long key, @NotNull LongFunction<V> function) {
		Entry<V> entry = getOrCreateEntry(key);
		if (entry.value == UNDEFINED) return entry.value = function.apply(key);
		return entry.value;
	}
	public final V computeIfAbsentS(long key, @NotNull Supplier<V> supplier) {
		Entry<V> entry = getOrCreateEntry(key);
		if (entry.value == UNDEFINED) return entry.value = supplier.get();
		return entry.value;
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
				int newKey = hashCode(entry.key) &newMask;
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
	private Entry<V> getOrCreateEntry(long key) {
		restart:
		for(;;) {
			Entry<V> entry = getFirst(key, true);

			int loop = 0;
			while (true) {
				if (entry.key == key) return entry;
				if (entry.next == null) {
					entry = entry.next = new Entry<>(key, (V)UNDEFINED);

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
	private Entry<V> getFirst(long key, boolean create) {
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