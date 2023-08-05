package roj.collect;

import roj.concurrent.FastThreadLocal;
import roj.util.Helpers;

import java.util.Set;
import java.util.function.ToDoubleFunction;

import static roj.collect.IntMap.UNDEFINED;

/**
 * @author Roj234
 * @since 2021/5/31 1:21
 */
public class ToDoubleMap<K> extends MyHashMap<K, Double> implements ToDoubleFunction<K> {
	public static final class Entry<K> extends AbstractEntry<K, Double> {
		public double v;

		@Override
		public Double getValue() { return v; }
		@Override
		public Double setValue(Double value) {
			double oldV = v;
			v = value;
			return oldV;
		}

		public double getDouble() { return v; }
		public double setDouble(double v) {
			double oldV = this.v;
			this.v = v;
			return oldV;
		}

		@Override
		public String toString() { return String.valueOf(k)+'='+v; }
	}

	public ToDoubleMap() { super(); }
	public ToDoubleMap(int size) { super(size); }
	public ToDoubleMap(int size, float loadFactor) { super(size, loadFactor); }
	public ToDoubleMap(MyHashMap<K, Double> map) { super(map); }

	public Set<Entry<K>> selfEntrySet() { return _Generic_EntrySet.create(this); }

	@Override
	public double applyAsDouble(K value) { return getDouble(value); }

	public double getDouble(K key) { return getOrDefault(key, Double.NaN); }
	public double getOrDefault(K key, double def) {
		Entry<K> entry = (Entry<K>) getEntry(key);
		return entry == null ? def : entry.v;
	}
	public Double putDouble(K key, double val) {
		Entry<K> entry = (Entry<K>) getOrCreateEntry(key);
		Double oldV;
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
	public boolean putDoubleIfAbsent(K key, int val) {
		Entry<K> entry = (Entry<K>) getOrCreateEntry(key);
		if (entry.k == UNDEFINED) {
			entry.k = key;
			entry.v = val;
			onPut(entry, val);
			return true;
		}
		return false;
	}

	public double removeDouble(Object k) {
		Entry<K> entry = (Entry<K>) remove0(k, UNDEFINED);
		if (entry == null) return Double.NaN;
		double oldV = entry.v;
		reserveEntry(entry);
		return oldV;
	}

	@SuppressWarnings("unchecked")
	public boolean containsDoubleValue(double val) {
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
	protected final void onPut(AbstractEntry<K, Double> entry, Double newV) { onPut(entry, (double)newV); }
	protected void onPut(AbstractEntry<K, Double> entry, double newV) {}

	private static final FastThreadLocal<ObjectPool<AbstractEntry<?,?>>> MY_OBJECT_POOL = FastThreadLocal.withInitial(() -> new ObjectPool<>(null, 99));
	protected AbstractEntry<K, Double> useEntry() {
		AbstractEntry<K, Double> entry = Helpers.cast(MY_OBJECT_POOL.get().get());

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