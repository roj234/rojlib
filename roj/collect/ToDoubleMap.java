package roj.collect;

import roj.math.MathUtils;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.ToDoubleFunction;

import static roj.collect.IntMap.MAX_NOT_USING;
import static roj.collect.IntMap.UNDEFINED;

/**
 * @author Roj234
 * @since 2021/5/31 1:21
 */
public class ToDoubleMap<K> extends AbstractMap<K, Double> implements _Generic_Map<ToDoubleMap.Entry<K>>, ToDoubleFunction<K> {
	@Override
	public double applyAsDouble(K value) {
		return getDouble(value);
	}

	public double getDouble(K key) {
		return getOrDefault(key, Double.NaN);
	}

	public double getOrDefault(K key, double l) {
		Entry<K> entry = getEntry(key);
		return entry == null ? l : entry.v;
	}

	public static class Entry<K> implements _Generic_Entry<Entry<K>>, Map.Entry<K, Double> {
		public K k;
		public double v;

		protected Entry(K k, double v) {
			this.k = k;
			this.v = v;
		}

		public K getKey() {
			return k;
		}

		@Override
		public Double getValue() {
			return this.v;
		}

		@Override
		public Double setValue(Double value) {
			double ov = v;
			v = value;
			return ov;
		}

		public double getDouble() {
			return v;
		}

		public double setDouble(double v) {
			double old = this.v;
			this.v = v;
			return old;
		}

		public Entry<K> next;

		@Override
		public Entry<K> __next() {
			return next;
		}

		@Override
		public String toString() {
			return String.valueOf(k) + '=' + v;
		}
	}

	protected Entry<?>[] entries;
	protected int size = 0;

	int length = 2;
	protected int mask = 1;

	float loadFactor = 0.8f;

	public ToDoubleMap() {
		this(16);
	}

	public ToDoubleMap(int size) {
		ensureCapacity(size);
	}

	public ToDoubleMap(int size, float loadFactor) {
		ensureCapacity(size);
		this.loadFactor = loadFactor;
	}

	public ToDoubleMap(ToDoubleMap<K> map) {
		this.putAll(map);
	}

	public void ensureCapacity(int size) {
		if (size < length) return;
		length = MathUtils.getMin2PowerOf(size);
		mask = length - 1;

		if (this.entries != null) resize();
	}

	public int size() {
		return size;
	}

	@Nonnull
	public Set<Map.Entry<K, Double>> entrySet() { return Helpers.cast(selfEntrySet()); }
	public Set<Entry<K>> selfEntrySet() { return _Generic_EntrySet.create(this); }
	public _Generic_Entry<?>[] __entries() { return entries; }
	public void __remove(Entry<K> entry) { remove(entry.k); }

	@SuppressWarnings("unchecked")
	public void putAll(@Nonnull Map<? extends K, ? extends Double> otherMap) {
		if (otherMap instanceof ToDoubleMap) {
			putAll((ToDoubleMap<K>) otherMap);
		} else {
			super.putAll(otherMap);
		}
	}

	@SuppressWarnings("unchecked")
	public void putAll(ToDoubleMap<K> otherMap) {
		for (int i = 0; i < otherMap.length; i++) {
			Entry<K> entry = (Entry<K>) otherMap.entries[i];
			if (entry == null) continue;
			while (entry != null) {
				this.putDouble(entry.k, entry.v);
				entry = entry.next;
			}
		}
	}

	@SuppressWarnings("unchecked")
	protected void resize() {
		Entry<?>[] newEntries = new Entry<?>[length];
		Entry<K> entry;
		Entry<K> next;
		int i = 0, j = entries.length;
		for (; i < j; i++) {
			entry = (Entry<K>) entries[i];
			entries[i] = null;
			while (entry != null) {
				next = entry.next;
				int newKey = indexFor(entry.k);
				Entry<K> old = (Entry<K>) newEntries[newKey];
				newEntries[newKey] = entry;
				entry.next = old;
				entry = next;
			}
		}

		this.entries = newEntries;
	}

	@Override
	public Double put(K key, Double value) {
		return putDouble(key, value);
	}

