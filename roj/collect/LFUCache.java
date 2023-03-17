package roj.collect;

import roj.util.Helpers;

import java.util.function.Consumer;

import static roj.collect.IntMap.UNDEFINED;

/**
 * A Least Frequency Used cache implement in O(1) time complexity
 *
 * @author Roj233
 * @since 2021/9/9 23:17
 */
public class LFUCache<K, V> extends MyHashMap<K, V> implements Cache<V> {
	public static final class Entry<K, V> extends MyHashMap.Entry<K, V> {
		Bucket owner;
		Entry<K, V> lfuPrev, lfuNext;

		public Entry(K k, V v) {
			super(k, v);
		}

		public String cToString() {
			return lfuNext == null ? String.valueOf(k) : k + ", " + lfuNext.cToString();
		}
	}

	private static final class Bucket {
		int freq;
		Bucket prev, next;
		Entry<?, ?> entry;

		@Override
		public String toString() {
			return freq + "=>[" + entry.cToString() + "], " + next;
		}
	}

	private final int maximumCapacity, removeAtOnce;
	private final Bucket head;

	private int wellDepth;
	private Bucket well;

	private Consumer<V> listener;

	public LFUCache(int maxCap) {
		this(maxCap, 16);
	}

	public LFUCache(int maxCap, int evictOnce) {
		this.maximumCapacity = maxCap;
		this.removeAtOnce = evictOnce;
		this.head = new Bucket();
		this.head.freq = 1;
	}

	@Override
	protected final MyHashMap.Entry<K, V> createEntry(K id) {
		return new Entry<>(id, null);
	}

	@Override
	public final MyHashMap.Entry<K, V> getEntry(K id) {
		MyHashMap.Entry<K, V> entry = super.getEntry(id);
		if (entry != null && entry.v != UNDEFINED) access((Entry<K, V>) entry);
		return entry;
	}

	@Override
	protected final MyHashMap.Entry<K, V> getCachedEntry(K id) {
		if (size == maximumCapacity) evict(removeAtOnce);

		Entry<K, V> entry = (Entry<K, V>) super.getCachedEntry(id);
		append(entry, head);
		return entry;
	}

	@Override
	protected final void putRemovedEntry(MyHashMap.Entry<K, V> entry) {
		super.putRemovedEntry(entry);

		Entry<K, V> ent = (Entry<K, V>) entry;
		unlink(ent);
		ent.owner = null;
		ent.lfuPrev = ent.lfuNext = null;
	}

	@Override
	public final void clear() {
		head.next = null;
		head.entry = null;
		wellDepth = 0;
		well = null;
	}

	@Override
	public void setEvictListener(Consumer<V> listener) {
		this.listener = listener;
	}

	public final int evict(int amount) {
		int except = amount;

		Bucket b = head;
		while (amount > 0) {
			Bucket tmpNext = b.next;

			Entry<?, ?> entry = b.entry;
			while (entry != null && amount-- > 0) {
				Entry<?, ?> next = entry.lfuNext;
				Object v = entry.v;
				remove(entry.k);
				if (listener != null) listener.accept(Helpers.cast(v));
				entry = next;
			}

			if (tmpNext == null) break;
			b = tmpNext;
		}

		return except-amount;
	}

	public final void access(Entry<K, V> entry) {
		Bucket b = entry.owner;

		int nextF = b.freq+1;
		if (nextF < 0) throw new IllegalArgumentException("ERROR!");

		find: {
			while (b.freq < nextF) {
				if (b.freq == -1) {
					throw new IllegalArgumentException();
				}
				if (b.next == null) {
					Bucket f = fill();
					f.freq = nextF;
					f.prev = b;

					b = b.next = f;
					break find;
				}
				b = b.next;
			}

			if (b.freq > nextF) {
				Bucket f = fill();
				f.freq = nextF;
				f.prev = b.prev;
				if (f.prev != null) f.prev.next = f;
				f.next = b;

				b = b.prev = f;
			}
		}

		unlink(entry);
		append(entry, b);
	}

	@SuppressWarnings("unchecked")
	private static <K,V> void append(Entry<K,V> entry, Bucket b) {
		entry.owner = b;

		entry.lfuPrev = null;
		Entry<K, V> prevNext = entry.lfuNext = (Entry<K, V>) b.entry;
		if (prevNext != null) prevNext.lfuPrev = entry;

		b.entry = entry;
	}
	private void unlink(Entry<K, V> entry) {
		if (entry.lfuPrev != null) {
			Entry<K, V> next = entry.lfuPrev.lfuNext = entry.lfuNext;
			if (next != null) next.lfuPrev = entry.lfuPrev;
		} else {
			Bucket b = entry.owner;
			Entry<K, V> next = entry.lfuNext;

			if (next == null) {
				if (b.prev != null) { // do not remove head (1)
					drain(b);
				}
			} else {
				b.entry = next;
				next.lfuPrev = null;
			}
		}
	}

	private void drain(Bucket o) {
		o.prev.next = o.next;
		o.next.prev = o.prev;

		if (wellDepth < 16) {
			o.next = well;
			o.prev = null;
			o.freq = -1;
			well = o;
			wellDepth++;
		}
	}
	private Bucket fill() {
		if (wellDepth > 0) {
			Bucket rt = well;
			well = rt.next;
			wellDepth--;
			rt.prev = rt.next = null;
			return rt;
		}
		return new Bucket();
	}
}
