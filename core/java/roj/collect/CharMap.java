package roj.collect;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import roj.math.MathUtils;
import roj.util.Helpers;

import java.util.*;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import static roj.collect.IntMap.*;

public final class CharMap<V> extends AbstractMap<Character, V> implements _LibMap<CharMap.Entry<V>> {
	public static final class Entry<V> implements _LibEntry, Map.Entry<Character, V> {
		final char key;
		V value;

		public Entry(char key, V value) {this.key = key;this.value = value;}

		@Deprecated public Character getKey() {return key;}
		public char getCharKey() {return key;}
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

	public CharMap() {this(16);}
	public CharMap(int size) {ensureCapacity(size);}
	public CharMap(CharMap<V> map) {
		nextResize = map.nextResize;
		mask = map.mask;
		putAll(map);
	}

	public final void ensureCapacity(int size) {
		if (size <= mask) return;
		if (size > 16384) size = 16384;
		int length = MathUtils.getMin2PowerOf(size);

		if (entries != null) {
			mask = (length>>1) - 1;
			resize();
		} else {
			mask = length-1;
			nextResize = (int) (length * REFERENCE_LOAD_FACTOR);
		}
	}

	private static int hashCode(int i) {return ((i ^ (i >>> 7) ^ (i >>> 5) ^ (i >>> 3)) * 13);}

	// GenericMap interface
	@Override public final _LibEntry[] __entries() {return entries;}
	@Override public final void __remove(Entry<V> vEntry) {remove(vEntry.key);}
	// GenericMap interface

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
			if (entry.key == key) return entry;
			entry = entry.next;
		}
		return null;
	}

	@Override
	@Deprecated
	public final V put(Character key, V value) {return put((char) key, value);}
	public final V put(char key, V value) {
		Entry<V> entry = getOrCreateEntry(key);
		V oldV = entry.setValue(value);
		return oldV == UNDEFINED ? null : oldV;
	}

	@Override
	@Deprecated
	public final V remove(Object key) {return remove((char) key);}
	public final V remove(char key) {
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
	public final Set<Map.Entry<Character, V>> entrySet() {return Helpers.cast(selfEntrySet());}
	public final Set<Entry<V>> selfEntrySet() {return _LibEntrySet.create(this);}

	@Override
	@Deprecated
	public final V getOrDefault(Object key, V def) {return getOrDefault((char) key, def);}
	public final V getOrDefault(char k, V def) {
		Entry<V> entry = getEntry(k);
		return entry == null ? def : entry.value;}

	public final V computeIfAbsentC(char k, @NotNull IntFunction<V> function) {
		Entry<V> entry = getOrCreateEntry(k);
		if (entry.value == UNDEFINED) return entry.value = function.apply(k);
		return entry.value;
	}
	public final V computeIfAbsentS(char k, @NotNull Supplier<V> supplier) {
		Entry<V> entry = getOrCreateEntry(k);
		if (entry.value == UNDEFINED) return entry.value = supplier.get();
		return entry.value;
	}

	@SuppressWarnings("unchecked")
	private void resize() {
		int length = (mask+1) << 1;
		if (length > 32768) length = 32768;

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
	private Entry<V> getOrCreateEntry(char key) {
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
	private Entry<V> getFirst(char key, boolean create) {
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