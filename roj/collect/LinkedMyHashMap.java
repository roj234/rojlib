package roj.collect;

import roj.concurrent.FastThreadLocal;
import roj.util.Helpers;

import java.util.Iterator;
import java.util.function.BiConsumer;

import static roj.collect.IntMap.UNDEFINED;

/**
 * @author Roj234
 * @since 2021/4/25 23:1
 */
public class LinkedMyHashMap<K, V> extends MyHashMap<K, V> {
	private boolean reversed, accessOrder;

	public static class LinkedEntry<K, V> extends MyHashMap.Entry<K, V> {
		public LinkedEntry<K, V> p, n;
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

	public void setAccessOrder(boolean accessOrder) {
		this.accessOrder = accessOrder;
	}

	public LinkedMyHashMap<K, V> setReverseIterate(boolean isReverse) {
		this.reversed = isReverse;
		return this;
	}

	@Override
	public Iterator<AbstractEntry<K, V>> __iterator() {
		return Helpers.cast(new EntryItr());
	}
	public Iterator<Entry<K, V>> __iterator_javac_is_sb() {
		return Helpers.cast(new EntryItr());
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
	protected void onPut(AbstractEntry<K, V> entry, V newV) {
		LinkedEntry<K, V> myEntry = (LinkedEntry<K, V>) entry;
		if (head == null) head = myEntry;

		myEntry.p = tail;
		if (tail != null) tail.n = myEntry;
		tail = myEntry;
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

	@Override
	protected void onGet(AbstractEntry<K, V> entry) {
		if (accessOrder) {
			onDel(entry);
			onPut(entry, null);
		}
	}

	public void clear() {
		super.clear();
		tail = head = null;
	}

	private static final FastThreadLocal<ObjectPool<AbstractEntry<?,?>>> MY_OBJECT_POOL = FastThreadLocal.withInitial(() -> new ObjectPool<>(null, 128));
	protected AbstractEntry<K, V> useEntry() {
		AbstractEntry<K, V> entry = Helpers.cast(MY_OBJECT_POOL.get().get());

		if (entry == null) entry = new LinkedEntry<>();
		entry.k = Helpers.cast(UNDEFINED);
		return entry;
	}
	protected void reserveEntry(AbstractEntry<?, ?> entry) {
		LinkedEntry<?, ?> entry1 = (LinkedEntry<?, ?>) entry;
		entry1.k = null;
		entry1.v = null;
		entry1.p = null;
		entry1.n = null;
		entry1.next = null;
		MY_OBJECT_POOL.get().reserve(entry1);
	}
}