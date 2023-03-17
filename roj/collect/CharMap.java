package roj.collect;

import roj.math.MathUtils;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.IntFunction;

import static roj.collect.IntMap.MAX_NOT_USING;
import static roj.collect.IntMap.UNDEFINED;

public final class CharMap<V> extends AbstractMap<Character, V> implements MapLike<CharMap.Entry<V>> {
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

	public static class Entry<V> implements MapLikeEntry<Entry<V>>, Map.Entry<Character, V> {
		char k;
		Object v;

		Entry(char k, Object v) {
			this.k = k;
			this.v = v;
		}

		public char getChar() {
			return k;
		}

		@Override
		@Deprecated
		public Character getKey() {
			return k;
		}

		@SuppressWarnings("unchecked")
		public V getValue() {
			return (V) v;
		}

		@SuppressWarnings("unchecked")
		public V setValue(V now) {
			Object v = this.v;
			this.v = now;
			return (V) v;
		}

		Entry<V> next;

		@Override
		public Entry<V> nextEntry() {
			return next;
		}
	}

	Entry<?>[] entries;
	int size = 0;

	Entry<V> notUsing = null;

	int length = 1;
	float loadFactor = 0.8f;

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
		Entry<V> newEntry = getCachedEntry(entry.k, entry.getValue());
		Entry<V> head = newEntry;
		while (entry.next != null) {
			entry = entry.next;
			newEntry.next = getCachedEntry(entry.k, entry.getValue());
			newEntry = newEntry.next;
		}
		return head;
	}

	public void ensureCapacity(int size) {
		if (size < length) return;
		length = MathUtils.getMin2PowerOf(size);

		if (this.entries != null) resize();
	}

	public Set<Entry<V>> selfEntrySet() {
		return new EntrySet<>(this);
	}

	@Nonnull
	@Override
	public Set<Map.Entry<Character, V>> entrySet() {
		return Helpers.cast(selfEntrySet());
	}

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

	public int size() {
		return size;
	}

	@Override
	public void removeEntry0(Entry<V> vEntry) {
		remove(vEntry.k);
	}

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
		Entry<V> entry;
		Entry<V> next;
		int i = 0, j = entries.length;
		for (; i < j; i++) {
			entry = (Entry<V>) entries[i];
			while (entry != null) {
				next = entry.next;
				int newKey = indexFor(entry.k);
				Entry<V> entry2 = (Entry<V>) newEntries[newKey];
				newEntries[newKey] = entry;
				entry.next = entry2;
				entry = next;
			}
		}

		this.entries = newEntries;
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
			entries[indexFor(id)] = entry.next;
		}

		V v = (V) entry.v;

		putRemovedEntry(entry);

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
		for (int i = 0; i < length; i++) {
			Entry<V> entry = (Entry<V>) entries[i];
			if (entry == null) continue;
			while (entry != null) {
				if (Objects.equals(v, entry.getValue())) {
					return entry;
				}
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

		return entry.next = getCachedEntry(id, UNDEFINED);
	}

	private Entry<V> getCachedEntry(char id, Object value) {
		Entry<V> cached = this.notUsing;
		if (cached != null) {
			cached.k = id;
			cached.v = value;
			this.notUsing = cached.next;
			cached.next = null;
			return cached;
		}

		return new Entry<>(id, value);
	}

	private void putRemovedEntry(Entry<V> entry) {
		if (notUsing != null && notUsing.k > MAX_NOT_USING) {
			return;
		}
		entry.next = notUsing;
		entry.k = (char) (notUsing == null ? 1 : notUsing.k + 1);
		notUsing = entry;
	}

	private int indexFor(int id) {
		return (id ^ (id >>> 16)) & (length - 1);
	}

	@SuppressWarnings("unchecked")
	private Entry<V> getEntryFirst(char k, boolean create) {
		int id = indexFor(k);
		if (entries == null) {
			if (!create) return null;
			entries = new Entry<?>[length];
		}
		Entry<V> entry;
		if ((entry = (Entry<V>) entries[id]) == null) {
			if (!create) return null;
			return (Entry<V>) (entries[id] = getCachedEntry(k, UNDEFINED));
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
			if (notUsing == null || notUsing.k < MAX_NOT_USING) {
				for (int i = 0; i < length; i++) {
					if (entries[i] != null) {
						putRemovedEntry(Helpers.cast(entries[i]));
						entries[i] = null;
					}
				}
			} else {Arrays.fill(entries, null);}
		}
	}

	static final class EntrySet<V> extends AbstractSet<Entry<V>> {
		private final CharMap<V> map;

		private EntrySet(CharMap<V> map) {
			this.map = map;
		}

		public final int size() {
			return map.size();
		}

		public final void clear() {
			map.clear();
		}

		public final Iterator<Entry<V>> iterator() {
			return isEmpty() ? Collections.emptyIterator() : new EntryItr<>(map.entries, map);
		}

		public final boolean contains(Object o) {
			if (!(o instanceof CharMap.Entry)) return false;
			Entry<?> e = (Entry<?>) o;
			char key = e.getChar();
			Entry<?> comp = map.getEntry(key);
			return comp != null && comp.v == e.v;
		}

		public final boolean remove(Object o) {
			if (o instanceof Map.Entry) {
				CharMap.Entry<?> e = (CharMap.Entry<?>) o;
				return map.remove(e.k) != null;
			}
			return false;
		}

		public final Spliterator<CharMap.Entry<V>> spliterator() {
			return Spliterators.spliterator(map.entries, 0);
		}
	}
}