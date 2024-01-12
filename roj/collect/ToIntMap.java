package roj.collect;

import roj.concurrent.FastThreadLocal;
import roj.util.Helpers;

import java.util.Set;
import java.util.function.ToIntFunction;

import static roj.collect.IntMap.UNDEFINED;

public class ToIntMap<K> extends MyHashMap<K, Integer> implements ToIntFunction<K> {
	public static final class Entry<K> extends AbstractEntry<K, Integer> {
		public int v;

		public Entry() {}
		public Entry(K key, int val) {
			this.k = key;
			this.v = val;
		}

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
	}

	public ToIntMap() { super(); }
	public ToIntMap(int size) { super(size); }
	public ToIntMap(int size, float loadFactor) { super(size, loadFactor); }
	public ToIntMap(MyHashMap<K, Integer> map) { super(map); }

	public Set<Entry<K>> selfEntrySet() { return _Generic_EntrySet.create(this); }

	@Override
	public int applyAsInt(K value) { return getOrDefault(value, -1); }
	public int getInt(K key) { return getOrDefault(key, 0); }
	public int getOrDefault(K key, int def) {
		Entry<K> entry = (Entry<K>) getEntry(key);
		return entry == null ? def : entry.v;
	}

	public int increase(K key, int i) {
		Entry<K> entry = (Entry<K>) getOrCreateEntry(key);
		if (entry.k == UNDEFINED) {
			entry.k = key;
			size++;
			onPut(entry, entry.v+i);
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
		onPut(entry, val);
		entry.v = val;
		return oldV;
	}
	public boolean putIntIfAbsent(K key, int val) {
		Entry<K> entry = (Entry<K>) getOrCreateEntry(key);
		if (entry.k == UNDEFINED) {
			entry.k = key;
			size++;

			onPut(entry, val);
			entry.v = val;
			return true;
		}
		return false;
	}

	public int removeInt(Object k) {
		Entry<K> entry = (Entry<K>) remove0(k, UNDEFINED);
		if (entry == null) return -1;
		int oldV = entry.v;
		reserveEntry(entry);
		return oldV;
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

	@Override
	protected final void onPut(AbstractEntry<K, Integer> entry, Integer newV) { onPut(entry, (int)newV); }
	protected void onPut(AbstractEntry<K, Integer> entry, int newV) {}

	private static final FastThreadLocal<ObjectPool<AbstractEntry<?,?>>> MY_OBJECT_POOL = FastThreadLocal.withInitial(() -> new ObjectPool<>(null, 99));
	protected AbstractEntry<K, Integer> useEntry() {
		AbstractEntry<K, Integer> entry = Helpers.cast(MY_OBJECT_POOL.get().get());

		if (entry == null) entry = new Entry<>();
		entry.k = Helpers.cast(UNDEFINED);
		return entry;
	}
	protected void reserveEntry(AbstractEntry<?, ?> entry) {
		entry.k = null;
		entry.next = null;
		MY_OBJECT_POOL.get().reserve(entry);
	}
}