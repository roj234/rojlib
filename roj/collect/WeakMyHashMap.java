package roj.collect;

import roj.math.MathUtils;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.*;

/**
 * @author Roj234
 * @since 2021/5/27 0:8
 */
public class WeakMyHashMap<K, V> extends AbstractMap<K, V> implements MapLike<WeakMyHashMap.Entry<V>> {
	protected final ReferenceQueue<Object> queue = new ReferenceQueue<>();

	public static class Entry<V> extends WeakReference<Object> implements MapLikeEntry<Entry<V>>, Map.Entry<Object, V> {
		public Entry(ReferenceQueue<Object> queue, Object referent) {
			super(referent, queue);
		}

		private V v;
		private int hash;
		private Entry<V> next;

		@Override
		public Entry<V> nextEntry() {
			return next;
		}

		@Override
		public Object getKey() {
			return get();
		}

		@Override
		public V getValue() {
			return v;
		}

		@Override
		public V setValue(V value) {
			V v1 = v;
			v = value;
			return v1;
		}
	}

	protected Entry<?>[] entries;
	protected int size = 0;

	int length = 2;

	public WeakMyHashMap() {
		this(16);
	}

	/**
	 * @param size 初始化大小
	 */
	public WeakMyHashMap(int size) {
		ensureCapacity(size);
	}

	public void ensureCapacity(int size) {
		if (size <= 0) throw new NegativeArraySizeException(String.valueOf(size));
		if (size < length) return;
		length = MathUtils.getMin2PowerOf(size);

		if (entries != null) resize();
	}

	@Override
	public int size() {
		return size;
	}

	@Override
	public void removeEntry0(Entry<V> entry) {
		remove(entry.get());
	}

	public void resize() {
		Entry<?>[] newEntries = new Entry<?>[length];
		Entry<?> entry;
		Entry<?> next;
		int i = 0, j = entries.length;
		for (; i < j; i++) {
			entry = entries[i];
			entries[i] = null;
			while (entry != null) {
				next = entry.next;
				int newKey = indexFor(entry.hash);
				Entry<?> old = newEntries[newKey];
				newEntries[newKey] = entry;
				entry.next = Helpers.cast(old);
				entry = next;
			}
		}

		this.entries = newEntries;
	}

	@Override
	@SuppressWarnings("unchecked")
	public V put(K key, V val) {
		doEvict();

		int hash = key.hashCode();
		int index = indexFor(hash);
		Entry<V> result;
		if (entries == null) entries = new Entry<?>[length];
		result = (Entry<V>) entries[index];

		if (size > length * 0.8f) {
			length <<= 1;
			resize();
		}

		if (result == null) {
			Entry<Object> entry = new Entry<>(queue, key);
			entry.v = val;
			(entries[index] = entry).hash = hash;
			size++;
			return null;
		}

		while (true) {
			if (result.equals(key)) {
				V prev = result.v;
				result.v = val;
				return prev;
			}
			if (result.next == null) break;
			result = result.next;
		}

		Entry<V> entry = new Entry<>(queue, key);
		entry.v = val;
		(result.next = entry).hash = hash;
		size++;
		return null;
	}

	@Override
	@SuppressWarnings("unchecked")
	public V remove(Object key) {
		if (key == null || entries == null) return null;

		doEvict();

		int index = indexFor(key.hashCode());
		Entry<V> curr = (Entry<V>) entries[index];
		Entry<V> prev = null;
		while (curr != null) {
			if (Objects.equals(curr.get(), key)) {
				if (prev == null) {entries[index] = null;} else {
					prev.next = curr.next;
				}
				return curr.v;
			}
			prev = curr;
			curr = curr.next;
		}

		return null;
	}

	@Override
	@SuppressWarnings("unchecked")
	public boolean containsKey(Object key) {
		if (key == null || entries == null) return false;

		doEvict();

		int index = indexFor(key.hashCode());
		Entry<V> curr = (Entry<V>) entries[index];
		while (curr != null) {
			if (Objects.equals(curr.get(), key)) {
				return true;
			}
			curr = curr.next;
		}
		return false;
	}

	@Override
	@SuppressWarnings("unchecked")
	public V get(Object key) {
		if (key == null || entries == null) return null;

		doEvict();

		int index = indexFor(key.hashCode());
		Entry<V> curr = (Entry<V>) entries[index];
		while (curr != null) {
			if (Objects.equals(curr.get(), key)) {
				return curr.v;
			}
			curr = curr.next;
		}
		return null;
	}

	@Nonnull
	public Iterator<Entry<V>> iterator() {
		// 这不香吗
		return isEmpty() ? Collections.emptyIterator() : new EntryItr<>(this.entries, this);
	}

	int indexFor(int obj) {
		return obj & (length - 1);
	}

	@SuppressWarnings("unchecked")
	public void doEvict() {
		Entry<?> entry;
		outer:
		while ((entry = (Entry<?>) queue.poll()) != null) {
			onEntryRemoved((V) entry.v);

			if (entries == null) continue;

			Entry<?> curr = entries[indexFor(entry.hash)];
			Entry<?> prev = null;
			while (curr != entry) {
				if (curr == null) continue outer;
				prev = curr;
				curr = curr.next;
			}

			if (prev == null) {
				entries[indexFor(entry.hash)] = null;
			} else {
				prev.next = Helpers.cast(curr.next);
			}
			size--;
		}
	}

	protected void onEntryRemoved(V v) {}

	@Override
	public void clear() {
		size = 0;
		if (entries != null) Arrays.fill(entries, null);

		doEvict();
	}

	public Set<Map.Entry<K, V>> entrySet() {
		return new EntrySet<>(this);
	}

	static class EntrySet<K, V> extends AbstractSet<Map.Entry<K, V>> {
		private final WeakMyHashMap<K, V> map;

		public EntrySet(WeakMyHashMap<K, V> map) {
			this.map = map;
		}

		public final int size() {
			return map.size();
		}

		public final void clear() {
			map.clear();
		}

		@Nonnull
		public final Iterator<Map.Entry<K, V>> iterator() {
			return isEmpty() ? Collections.emptyIterator() : Helpers.cast(new EntryItr<>(map.entries, map));
		}

		@SuppressWarnings("unchecked")
		public final boolean contains(Object o) {
			if (!(o instanceof WeakMyHashMap.Entry)) return false;
			WeakMyHashMap.Entry<?> e = (WeakMyHashMap.Entry<?>) o;
			return map.get((K) e.getKey()) == e.v;
		}

		public final boolean remove(Object o) {
			if (o instanceof Map.Entry) {
				MyHashMap.Entry<?, ?> e = (MyHashMap.Entry<?, ?>) o;
				return map.remove(e.k) != null;
			}
			return false;
		}
	}
}