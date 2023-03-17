package roj.collect;

import java.util.Iterator;
import java.util.function.BiConsumer;

/**
 * @author Roj234
 * @since 2021/4/25 23:1
 */
public class LinkedMyHashMap<K, V> extends MyHashMap<K, V> {
	private boolean reversed, accessOrder;

	public static class LinkedEntry<K, V> extends MyHashMap.Entry<K, V> {
		public LinkedEntry<K, V> p, n;
		protected LinkedEntry(K k, V v) {
			super(k, v);
		}
	}

	public LinkedEntry<K, V> firstEntry() {
		return tail == head ? null : head;
	}
	public LinkedEntry<K, V> lastEntry() {
		return tail == head ? null : tail;
	}

	public V firstValue() {
		return tail == head ? null : head.v;
	}
	public V lastValue() {
		return tail == head ? null : tail.v;
	}

	public V valueAt(int id) {
		if (id >= size) return null;

		LinkedEntry<K, V> entry = head;
		while (id-- > 0) {
			entry = entry.n;
		}
		return entry == null ? null : entry.v;
	}
	public V valueFromLast(int id) {
		if (id >= size) return null;

		LinkedEntry<K, V> entry = tail;
		while (id-- > 0) {
			entry = entry.p;
		}
		return entry == null ? null : entry.v;
	}

	public LinkedMyHashMap() {
		this(16);
	}
	public LinkedMyHashMap(int size) {
		super(size);
	}

	LinkedEntry<K, V> head, tail;

	protected Entry<K, V> createEntry(K id) {
		return new LinkedEntry<>(id, null);
	}

	public void setAccessOrder(boolean accessOrder) {
		this.accessOrder = accessOrder;
	}

	public LinkedMyHashMap<K, V> setReverseIterate(boolean isReverse) {
		this.reversed = isReverse;
		return this;
	}

	@Override
	public Iterator<Entry<K, V>> entryIterator() {
		return new EntryItr();
	}

	@Override
	public void forEach(BiConsumer<? super K, ? super V> action) {
		boolean rev = reversed;
		LinkedEntry<K, V> e = rev ? tail : head;

		while (e != null) {
			action.accept(e.k, e.v);
			e = rev ? e.p : e.n;
		}
	}

	public class EntryItr extends AbstractIterator<Entry<K, V>> {
		private final boolean rev = reversed;
		private LinkedEntry<K, V> entry = rev ? tail : head;

		@Override
		protected boolean computeNext() {
			while (true) {
				if (entry == null) return false;

				LinkedEntry<K, V> prev = entry;
				entry = rev ? entry.p : entry.n;
				if (prev.v == IntMap.UNDEFINED) continue;

				result = prev;
				return true;
			}
		}

		@Override
		protected void remove(Entry<K, V> obj) {
			LinkedMyHashMap.this.remove(obj.k);
		}
	}

	@Override
	void afterPut(Entry<K, V> entry) {
		LinkedEntry<K, V> myEntry = (LinkedEntry<K, V>) entry;
		if (head == null) head = myEntry;

		myEntry.p = tail;
		if (tail != null) tail.n = myEntry;
		tail = myEntry;
	}

	@Override
	void afterRemove(Entry<K, V> entry) {
		LinkedEntry<K, V> myEntry = (LinkedEntry<K, V>) entry;

		if (myEntry.n == null) tail = myEntry.p;
		else myEntry.n.p = myEntry.p;

		if (myEntry.p == null) head = myEntry.n;
		else myEntry.p.n = myEntry.n;

		myEntry.p = myEntry.n = null;
	}

	@Override
	void afterAccess(Entry<K, V> entry, V now) {
		if (accessOrder) {
			afterRemove(entry);
			afterPut(entry);
		}
	}

	public void clear() {
		super.clear();
		tail = head = null;
	}
}