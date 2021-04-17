package roj.collect;

import roj.math.MathUtils;

import javax.annotation.Nonnull;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;

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
		public Entry(Object referent, ReferenceQueue<Object> queue) { super(referent, queue); }
		int hash;
		Entry next;

		@Override
		public Entry nextEntry() { return next; }
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
		Entry[] newEntries = new Entry[length];
		Entry entry;
		Entry next;
		int i = 0, j = entries.length;
		for (; i < j; i++) {
			entry = entries[i];
			entries[i] = null;
			while (entry != null) {
				next = entry.next;
				if (entry.get() != null) {
					int newKey = entry.hash & (length - 1);
					Entry old = newEntries[newKey];
					newEntries[newKey] = entry;
					entry.next = old;
				}
				entry = next;
			}
		}

		this.entries = newEntries;
	}

	@Override
	public boolean add(K key) {
		if (key == null) return false;

		doEvict();

		int hash = hashCode(key);
		if (entries == null) entries = new Entry[length];

		int index = hash & (length - 1);
		Entry entry = entries[index];

		if (entry == null) {
			(entries[index] = new Entry(key, queue)).hash = hash;
			size++;
			return true;
		}

		while (true) {
			if (equals(entry, key)) return false;

			if (entry.next == null) break;
			entry = entry.next;
		}

		if (size > length * 0.8f) {
			length <<= 1;
			resize();
		}

		(entry.next = new Entry(key, queue)).hash = hash;
		size++;
		return true;
	}

	protected int hashCode(Object key) { return key.hashCode(); }
	protected boolean equals(Entry a, Object b) { Object o = a.get(); return o != null && o.equals(b); }

	@Override
	public boolean remove(Object key) {
		if (key == null) return false;

		doEvict();

		if (entries == null) return false;
		int index = hashCode(key) & (length-1);
		Entry curr = entries[index];
		Entry prev = null;
		while (curr != null) {
			if (equals(curr, key)) {
				if (prev == null) entries[index] = null;
				else prev.next = curr.next;

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

		Entry entry = entries[hashCode(key) & (length-1)];
		while (entry != null) {
			if (equals(entry, key)) return true;
			entry = entry.next;
		}
		return false;
	}

	@Nonnull
	@Override
	public Iterator<K> iterator() {
		return isEmpty() ? Collections.emptyIterator() : new SetItr<>(this);
	}

	public void doEvict() {
		Entry remove;

		o:
		while ((remove = (Entry) queue.poll()) != null) {
			if (entries == null) continue;

			// get prev
			Entry curr = entries[remove.hash & (length-1)];
			Entry prev = null;
			while (curr != remove) {
				if (curr == null) continue o;

				prev = curr;
				curr = curr.next;
			}

			if (prev == null) entries[remove.hash & (length-1)] = null;
			else prev.next = remove.next;
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