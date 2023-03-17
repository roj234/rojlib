package roj.collect;

import roj.math.MathUtils;

import javax.annotation.Nonnull;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.*;

/**
 * @author Roj234
 * @since 2021/5/16 14:34
 */
public class WeakHashSet<K> extends AbstractSet<K> implements MapLike<WeakHashSet.Entry> {
	public static <T> WeakHashSet<T> newWeakHashSet() {
		return new WeakHashSet<>();
	}

	private final ReferenceQueue<Object> queue = new ReferenceQueue<>();

	protected static class Entry extends WeakReference<Object> implements MapLikeEntry<Entry> {
		public Entry(ReferenceQueue<Object> queue, Object referent) {
			super(referent, queue);
		}

		@Override
		public boolean equals(Object obj) {
			Object ano = get();
			return ano == null ? obj == null : ano.equals(obj);
		}

		@Override
		public int hashCode() {
			return hash;
		}

		int hash;
		Entry next;

		@Override
		public Entry nextEntry() {
			return next;
		}
	}

	protected Entry[] entries;
	protected int size = 0;

	int length = 2;

	public WeakHashSet() {
		this(16);
	}

	/**
	 * @param size 初始化大小
	 */
	public WeakHashSet(int size) {
		ensureCapacity(size);
	}

	public void ensureCapacity(int size) {
		if (size <= 0) throw new NegativeArraySizeException(String.valueOf(size));
		if (size < length) return;
		length = MathUtils.getMin2PowerOf(size);

		if (entries != null) resize();
	}

	private static class SetItr<K> extends MapItr<Entry> implements Iterator<K> {
		public SetItr(WeakHashSet<K> map) {
			super(map.entries, map);
		}

		@Override
		@SuppressWarnings("unchecked")
		public K next() {
			return (K) nextT().get();
		}
	}

	@Override
	public int size() {
		doEvict();
		return size;
	}

	@Override
	public void removeEntry0(Entry entry) {
		remove(entry.get());
	}

	public void resize() {
		doEvict();

		Entry[] newEntries = new Entry[length];
		Entry entry;
		Entry next;
		int i = 0, j = entries.length;
		for (; i < j; i++) {
			entry = entries[i];
			entries[i] = null;
			while (entry != null) {
				next = entry.next;
				int newKey = indexFor(entry.hash);
				Entry old = newEntries[newKey];
				newEntries[newKey] = entry;
				entry.next = old;
				entry = next;
			}
		}

		this.entries = newEntries;
	}

	@Override
	public boolean add(K key) {
		if (key == null) return false;

		doEvict();

		int hash = key.hashCode();
		int index = indexFor(hash);
		Entry result;
		if (entries == null) entries = new Entry[length];
		result = entries[index];

		if (size > length * 0.8f) {
			length <<= 1;
			resize();
		}

		if (result == null) {
			(entries[index] = new Entry(queue, key)).hash = hash;
			size++;
			return true;
		}
		while (true) {
			if (result.equals(key)) {
				return false;
			}
			if (result.next == null) break;
			result = result.next;
		}
		(result.next = new Entry(queue, key)).hash = hash;
		size++;
		return true;
	}

	@Override
	public boolean remove(Object key) {
		if (key == null) return false;

		doEvict();

		if (entries == null) return false;
		int index = indexFor(key.hashCode());
		Entry curr = entries[index];
		Entry prev = null;
		while (curr != null) {
			if (Objects.equals(curr.get(), key)) {
				if (prev == null) {entries[index] = null;} else {
					prev.next = curr.next;
				}
				return true;
			}
			prev = curr;
			curr = curr.next;
		}

		return false;
	}

	@Override
	public boolean contains(Object key) {
		if (key == null || entries == null) return false;

		doEvict();

		int index = indexFor(key.hashCode());
		Entry curr = entries[index];
		while (curr != null) {
			if (Objects.equals(curr.get(), key)) {
				return true;
			}
			curr = curr.next;
		}
		return false;
	}

	@Nonnull
	@Override
	public Iterator<K> iterator() {
		// 这不香吗
		return isEmpty() ? Collections.emptyIterator() : new SetItr<>(this);
	}

	int indexFor(int obj) {
		return obj & (length - 1);
	}

	public void doEvict() {
		Entry entry;
		o:
		while ((entry = (Entry) queue.poll()) != null) {
			if (entries == null) continue;

			Entry curr = entries[indexFor(entry.hash)];
			Entry prev = null;
			while (curr != entry) {
				if (curr == null) continue o;

				prev = curr;
				curr = curr.next;
			}

			if (prev == null) {
				entries[indexFor(entry.hash)] = null;
			} else {
				prev.next = curr.next;
			}
			size--;
		}
	}

	@Override
	public void clear() {
		size = 0;
		if (entries != null) Arrays.fill(entries, null);

		while (queue.poll() != null) ;
	}
}