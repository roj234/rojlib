package roj.collect;

import roj.util.Helpers;

import java.util.Comparator;
import java.util.Set;
import java.util.function.ToIntFunction;

import static roj.collect.IntMap.UNDEFINED;

public class ToIntMap<K> extends MyHashMap<K, Integer> implements ToIntFunction<K> {
	public static final class Entry<K> extends AbstractEntry<K, Integer> {
		public int v;

		public Entry() {}
		public Entry(K key, int val) {this.k = key;this.v = val;}

		@Override
		@Deprecated
		public Integer getValue() { return v; }
		@Override
		@Deprecated
		public Integer setValue(Integer value) {
			int oldV = v;
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
			return v ^ hash;
		}

		public static <T> Comparator<Entry<T>> comparator() {return (o1, o2) -> Integer.compare(o1.v, o2.v);}
		public static <T> Comparator<Entry<T>> reverseComparator() {return (o1, o2) -> Integer.compare(o2.v, o1.v);}
	}

	public static <T> ToIntMap<T> fromArray(T[] arr) {
		ToIntMap<T> map = new ToIntMap<>(arr.length);
		for (int i = 0; i < arr.length; i++) map.putInt(arr[i], i);
		return map;
	}

	public ToIntMap() { super(); }
	public ToIntMap(int size) { super(size); }
	public ToIntMap(MyHashMap<K, Integer> map) { super(map); }

	public final Set<Entry<K>> selfEntrySet() { return _Generic_EntrySet.create(this); }

	@Override
	public final int applyAsInt(K value) { return getOrDefault(value, -1); }
	public final int getInt(K key) { return getOrDefault(key, 0); }
	public final int getOrDefault(K key, int def) {
		Entry<K> entry = (Entry<K>) getEntry(key);
		return entry == null ? def : entry.v;
	}

	public int increment(K key, int i) {
		Entry<K> entry = (Entry<K>) getOrCreateEntry(key);
		if (entry.k == UNDEFINED) {
			entry.k = key;
			size++;
		}
		return entry.v += i;
	}

	public Integer putInt(K key, int val) {
		Entry<K> entry = (Entry<K>) getOrCreateEntry(key);
		Integer oldV;
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
	public boolean putIntIfAbsent(K key, int val) {
		Entry<K> entry = (Entry<K>) getOrCreateEntry(key);
		if (entry.k == UNDEFINED) {
			entry.k = key;
			entry.v = val;
			size++;
			return true;
		}
		return false;
	}

	public int putOrGet(K key, int val, int ifPut) {
		Entry<K> entry = (Entry<K>) getOrCreateEntry(key);
		if (entry.k == UNDEFINED) {
			entry.k = key;
			entry.v = val;
			size++;
			return ifPut;
		}
		return entry.v;
	}

	public final int removeInt(Object k) { return removeInt(k, -1); }
	public final int removeInt(Object k, int def) {
		Entry<K> entry = (Entry<K>) remove0(k, UNDEFINED);
		if (entry == null) return def;
		return entry.v;
	}

	@SuppressWarnings("unchecked")
	public boolean containsIntValue(int val) {
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

	protected AbstractEntry<K, Integer> useEntry() {
		AbstractEntry<K, Integer> entry = new Entry<>();
		entry.k = Helpers.cast(UNDEFINED);
		return entry;
	}
}