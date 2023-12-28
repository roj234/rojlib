package roj.collect;

import roj.concurrent.FastThreadLocal;
import roj.math.MathUtils;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntFunction;

import static roj.collect.IntMap.UNDEFINED;

public final class CharMap<V> extends AbstractMap<Character, V> implements _Generic_Map<CharMap.Entry<V>> {
	@SuppressWarnings("unchecked")
	public void putAll(CharMap<V> map) {
		if (map.entries == null) return;
		this.ensureCapacity(size + map.size());
		for (int i = 0; i < map.length; i++) {
			Entry<?> entry = map.entries[i];
			while (entry != null) {
				put(entry.k, (V) entry.v);

				entry = entry.next;
			}
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public void putAll(@Nonnull Map<? extends Character, ? extends V> m) {
		if (m instanceof CharMap) {
			putAll((CharMap<V>) m);
		} else {
			super.putAll(m);
		}
	}

	public static class Entry<V> implements _Generic_Entry, Map.Entry<Character, V> {
		char k;
		Object v;

		public char getChar() { return k; }
		@Override
		@Deprecated
		public Character getKey() { return k; }

		@SuppressWarnings("unchecked")
		public V getValue() { return (V) v; }

		@SuppressWarnings("unchecked")
		public V setValue(V now) {
			Object v = this.v;
			this.v = now;
			return (V) v;
		}

		Entry<V> next;
		@Override
		public _Generic_Entry __next() { return next; }
	}

	Entry<?>[] entries;
	int size = 0;

	int length = 1, mask;
	float loadFactor = 1f;

	public CharMap() {
		this(16);
	}

	public CharMap(int size) {
		ensureCapacity(size);
	}

	public CharMap(int size, float loadFactor) {
		ensureCapacity(size);
		this.loadFactor = loadFactor;
	}

	@SuppressWarnings("unchecked")
	public CharMap(CharMap<V> map) {
		ensureCapacity(map.size);
		this.loadFactor = map.loadFactor;
		if (map.size() == 0) return;

		this.entries = new Entry<?>[map.entries.length];
		for (int i = 0; i < this.entries.length; i++) {
			this.entries[i] = cloneNode((Entry<V>) map.entries[i]);
		}
		this.size = map.size();
	}

	private Entry<V> cloneNode(Entry<V> entry) {
		if (entry == null) return null;
		Entry<V> newEntry = useEntry(entry.k, entry.getValue());
		Entry<V> head = newEntry;
		while (entry.next != null) {
			entry = entry.next;
			newEntry.next = useEntry(entry.k, entry.getValue());
			newEntry = newEntry.next;
		}
		return head;
	}

	public void ensureCapacity(int size) {
		if (size < length) return;
		length = MathUtils.getMin2PowerOf(size);
		mask = length-1;

		if (this.entries != null) resize();
	}

	public Set<Entry<V>> selfEntrySet() { return _Generic_EntrySet.create(this); }

	@Nonnull
	@Override
	public Set<Map.Entry<Character, V>> entrySet() { return Helpers.cast(selfEntrySet()); }

	@Override
	public boolean containsKey(Object key) {
		return containsKey((char) key);
	}

	@Override
	public V get(Object key) {
		return get((char) key);
	}

	@Override
	public V put(Character key, V value) {
		return put((char) key, value);
	}

	@Override
	public V remove(Object key) {
		return remove((char) key);
	}

	public int size() { return size; }

	@Override
	public _Generic_Entry[] __entries() { return entries; }
	@Override
	public void __remove(Entry<V> vEntry) { remove(vEntry.k); }

	@Nonnull
	public V computeIfAbsent(char k, @Nonnull IntFunction<V> supplier) {
		V v = get(k);
		if (v == null) {
			put(k, v = supplier.apply(k));
		}
		return v;
	}

	@SuppressWarnings("unchecked")
	private void resize() {
		Entry<?>[] newEntries = new Entry<?>[length];
		int newMask = length-1;
		Entry<V> entry;
		Entry<V> next;
		int i = 0, j = entries.length;
		for (; i < j; i++) {
			entry = (Entry<V>) entries[i];
			while (entry != null) {
				next = entry.next;
				int newKey = charHash(entry.k)&newMask;
				Entry<V> entry2 = (Entry<V>) newEntries[newKey];
				newEntries[newKey] = entry;
				entry.next = entry2;
				entry = next;
			}
		}

		this.entries = newEntries;
		this.mask = newMask;
	}

	public V put(char key, V e) {
		Entry<V> entry = getOrCreateEntry(key);
		V oldV = entry.setValue(e);
		if (oldV == UNDEFINED) {
			oldV = null;
			if (++size > length * loadFactor) {
				length <<= 1;
				resize();
			}
		}
		return oldV;
	}

	@SuppressWarnings("unchecked")
	public V remove(char id) {
		Entry<V> prevEntry = null;
		Entry<V> entry = getEntryFirst(id, false);
		while (entry != null) {
			if (entry.k == id) {
				break;
			}
			prevEntry = entry;
			entry = entry.next;
		}

		if (entry == null) return null;

		size--;

		if (prevEntry != null) {
			prevEntry.next = entry.next;
		} else {
			entries[charHash(id)&mask] = entry.next;
		}

		V v = (V) entry.v;
		reserveEntry(entry);
		return v;
	}

	@SuppressWarnings("unchecked")
	public boolean containsValue(Object v) {
		return getEntry((V) v) != null;
	}
	public boolean containsKey(char i) {
		return getEntry(i) != null;
	}

	@SuppressWarnings("unchecked")
	private Entry<V> getEntry(V v) {
		if (entries == null) return null;
		for (Entry<?> entry : entries) {
			if (entry == null) continue;
			while (entry != null) {
				if (Objects.equals(v, entry.getValue()))
					return (Entry<V>) entry;
				entry = entry.next;
			}
		}
		return null;
	}

	private Entry<V> getEntry(char id) {
		Entry<V> entry = getEntryFirst(id, false);
		while (entry != null) {
			if (entry.k == id) return entry;
			entry = entry.next;
		}
		return null;
	}

	private Entry<V> getOrCreateEntry(char id) {
		Entry<V> entry = getEntryFirst(id, true);
		while (true) {
			if (entry.k == id) return entry;
			if (entry.next == null) break;
			entry = entry.next;
		}

		return entry.next = useEntry(id, UNDEFINED);
	}

	private int charHash(int id) {
		return ((id ^ (id >>> 7) ^ (id >>> 5) ^ (id >>> 3)) * 13 );
	}

	@SuppressWarnings("unchecked")
	private Entry<V> getEntryFirst(char k, boolean create) {
		int id = charHash(k)&mask;
		if (entries == null) {
			if (!create) return null;
			entries = new Entry<?>[length];
		}
		Entry<V> entry;
		if ((entry = (Entry<V>) entries[id]) == null) {
			if (!create) return null;
			return (Entry<V>) (entries[id] = useEntry(k, UNDEFINED));
		}
		return entry;
	}

	public V get(char id) {
		Entry<V> entry = getEntry(id);
		return entry == null ? null : entry.getValue();
	}

	public void clear() {
		if (size == 0) return;
		size = 0;
		if (entries != null) {
			Entry<?>[] ent = entries;
			for (int i = 0; i < ent.length; i++) {
				if (ent[i] != null) {
					reserveEntry(ent[i]);
					ent[i] = null;
				}
			}
		}
	}

	private static final FastThreadLocal<ObjectPool<CharMap.Entry<?>>> MY_OBJECT_POOL = FastThreadLocal.withInitial(() -> new ObjectPool<>(null, 99));
	private CharMap.Entry<V> useEntry(char k, Object val) {
		CharMap.Entry<V> entry = Helpers.cast(MY_OBJECT_POOL.get().get());
		if (entry == null) entry = new CharMap.Entry<>();
		entry.k = k;
		entry.v = Helpers.cast(val);
		return entry;
	}
	private void reserveEntry(CharMap.Entry<?> entry) {
		entry.v = null;
		entry.next = null;
		MY_OBJECT_POOL.get().reserve(entry);
	}
}