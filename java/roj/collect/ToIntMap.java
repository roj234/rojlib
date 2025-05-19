package roj.collect;

import roj.util.Helpers;

import java.util.Comparator;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import static roj.collect.IntMap.UNDEFINED;

public class ToIntMap<K> extends MyHashMap<K, Integer> implements ToIntFunction<K> {
	public static final class Entry<K> extends AbstractEntry<K, Integer> {
		public int value;

		public Entry() {}
		public Entry(K key, int val) {this.key = key;this.value = val;}

		@Override
		@Deprecated
		public Integer getValue() { return value; }
		@Override
		@Deprecated
		public Integer setValue(Integer value) {
			int oldV = this.value;
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
			return value ^ hash;
		}

		public static <T> Comparator<Entry<T>> comparator() {return (o1, o2) -> Integer.compare(o1.value, o2.value);}
		public static <T> Comparator<Entry<T>> reverseComparator() {return (o1, o2) -> Integer.compare(o2.value, o1.value);}
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
	public final int applyAsInt(K value) { return getInt(value); }
	public final int getInt(Object key) { return getOrDefault(key, -1); }
	public final int getOrDefault(Object key, int def) {
		Entry<K> entry = (Entry<K>) getEntry(key);
		return entry == null ? def : entry.value;
	}

	/**
	 * 增加key计数器i
	 * 如果需要排序，可以看一下常数时间的实现：{@link LFUCache#increment(Object, Function)}
	 */
	public int increment(K key, int i) {
		Entry<K> entry = (Entry<K>) getOrCreateEntry(key);
		if (entry.key == UNDEFINED) {
			entry.key = key;
			size++;
		}
		return entry.value += i;
	}

	public Integer putInt(K key, int val) {
		Entry<K> entry = (Entry<K>) getOrCreateEntry(key);
		Integer oldV;
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
	public boolean putIntIfAbsent(K key, int val) {
		Entry<K> entry = (Entry<K>) getOrCreateEntry(key);
		if (entry.key == UNDEFINED) {
			entry.key = key;
			entry.value = val;
			size++;
			return true;
		}
		return false;
	}

	public int putOrGet(K key, int val, int ifPut) {
		Entry<K> entry = (Entry<K>) getOrCreateEntry(key);
		if (entry.key == UNDEFINED) {
			entry.key = key;
			entry.value = val;
			size++;
			return ifPut;
		}
		return entry.value;
	}

	public final int removeInt(Object k) { return removeInt(k, -1); }
	public final int removeInt(Object k, int def) {
		Entry<K> entry = (Entry<K>) remove0(k, UNDEFINED);
		if (entry == null) return def;
		return entry.value;
	}

	@SuppressWarnings("unchecked")
	public boolean containsIntValue(int val) {
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

	protected AbstractEntry<K, Integer> useEntry() {
		AbstractEntry<K, Integer> entry = new Entry<>();
		entry.key = Helpers.cast(UNDEFINED);
		return entry;
	}
}