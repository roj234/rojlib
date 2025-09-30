package roj.collect;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import roj.math.MathUtils;
import roj.util.Helpers;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Iterator;

import static roj.collect.IntMap.REFERENCE_LOAD_FACTOR;

/**
 * @author Roj234
 * @since 2021/5/27 0:8
 */
public class WeakHashSet<K> extends AbstractSet<K> implements FindSet<K>, _LibMap<WeakHashSet.Entry> {
	public static <T> WeakHashSet<T> newWeakHashSet() {
		return new WeakHashSet<>();
	}

	protected final ReferenceQueue<Object> queue = new ReferenceQueue<>();

	public static class Entry extends WeakReference<Object> implements _LibEntry {
		public Entry(Object key, ReferenceQueue<Object> queue) {
			super(key, queue);
			this.hash = System.identityHashCode(key);
		}

		protected final int hash;

		Entry next;
		public final Entry __next() { return next; }
	}

	protected Entry[] entries;
	protected int size = 0;

	int mask = 1;

	public WeakHashSet() { this(16); }
	public WeakHashSet(int size) { ensureCapacity(size); }

	public final void ensureCapacity(int size) {
		if (size <= mask) return;
		int length = MathUtils.nextPowerOfTwo(size);

		if (entries != null) resize(length);
		else mask = length-1;
	}

	private static final class Itr<K> extends _LibItr<Entry> implements Iterator<K> {
		K myValue;

		Itr(WeakHashSet<K> set) { super(set.entries, set); }

		@Override
		@SuppressWarnings("unchecked")
		public boolean hasNext() {
			while (true) {
				boolean b = super.hasNext();
				if (!b) return false;
				if ((myValue = (K) obj.get()) != null) return true;
				nextT();
			}
		}

		@Override
		public K next() { nextT(); return myValue; }
	}

	@Override
	public int size() { doEvict(); return size; }

	public final _LibEntry[] __entries() { return entries; }
	public final void __remove(Entry entry) { remove(entry.get()); }

	public void resize(int len) {
		Entry[] newEntries = new Entry[len--];

		Entry entry, next;
		int i = 0, j = entries.length;
		for (; i < j; i++) {
			entry = entries[i];
			entries[i] = null;

			while (entry != null) {
				next = entry.next;

				if (entry.get() != null) {
					int newKey = entry.hash&len;
					Entry old = newEntries[newKey];
					newEntries[newKey] = entry;
					entry.next = Helpers.cast(old);
				} else {
					size--;
				}

				entry = next;
			}
		}

		entries = newEntries;
		mask = len;
	}

	@Override
	public final boolean add(K key) { return findOrAdd(key, true).get() == key; }
	@Override
	@SuppressWarnings("unchecked")
	public final boolean contains(Object o) { return findOrAdd((K) o, false) != null; }
	@Override
	@SuppressWarnings("unchecked")
	public final K find(K k) {
		Entry entry = findOrAdd(k, false);
		return entry == null ? k : (K) entry.get();
	}
	@Override
	@SuppressWarnings("unchecked")
	public final K intern(K k) { return (K) findOrAdd(k, true).get(); }

	@Contract("_,true -> !null")
	protected final Entry findOrAdd(K key, boolean doAdd) {
		if (key == null) throw new NullPointerException("key");

		doEvict();

		if (entries == null) entries = new Entry[mask+1];
		else if (size > mask * REFERENCE_LOAD_FACTOR) {
			resize((mask+1) << 1);
		}

		int hash = System.identityHashCode(key);

		Entry prev = entries[mask&hash];
		if (prev != null) {
			while (true) {
				if (prev.get() == key) return prev;

				if (prev.next == null) break;
				prev = prev.next;
			}
		}

		if (!doAdd) return null;

		Entry entry = createEntry(key, queue);

		if (prev == null) entries[mask&hash] = entry;
		else prev.next = entry;

		size++;
		return entry;
	}

	@Override
	public final boolean remove(Object key) {
		if (key == null || entries == null) return false;

		doEvict();

		int index = System.identityHashCode(key)&mask;
		Entry curr = entries[index];
		Entry prev = null;
		while (curr != null) {
			if (curr.get() == key) {
				if (prev == null) entries[index] = null;
				else prev.next = curr.next;
				size--;

				entryRemoved(curr, false);
				return true;
			}
			prev = curr;
			curr = curr.next;
		}

		return false;
	}

	@NotNull
	public Iterator<K> iterator() { doEvict(); return new Itr<>(this); }

	public void doEvict() {
		Entry entry;
		outer:
		while ((entry = (Entry) queue.poll()) != null) {
			entryRemoved(entry, true);

			if (entries == null) continue;

			Entry curr = entries[entry.hash&mask];
			Entry prev = null;
			while (curr != entry) {
				if (curr == null) continue outer;
				prev = curr;
				curr = curr.next;
			}

			if (prev == null) entries[entry.hash&mask] = curr.next;
			else prev.next = curr.next;
			size--;
		}
	}

	@Override
	public void clear() {
		doEvict();

		size = 0;
		if (entries != null) Arrays.fill(entries, null);

		doEvict();
	}

	protected Entry createEntry(K key, ReferenceQueue<Object> queue) { return new Entry(key, queue); }
	protected void entryRemoved(Entry entry, boolean byGC) {}
}