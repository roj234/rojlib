package roj.collect;

import roj.util.Helpers;

import java.util.Iterator;
import java.util.function.BiConsumer;

import static roj.collect.IntMap.UNDEFINED;

/**
 * @author Roj234
 * @since 2021/4/25 23:1
 */
public class LinkedMyHashMap<K, V> extends MyHashMap<K, V> {
	public static class LinkedEntry<K, V> extends MyHashMap.Entry<K, V> {
		public LinkedEntry<K, V> p, n;
	}

	LinkedEntry<K, V> head, tail;
	private boolean reversed, accessOrder;

	public LinkedMyHashMap() {this(16);}
	public LinkedMyHashMap(int size) {super(size);}

	public void setAccessOrder(boolean accessOrder) {this.accessOrder = accessOrder;}
	public void setReverseIterate(boolean isReverse) {this.reversed = isReverse;}

	public LinkedEntry<K, V> firstEntry() {return tail == head ? null : head;}
	public LinkedEntry<K, V> lastEntry() {return tail == head ? null : tail;}

	public V firstValue() {return tail == head ? null : head.v;}
	public V lastValue() {return tail == head ? null : tail.v;}

	public V valueAt(int id) {
		if (id >= size) return null;

		LinkedEntry<K, V> entry = head;
		while (id-- > 0) {
			entry = entry.n;
		}
		return entry == null ? null : entry.v;
	}
	public V lastValueAt(int id) {
		if (id >= size) return null;

		LinkedEntry<K, V> entry = tail;
		while (id-- > 0) {
			entry = entry.p;
		}
		return entry == null ? null : entry.v;
	}

	@Override
	public Iterator<AbstractEntry<K, V>> __iterator() {return Helpers.cast(new EntryItr());}
	public Iterator<Entry<K, V>> __iterator_javac_is_sb() {return Helpers.cast(new EntryItr());}
	public final class EntryItr extends AbstractIterator<Entry<K, V>> {
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
		protected void remove(Entry<K, V> obj) {LinkedMyHashMap.this.remove(obj.k);}
	}

	public void clear() {
		super.clear();
		tail = head = null;
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

	@Override
	protected void onPut(AbstractEntry<K, V> entry, V newV) {
		LinkedEntry<K, V> myEntry = (LinkedEntry<K, V>) entry;
		if (myEntry.v != UNDEFINED) return;
		insert(myEntry);
	}

	private void insert(LinkedEntry<K, V> myEntry) {
		if (head == null) head = myEntry;

		myEntry.p = tail;
		if (tail != null) tail.n = myEntry;
		tail = myEntry;
	}

	@Override
	protected void onGet(AbstractEntry<K, V> entry) {
		if (accessOrder && entry != head) {
			onDel(entry);
			insert((LinkedEntry<K, V>) entry);
		}
	}

	@Override
	protected void onDel(AbstractEntry<K, V> entry) {
		LinkedEntry<K, V> myEntry = (LinkedEntry<K, V>) entry;

		if (myEntry.n == null) tail = myEntry.p;
		else myEntry.n.p = myEntry.p;

		if (myEntry.p == null) head = myEntry.n;
		else myEntry.p.n = myEntry.n;

		myEntry.p = myEntry.n = null;
	}

	protected AbstractEntry<K, V> useEntry() {
		LinkedEntry<K, V> entry = new LinkedEntry<>();
		entry.k = Helpers.cast(UNDEFINED);
		entry.v = Helpers.cast(UNDEFINED);
		return entry;
	}
}