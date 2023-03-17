package roj.collect;

import roj.math.MathUtils;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.PrimitiveIterator;
import java.util.PrimitiveIterator.OfInt;

import static roj.collect.IntMap.MAX_NOT_USING;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class IntSet implements MapLike<IntSet.Entry>, Iterable<Integer> {
	public static class Entry implements MapLikeEntry<Entry> {
		protected int k;

		protected Entry(int k) {
			this.k = k;
		}

		protected Entry next;

		@Override
		public Entry nextEntry() {
			return next;
		}
	}

	protected Entry[] entries;
	protected int size = 0;

	int length = 2;

	float loadFactor = 0.8f;

	public IntSet() {
		this(16);
	}

	public IntSet(int... arr) {
		ensureCapacity(arr.length);
		this.addAll(arr);
	}

	public IntSet(int size) {
		ensureCapacity(size);
	}

	public IntSet(IntSet list) {
		this.loadFactor = list.loadFactor;
		ensureCapacity(list.size());
		this.addAll(list);
	}

	public void ensureCapacity(int size) {
		if (size < length) return;
		length = MathUtils.getMin2PowerOf(size);
		resize();
	}

	@Nonnull
	public IntIterator iterator() {
		return new SetItr(this);
	}

	@Nonnull
	//@Override
	public int[] toArray() {
		int[] result = new int[size];

		int i = 0;
		for (PrimitiveIterator.OfInt itr = this.iterator(); itr.hasNext(); ) {
			int v = itr.nextInt();
			result[i++] = v;
		}

		return result;
	}

	public boolean isEmpty() {
		return size == 0;
	}

	public int size() {
		return size;
	}

	@Override
	public void removeEntry0(Entry kEntry) {
		remove(kEntry.k);
	}

	public void addAll(IntSet otherSet) {
		if (otherSet.entries == null) return;
		for (int i = 0; i < otherSet.length; i++) {
			Entry entry = otherSet.entries[i];
			if (entry == null) continue;
			while (entry != null) {
				this.add(entry.k);
				entry = entry.next;
			}
		}
	}

	public void resize() {
		if (entries == null) return;
		//System.err.println("扩容为: "+ DELIM);
		Entry[] newEntries = new Entry[length];
		Entry entry;
		Entry next;
		int i = 0, j = entries.length;
		for (; i < j; i++) {
			entry = entries[i];
			entries[i] = null;
			while (entry != null) {
				next = entry.next;
				int newKey = indexFor(entry.k);
				Entry old = newEntries[newKey];
				newEntries[newKey] = entry;
				entry.next = old;
				entry = next;
			}
		}

		this.entries = newEntries;

	}

	public boolean add(int key) {
		if (size > length * loadFactor) {
			length <<= 1;
			resize();
		}

		int k2 = key - 1;

		Entry entry = getOrCreateEntry(key, k2);
		if (entry.k == k2) {
			entry.k = key;
			afterAdd(key);
			size++;
			return true;
		}
		return false;
	}

	//@Override
	public boolean containsAll(int... collection) {
		for (int o : collection) {
			if (!contains(o)) return false;
		}
		return true;
	}

	//@Override
	public boolean addAll(int... collection) {
		boolean a = false;
		for (int k : collection) {
			a |= this.add(k);
		}
		return a;
	}

	//@Override
	public boolean removeAll(@Nonnull int... collection) {
		boolean k = false;
		for (int o : collection) {
			k |= remove(o);
		}
		return k;
	}

	public boolean intersection(IntSet collection) {
		boolean m = false;
		for (OfInt itr = this.iterator(); itr.hasNext(); ) {
			int i = itr.nextInt();
			if (!collection.contains(i)) {
				itr.remove();
				m = true;
			}
		}
		return m;
	}

	void afterAdd(int key) {
	}

	public boolean remove(int id) {
		Entry prevEntry = null;
		Entry toRemove = null;
		{
			Entry entry = getEntryFirst(id, -1, false);
			while (entry != null) {
				if (entry.k == id) {
					toRemove = entry;
					break;
				}
				prevEntry = entry;
				entry = entry.next;
			}
		}

		if (toRemove == null) return false;

		afterRemove(toRemove);

		this.size--;

		if (prevEntry != null) {
			prevEntry.next = toRemove.next;
		} else {
			this.entries[indexFor(id)] = toRemove.next;
		}

		putRemovedEntry(toRemove);

		return true;
	}

	void afterRemove(Entry key) {
	}

	public boolean contains(int o) {
		Entry entry = getEntry(o);
		return entry != null;
	}

	Entry getEntry(int id) {
		Entry entry = getEntryFirst(id, -1, false);
		while (entry != null) {
			if (id == entry.k) return entry;
			entry = entry.next;
		}
		return null;
	}

	Entry getOrCreateEntry(int id, int def) {
		Entry entry = getEntryFirst(id, def, true);
		if (entry.k == def) return entry;
		while (true) {
			if (id == entry.k) return entry;
			if (entry.next == null) break;
			entry = entry.next;
		}
		Entry firstUnused = getCachedEntry(def);
		entry.next = firstUnused;
		return firstUnused;
	}

	int indexFor(int id) {
		return (id ^ (id >>> 16)) & (length - 1);
	}

	protected Entry notUsing = null;

	protected Entry getCachedEntry(int id) {
		Entry cached = this.notUsing;
		if (cached != null) {
			cached.k = id;
			this.notUsing = cached.next;
			cached.next = null;
			return cached;
		}

		return new Entry(id);
	}

	protected void putRemovedEntry(Entry entry) {
		if (notUsing != null && notUsing.k > MAX_NOT_USING) {
			return;
		}
		entry.next = notUsing;
		entry.k = notUsing == null ? 1 : notUsing.k + 1;
		notUsing = entry;
	}

	Entry getEntryFirst(int id, int def, boolean create) {
		int i = indexFor(id);
		if (entries == null) {
			if (!create) return null;
			entries = new Entry[length];
		}
		Entry entry;
		if ((entry = entries[i]) == null) {
			if (!create) return null;
			entries[i] = entry = getCachedEntry(def);
		}
		return entry;
	}


	public String toString() {
		StringBuilder sb = new StringBuilder(getClass().getName()).append('[');
		for (PrimitiveIterator.OfInt itr = this.iterator(); itr.hasNext(); ) {
			sb.append(itr.nextInt()).append(',');
		}
		return sb.append(']').toString();
	}

	public void slowClear() {
		if (size == 0) return;
		size = 0;
		if (entries != null) {
			length = 16;
			entries = null;
		}
		if (notUsing != null) {
			notUsing = null;
		}
	}

	public void clear() {
		if (size == 0) return;
		size = 0;
		if (entries != null) {
			if (notUsing == null || notUsing.k < MAX_NOT_USING) {
				for (int i = 0; i < length; i++) {
					if (entries[i] != null) {
						putRemovedEntry(Helpers.cast(entries[i]));
						entries[i] = null;
					}
				}
			} else {Arrays.fill(entries, null);}
		}
	}

	private static class SetItr extends MapItr<Entry> implements IntIterator {
		public SetItr(IntSet map) {
			super(map.entries, map);
		}

		public int nextInt() {
			return nextT().k;
		}
	}
}