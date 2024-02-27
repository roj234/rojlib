package roj.collect;

import roj.util.Helpers;

import java.util.Set;
import java.util.function.ToLongFunction;

import static roj.collect.IntMap.UNDEFINED;

/**
 * @author Roj234
 * @since 2021/6/18 11:6
 */
public class ToLongMap<K> extends MyHashMap<K, Long> implements ToLongFunction<K> {
	public static final class Entry<K> extends AbstractEntry<K, Long> {
		public long v;

		@Override
		@Deprecated
		public Long getValue() { return v; }
		@Override
		@Deprecated
		public Long setValue(Long value) {
			long oldV = v;
			v = value;
			return oldV;
		}

		@Override
		public String toString() { return String.valueOf(k)+'='+v; }
		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			var entry = (Entry<?>) o;
			if (!(k != null ? k.equals(entry.k) : entry.k == null)) return false;
			return v == entry.v;
		}
		@Override
		public int hashCode() {
			int hash = k != null ? k.hashCode() : 0;
			return (int)(v ^ v >>> 32) ^ hash;
		}
	}

	public ToLongMap() { super(); }
	public ToLongMap(int size) { super(size); }
	public ToLongMap(MyHashMap<K, Long> map) { super(map); }

	public final Set<Entry<K>> selfEntrySet() { return _Generic_EntrySet.create(this); }

	@Override
	public final long applyAsLong(K key) { return getOrDefault(key, -1); }
	public final long getLong(K key) { return getOrDefault(key, 0); }
	public final long getOrDefault(K key, long def) {
		Entry<K> entry = (Entry<K>) getEntry(key);
		return entry == null ? def : entry.v;
	}

	public long increment(K key, long i) {
		Entry<K> entry = (Entry<K>) getOrCreateEntry(key);
		if (entry.k == UNDEFINED) {
			entry.k = key;
			size++;
		}
		return entry.v += i;
	}

	public Long putLong(K key, long val) {
		Entry<K> entry = (Entry<K>) getOrCreateEntry(key);
		Long oldV;
		if (entry.k == UNDEFINED) {
			entry.k = key;
			size++;

			oldV = null;
		} else {
			oldV = entry.v;
		}

		entry.v = val;
		return oldV;
	}
	public boolean putLongIfAbsent(K key, int val) {
		Entry<K> entry = (Entry<K>) getOrCreateEntry(key);
		if (entry.k == UNDEFINED) {
			entry.k = key;
			entry.v = val;
			size++;
			return true;
		}
		return false;
	}

	public final long removeLong(Object k) { return removeLong(k, -1); }
	public long removeLong(Object k, int def) {
		Entry<K> entry = (Entry<K>) remove0(k, UNDEFINED);
		if (entry == null) return def;
		return entry.v;
	}

	@SuppressWarnings("unchecked")
	public boolean containsLongValue(long val) {
		AbstractEntry<?, ?>[] ent = entries;
		if (ent == null) return false;
		for (int i = ent.length - 1; i >= 0; i--) {
			Entry<K> entry = (Entry<K>) ent[i];
			while (entry != null) {
				if (entry.v == val) return true;
				entry = (Entry<K>) entry.next;
			}
		}
		return false;
	}

	protected AbstractEntry<K, Long> useEntry() {
		AbstractEntry<K, Long> entry = new Entry<>();
		entry.k = Helpers.cast(UNDEFINED);
		return entry;
	}
}