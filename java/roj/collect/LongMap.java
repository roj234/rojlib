package roj.collect;

import org.jetbrains.annotations.NotNull;
import roj.math.MathUtils;
import roj.util.Helpers;

import java.util.*;
import java.util.function.LongFunction;

import static roj.collect.IntMap.NUMKEY_LOADFACTOR;
import static roj.collect.IntMap.UNDEFINED;

public class LongMap<V> extends AbstractMap<Long, V> implements _LibMap<LongMap.Entry<V>> {
	public static final class Entry<V> implements _LibEntry, Map.Entry<Long, V> {
		final long k;
		public V v;

		public Entry(long k, V v) {this.k = k;this.v = v;}

		@Override
		@Deprecated
		public Long getKey() {return k;}
		public long getLongKey() {return k;}
		public V getValue() {return v;}
		public V setValue(V now) {
			V v = this.v;
			this.v = now;
			return v;
		}

		Entry<V> next;
		@Override
		public _LibEntry __next() {return next;}
	}

	Entry<?>[] entries;
	int size;

	int length, mask;

	public LongMap() {this(16);}
	public LongMap(int size) {ensureCapacity(size);}
	public LongMap(LongMap<V> map) {
		length = map.length;
		mask = map.mask;
		putAll(map);
	}

	public final void ensureCapacity(int size) {
		if (size <= mask) return;
		int length = MathUtils.getMin2PowerOf(size);
		mask = length-1;

		if (entries != null) resize();
		else this.length = (int) (length * NUMKEY_LOADFACTOR);
	}

	private static int longHash(long c) {return (int) (c ^ (c >>> 32));}

	// GenericMap interface
	public _LibEntry[] __entries() {return entries;}
	public void __remove(Entry<V> vEntry) {remove(vEntry.k);}
	// GenericMap interface

	public int size() {return size;}

	@SuppressWarnings("unchecked")
	public final boolean containsValue(Object v) {return getValueEntry((V) v) != null;}
	@SuppressWarnings("unchecked")
	public final Entry<V> getValueEntry(V v) {
		if (entries == null) return null;
		for (int i = 0; i <= mask; i++) {
			Entry<V> entry = (Entry<V>) entries[i];
			if (entry == null) continue;
			while (entry != null) {
				if (Objects.equals(v, entry.getValue())) return entry;
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
	public final Entry<V> getEntry(long Key) {
		Entry<V> entry = getFirst(Key, false);
		while (entry != null) {
			if (entry.k == Key) return entry;
			entry = entry.next;
		}
		return null;
	}

	@Override
	@Deprecated
	public final V put(Long key, V value) {return putLong(key, value);}
	public final V putLong(long key, V e) {
		Entry<V> entry = getOrCreateEntry(key);
		V oldV = entry.setValue(e);
		return oldV == UNDEFINED ? null : oldV;
	}

	@Override
	@Deprecated
	public final V remove(Object key) {return remove((long) key);}
	public final V remove(long key) {
		Entry<V> entry = getFirst(key, false);
		if (entry == null) return null;

		if (entry.k == key) {
			size--;
			entries[longHash(key)&mask] = entry.next;
			return entry.v;
		}

		Entry<V> prev = entry;
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
				getOrCreateEntry(entry.k).v = (V) entry.v;
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
	public final V getOrDefault(long k, V def) {Entry<V> entry = getEntry(k);
		return entry == null ? def : entry.v;}

	public final V computeIfAbsentL(long k, @NotNull LongFunction<V> supplier) {
		Entry<V> entry = getOrCreateEntry(k);
		if (entry.v == UNDEFINED) return entry.v = supplier.apply(k);
		return entry.v;
	}

	@SuppressWarnings("unchecked")
	private void resize() {
		int length = MathUtils.getMin2PowerOf(this.length) << 1;
		if (length <= 0) return;

		Entry<?>[] newEntries = new Entry<?>[length];
		int newMask = length-1;

		Entry<V> entry, next;
		int i = 0, j = entries.length;
		for (; i < j; i++) {
			entry = (Entry<V>) entries[i];
			while (entry != null) {
				next = entry.next;
				int newKey = longHash(entry.k)&newMask;
				entry.next = (Entry<V>) newEntries[newKey];
				newEntries[newKey] = entry;
				entry = next;
			}
		}

		this.entries = newEntries;
		this.mask = newMask;
		this.length = (int) (length * NUMKEY_LOADFACTOR);
	}

	@SuppressWarnings("unchecked")
	private Entry<V> getOrCreateEntry(long key) {
		Entry<V> entry = getFirst(key, true);
		while (true) {
			if (entry.k == key) return entry;
			if (entry.next == null) {
				entry = entry.next = new Entry<>(key, (V)UNDEFINED);

				if (++size > length) resize();

				return entry;
			}

			entry = entry.next;
		}
	}
	@SuppressWarnings("unchecked")
	private Entry<V> getFirst(long k, boolean create) {
		int id = longHash(k) & mask;
		if (entries == null) {
			if (!create) return null;
			entries = new Entry<?>[mask+1];
		}
		Entry<V> entry;
		if ((entry = (Entry<V>) entries[id]) == null) {
			if (!create) return null;
			size++;
			return (Entry<V>) (entries[id] = new Entry<>(k, UNDEFINED));
		}
		return entry;
	}
}