	public Double putDouble(K key, double e) {
		if (size > length * loadFactor) {
			length <<= 1;
			mask = length - 1;
			resize();
		}

		Entry<K> entry = getOrCreateEntry(key);
		Double oldValue = entry.v;
		if (entry.k == UNDEFINED) {
			oldValue = null;
			entry.k = key;
			afterPut(key, e);
			size++;
		}
		afterChange(key, oldValue, entry.v = e);
		return oldValue;
	}

	void afterPut(K key, double val) {}
	void afterChange(K key, Double original, double now) {}
	void afterRemove(Entry<K> entry) {}

	@SuppressWarnings("unchecked")
	public Double remove(Object o) {
		K id = (K) o;
		Entry<K> prevEntry = null;
		Entry<K> toRemove = null;
		{
			Entry<K> entry = getEntryFirst(id, false);
			while (entry != null) {
				if (Objects.equals(entry.k, id)) {
					toRemove = entry;
					break;
				}
				prevEntry = entry;
				entry = entry.next;
			}
		}

		if (toRemove == null) return null;

		afterRemove(toRemove);

		this.size--;

		if (prevEntry != null) {
			prevEntry.next = toRemove.next;
		} else {
			this.entries[indexFor(id)] = toRemove.next;
		}

		double v = toRemove.v;

		putRemovedEntry(toRemove);

		return v;
	}

	public boolean containsValue(Object e) {
		return containsDoubleValue((Double) e);
	}

	public boolean containsDoubleValue(double e) {
		return getValueEntry(e) != null;
	}

	protected Entry<K> notUsing = null;

	protected Entry<K> getCachedEntry(K id, double value) {
		Entry<K> cached = this.notUsing;
		if (cached != null) {
			cached.k = id;
			cached.v = value;
			this.notUsing = cached.next;
			cached.next = null;
			return cached;
		}

		return new Entry<>(id, value);
	}

	protected void putRemovedEntry(Entry<K> entry) {
		if (notUsing != null && notUsing.v > MAX_NOT_USING) {
			return;
		}
		entry.k = null;
		entry.v = notUsing == null ? 1 : notUsing.v + 1;
		entry.next = notUsing;
		notUsing = entry;
	}


	@SuppressWarnings("unchecked")
	protected Entry<K> getValueEntry(double value) {
		if (entries == null) return null;
		for (int i = 0; i < length; i++) {
			Entry<K> entry = (Entry<K>) entries[i];
			if (entry == null) continue;
			while (entry != null) {
				if (value == entry.v) {
					return entry;
				}
				entry = entry.next;
			}
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	public boolean containsKey(Object i) {
		return getEntry((K) i) != null;
	}

	public Entry<K> getEntry(K id) {
		Entry<K> entry = getEntryFirst(id, false);
		while (entry != null) {
			if (Objects.equals(id, entry.k)) {
				return entry;
			}
			entry = entry.next;
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	protected Entry<K> getOrCreateEntry(K id) {
		Entry<K> entry = getEntryFirst(id, true);
		if (entry.k == UNDEFINED) return entry;
		while (true) {
			if (Objects.equals(id, entry.k)) return entry;
			if (entry.next == null) break;
			entry = entry.next;
		}
		Entry<K> firstUnused = getCachedEntry((K) UNDEFINED, 0);
		entry.next = firstUnused;
		return firstUnused;
	}

	int indexFor(K id) {
		int v;
		return id == null ? 0 : ((v = id.hashCode()) ^ (v >>> 16)) & mask;
	}

	@SuppressWarnings("unchecked")
	protected Entry<K> getEntryFirst(K id, boolean create) {
		int i = indexFor(id);
		if (entries == null) {
			if (!create) return null;
			entries = new Entry<?>[length];
		}
		Entry<K> entry;
		if ((entry = (Entry<K>) entries[i]) == null) {
			if (!create) return null;
			entries[i] = entry = getCachedEntry((K) UNDEFINED, 0);
		}
		return entry;
	}

	@SuppressWarnings("unchecked")
	public Double get(Object id) {
		Entry<K> entry = getEntry((K) id);
		return entry == null ? null : entry.v;
	}

	public void clear() {
		if (size == 0) return;
		size = 0;
		if (entries != null) {
			if (notUsing == null || notUsing.v < MAX_NOT_USING) {
				for (int i = 0; i < length; i++) {
					if (entries[i] != null) {
						putRemovedEntry(Helpers.cast(entries[i]));
						entries[i] = null;
					}
				}
			} else {Arrays.fill(entries, null);}
		}
	}
}