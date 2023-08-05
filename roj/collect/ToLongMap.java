package roj.collect;

import roj.concurrent.FastThreadLocal;
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
		public Long getValue() { return v; }
		@Override
		public Long setValue(Long value) {
			long oldV = v;
			v = value;
			return oldV;
		}

		public long getLong() { return v; }
		public long setLong(long v) {
			long oldV = this.v;
			this.v = v;
			return oldV;
		}

		@Override
		public String toString() { return String.valueOf(k)+'='+v; }
	}

	public ToLongMap() { super(); }
	public ToLongMap(int size) { super(size); }
	public ToLongMap(int size, float loadFactor) { super(size, loadFactor); }
	public ToLongMap(MyHashMap<K, Long> map) { super(map); }

	public Set<Entry<K>> selfEntrySet() { return _Generic_EntrySet.create(this); }

	@Override
	public long applyAsLong(K key) { return getOrDefault(key, -1); }

	public long getLong(K key) { return getOrDefault(key, 0); }
	public long getOrDefault(K key, long def) {
		Entry<K> entry = (Entry<K>) getEntry(key);
		return entry == null ? def : entry.v;
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
		onPut(entry, val);
		entry.v = val;
		return oldV;
	}
	public boolean putLongIfAbsent(K key, int val) {
		Entry<K> entry = (Entry<K>) getOrCreateEntry(key);
		if (entry.k == UNDEFINED) {
			entry.k = key;
			entry.v = val;
			onPut(entry, val);
			return true;
		}
		return false;
	}

	public long removeLong(Object k) {
		Entry<K> entry = (Entry<K>) remove0(k, UNDEFINED);
		if (entry == null) return -1;
		long oldV = entry.v;
		reserveEntry(entry);
		return oldV;
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

	@Override
	protected final void onPut(AbstractEntry<K, Long> entry, Long newV) { onPut(entry, (long)newV); }
	protected void onPut(AbstractEntry<K, Long> entry, long newV) {}

	private static final FastThreadLocal<ObjectPool<AbstractEntry<?,?>>> MY_OBJECT_POOL = FastThreadLocal.withInitial(() -> new ObjectPool<>(null, 99));
	protected AbstractEntry<K, Long> useEntry() {
		AbstractEntry<K, Long> entry = Helpers.cast(MY_OBJECT_POOL.get().get());

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