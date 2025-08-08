package roj.collect;

import roj.util.Helpers;

import java.util.Set;
import java.util.function.ToLongFunction;

import static roj.collect.IntMap.UNDEFINED;

/**
 * @author Roj234
 * @since 2021/6/18 11:6
 */
public class ToLongMap<K> extends HashMap<K, Long> implements ToLongFunction<K> {
	public static final class Entry<K> extends AbstractEntry<K, Long> {
		public long value;

		@Override
		@Deprecated
		public Long getValue() { return value; }
		@Override
		@Deprecated
		public Long setValue(Long value) {
			long oldV = this.value;
			this.value = value;
			return oldV;
		}

		@Override
		public String toString() { return String.valueOf(key)+'='+value; }
		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			var entry = (Entry<?>) o;
			if (!(key != null ? key.equals(entry.key) : entry.key == null)) return false;
			return value == entry.value;
		}
		@Override
		public int hashCode() {
			int hash = key != null ? key.hashCode() : 0;
			return (int)(value ^ value >>> 32) ^ hash;
		}
	}

	public ToLongMap() { super(); }
	public ToLongMap(int size) { super(size); }
	public ToLongMap(HashMap<K, Long> map) { super(map); }

	public final Set<Entry<K>> selfEntrySet() { return _LibEntrySet.create(this); }

	@Override
	public final long applyAsLong(K key) { return getOrDefault(key, -1); }
	public final long getLong(K key) { return getOrDefault(key, 0); }
	public final long getOrDefault(K key, long def) {
		Entry<K> entry = (Entry<K>) getEntry(key);
		return entry == null ? def : entry.value;
	}

	public long increment(K key, long i) {
		Entry<K> entry = (Entry<K>) getOrCreateEntry(key);
		if (entry.key == UNDEFINED) {
			entry.key = key;
			size++;
		}
		return entry.value += i;
	}

	public Long putLong(K key, long val) {
		Entry<K> entry = (Entry<K>) getOrCreateEntry(key);
		Long oldV;
		if (entry.key == UNDEFINED) {
			entry.key = key;
			size++;

			oldV = null;
		} else {
			oldV = entry.value;
		}

		entry.value = val;
		return oldV;
	}
	public boolean putLongIfAbsent(K key, int val) {
		Entry<K> entry = (Entry<K>) getOrCreateEntry(key);
		if (entry.key == UNDEFINED) {
			entry.key = key;
			entry.value = val;
			size++;
			return true;
		}
		return false;
	}

	public final long removeLong(Object k) { return removeLong(k, -1); }
	public long removeLong(Object k, int def) {
		Entry<K> entry = (Entry<K>) remove0(k, UNDEFINED);
		if (entry == null) return def;
		return entry.value;
	}

	@SuppressWarnings("unchecked")
	public boolean containsLongValue(long val) {
		AbstractEntry<?, ?>[] ent = entries;
		if (ent == null) return false;
		for (int i = ent.length - 1; i >= 0; i--) {
			Entry<K> entry = (Entry<K>) ent[i];
			while (entry != null) {
				if (entry.value == val) return true;
				entry = (Entry<K>) entry.next;
			}
		}
		return false;
	}

	protected AbstractEntry<K, Long> useEntry() {
		AbstractEntry<K, Long> entry = new Entry<>();
		entry.key = Helpers.cast(UNDEFINED);
		return entry;
	}
}