package roj.collect;

import org.jetbrains.annotations.NotNull;
import roj.math.MathUtils;
import roj.util.Helpers;

import java.util.*;
import java.util.function.IntFunction;

import static roj.collect.IntMap.NUMKEY_LOADFACTOR;
import static roj.collect.IntMap.UNDEFINED;

public final class CharMap<V> extends AbstractMap<Character, V> implements _LibMap<CharMap.Entry<V>> {
	public static final class Entry<V> implements _LibEntry, Map.Entry<Character, V> {
		final char k;
		public V v;

		public Entry(char k, V v) {this.k = k;this.v = v;}

		@Override
		@Deprecated
		public Character getKey() {return k;}
		public char getCharKey() {return k;}
		public V getValue() {return v;}
		public V setValue(V now) {
			V v = this.v;
			this.v = now;
			return v;
		}

		Entry<V> next;
		@Override
		public _LibEntry __next() {return next;}

		@Override
		public String toString() {return k+"="+v;}
	}

	Entry<?>[] entries;
	int size;

	int length, mask;

	public CharMap() {this(16);}
	public CharMap(int size) {ensureCapacity(size);}
	public CharMap(CharMap<V> map) {
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

	private static int charHash(int c) {return ((c ^ (c >>> 7) ^ (c >>> 5) ^ (c >>> 3)) * 13);}

	// GenericMap interface
	@Override
	public final _LibEntry[] __entries() {return entries;}
	@Override
	public final void __remove(Entry<V> vEntry) {remove(vEntry.k);}
	// GenericMap interface
	// Map interface
	public final int size() {return size;}

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
	public final boolean containsKey(Object key) {return containsKey((char) key);}
	public final boolean containsKey(char key) {return getEntry(key) != null;}

	@Override
	@Deprecated
	public final V get(Object key) {return get((char) key);}
	public final V get(char key) {
		Entry<V> entry = getEntry(key);
		return entry == null ? null : entry.getValue();
	}
	public final Entry<V> getEntry(char key) {
		Entry<V> entry = getFirst(key, false);
		while (entry != null) {
			if (entry.k == key) return entry;
			entry = entry.next;
		}
		return null;
	}

	@Override
	@Deprecated
	public final V put(Character key, V value) {return put((char) key, value);}
	public final V put(char key, V e) {
		Entry<V> entry = getOrCreateEntry(key);
		V oldV = entry.setValue(e);
		return oldV == UNDEFINED ? null : oldV;
	}

	@Override
	@Deprecated
	public final V remove(Object key) {return remove((char) key);}
	public final V remove(char key) {
		Entry<V> entry = getFirst(key, false);
		if (entry == null) return null;

		if (entry.k == key) {
			size--;
			entries[charHash(key)&mask] = entry.next;
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
	public final void putAll(@NotNull Map<? extends Character, ? extends V> m) {
		if (m instanceof CharMap) putAll((CharMap<V>) m);
		else super.putAll(m);
	}
	@SuppressWarnings("unchecked")
	public final void putAll(CharMap<V> map) {
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
	public final Set<Map.Entry<Character, V>> entrySet() {return Helpers.cast(selfEntrySet());}
	public final Set<Entry<V>> selfEntrySet() {return _LibEntrySet.create(this);}

	@Override
	@Deprecated
	public final V getOrDefault(Object key, V def) {return getOrDefault((char) key, def);}
	public final V getOrDefault(char k, V def) {
		Entry<V> entry = getEntry(k);
		return entry == null ? def : entry.v;}

	public final V computeIfAbsentC(char k, @NotNull IntFunction<V> supplier) {
		Entry<V> entry = getOrCreateEntry(k);
		if (entry.v == UNDEFINED) return entry.v = supplier.apply(k);
		return entry.v;
	}

	@SuppressWarnings("unchecked")
	private void resize() {
		int length = MathUtils.getMin2PowerOf(this.length) << 1;
		if (length <= 0 || length > 16383) return;

		Entry<?>[] newEntries = new Entry<?>[length];
		int newMask = length-1;

		Entry<V> entry, next;
		int i = 0, j = entries.length;
		for (; i < j; i++) {
			entry = (Entry<V>) entries[i];
			while (entry != null) {
				next = entry.next;
				int newKey = charHash(entry.k)&newMask;
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
	private Entry<V> getOrCreateEntry(char key) {
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
	private Entry<V> getFirst(char k, boolean create) {
		int id = charHash(k) & mask;
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