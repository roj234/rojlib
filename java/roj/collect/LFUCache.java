package roj.collect;

import roj.concurrent.FastThreadLocal;
import roj.util.Helpers;

import java.util.function.BiConsumer;

import static roj.collect.IntMap.UNDEFINED;

/**
 * A Least Frequency Used cache implement in O(1) time complexity
 *
 * @author Roj233
 * @since 2021/9/9 23:17
 */
public class LFUCache<K, V> extends MyHashMap<K, V> implements Cache<K,V> {
	public static final class Entry<K, V> extends MyHashMap.Entry<K, V> {
		Bucket owner;
		Entry<K, V> lfuPrev, lfuNext;

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

	private BiConsumer<K,V> listener;

	public LFUCache(int maxCap) {
		this(maxCap, 16);
	}

	public LFUCache(int maxCap, int evictOnce) {
		this.maximumCapacity = maxCap;
		this.removeAtOnce = evictOnce;
		this.head = new Bucket();
		this.head.freq = 1;
	}

	private static final FastThreadLocal<ObjectPool<AbstractEntry<?,?>>> MY_OBJECT_POOL = FastThreadLocal.withInitial(() -> new ObjectPool<>(null, 99));
	protected AbstractEntry<K, V> useEntry() {
		if (size == maximumCapacity) evict(removeAtOnce);

		Entry<K, V> entry = Helpers.cast(MY_OBJECT_POOL.get().get());

		if (entry == null) entry = new Entry<>();
		entry.k = Helpers.cast(UNDEFINED);
		append(entry, head);
		return entry;
	}
	@SuppressWarnings("unchecked")
	protected void reserveEntry(AbstractEntry<?, ?> entry) {
		Entry<K, V> ent = (Entry<K, V>) entry;
		unlink(ent);
		ent.owner = null;
		ent.lfuPrev = ent.lfuNext = null;
		entry.k = null;
		entry.next = null;
		entry.setValue(null);
		MY_OBJECT_POOL.get().reserve(entry);
	}

	@Override
	protected boolean supportAutoCollisionFix() { return false; }

	@Override
	public final void clear() {
		super.clear();
		head.next = null;
		head.entry = null;
		wellDepth = 0;
		well = null;
	}

	@Override
	public void setEvictListener(BiConsumer<K,V> listener) {
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

				Object k = entry.k;
				Object v = entry.v;

				remove(k);
				if (listener != null) listener.accept(Helpers.cast(k), Helpers.cast(v));
				entry = next;
			}

			if (tmpNext == null) break;
			b = tmpNext;
		}

		return except-amount;
	}

	@Override
	protected void onGet(AbstractEntry<K, V> entry) { access((Entry<K, V>) entry); }
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
		if (entry.lfuPrev == null) entry.owner.entry = entry.lfuNext;
		else entry.lfuPrev.lfuNext = entry.lfuNext;

		if (entry.lfuNext == null) {
			Bucket b = entry.owner;
			if (b.prev != null && b.entry == null) { // do not remove head (1)
				drain(b);
			}
		}
		else entry.lfuNext.lfuPrev = entry.lfuPrev;
	}

	private void drain(Bucket o) {
		o.prev.next = o.next;
		if (o.next != null) o.next.prev = o.prev;

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