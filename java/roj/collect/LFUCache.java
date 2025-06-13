package roj.collect;

import roj.util.Helpers;

import java.util.Map;
import java.util.function.BiConsumer;

import static roj.collect.IntMap.UNDEFINED;

/**
 * A Least Frequency Used cache implement in O(1) time complexity
 *
 * @author Roj233
 * @since 2021/9/9 23:17
 */
public class LFUCache<K, V> extends HashMap<K, V> implements Cache<K, V> {
	public static final class Entry<K, V> extends HashMap.Entry<K, V> {
		Bucket owner;
		Entry<K, V> lfuPrev, lfuNext;

		@Override
		public String toString() {return String.valueOf(key)+'='+ value +" (lfu="+(owner==null?"null":owner.freq)+")";}
	}

	private static final class Bucket {
		int freq;
		Bucket prev, next;
		Entry<?, ?> entry;
	}

	private final int maximumCapacity;
	private final Bucket head;

	private int wellDepth;
	private Bucket well;

	private BiConsumer<K, V> listener;

	public LFUCache(int maxCap) {
		this.maximumCapacity = maxCap;
		this.head = new Bucket();
		this.head.freq = 1;
	}

	@Override
	public final void clear() {
		super.clear();
		head.next = null;
		head.entry = null;
		wellDepth = 0;
		well = null;
	}

	@Override
	public final void onGet(AbstractEntry<K, V> entry) {
		Bucket b = ((Entry<K, V>) entry).owner;

		int nextF = b.freq+1;
		assert nextF >= 0;

		setNewFreq((Entry<K, V>) entry, b, nextF);
	}
	private void setNewFreq(Entry<K, V> entry, Bucket b, int freq) {
		find:{
			while (b.freq < freq) {
				assert b.freq >= 0;

				if (b.next == null) {
					Bucket f = fill();
					f.freq = freq;
					f.prev = b;

					b = b.next = f;
					break find;
				}
				b = b.next;
			}

			if (b.freq > freq) {
				Bucket f = fill();
				f.freq = freq;
				f.prev = b.prev;
				if (f.prev != null) f.prev.next = f;
				f.next = b;

				b = b.prev = f;
			}
		}

		unlink(entry);
		append(entry, b);
	}

	@Override
	protected boolean acceptTreeNode() {return false;}

	protected AbstractEntry<K, V> useEntry() {
		if (size > maximumCapacity) evict(size - maximumCapacity);

		Entry<K, V> entry = new Entry<>();

		entry.key = Helpers.cast(UNDEFINED);
		append(entry, head);
		return entry;
	}

	@Override
	protected void onDel(AbstractEntry<K, V> entry) {
		Entry<K, V> ent = (Entry<K, V>) entry;
		unlink(ent);

		ent.owner = null;
		ent.lfuPrev = ent.lfuNext = null;
		ent.next = null;
		ent.key = null;
	}

	private void unlink(Entry<K, V> entry) {
		if (entry.lfuPrev == null) entry.owner.entry = entry.lfuNext;
		else entry.lfuPrev.lfuNext = entry.lfuNext;

		if (entry.lfuNext == null) {
			Bucket b = entry.owner;
			if (b.prev != null && b.entry == null) { // do not remove head (1)
				drain(b);
			}
		} else entry.lfuNext.lfuPrev = entry.lfuPrev;
	}
	@SuppressWarnings("unchecked")
	private static <K, V> void append(Entry<K, V> entry, Bucket b) {
		entry.owner = b;

		entry.lfuPrev = null;
		Entry<K, V> prevNext = entry.lfuNext = (Entry<K, V>) b.entry;
		if (prevNext != null) prevNext.lfuPrev = entry;

		b.entry = entry;
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

	@Override
	public void setEvictListener(BiConsumer<K, V> listener) {this.listener = listener;}
	public final int evict(int amount) {
		int except = amount;

		Bucket b = head;
		while (amount > 0) {
			Bucket tmpNext = b.next;

			Entry<?, ?> entry = b.entry;
			while (entry != null && amount-- > 0) {
				Entry<?, ?> next = entry.lfuNext;

				Object k = entry.key;
				Object v = entry.value;

				remove(k);
				if (listener != null) listener.accept(Helpers.cast(k), Helpers.cast(v));
				entry = next;
			}

			if (tmpNext == null) break;
			b = tmpNext;
		}

		return except - amount;
	}

	/**
	 * 除了是LFU缓存外，这个类还可以是一个常数时间的计数器
	 * 并且经过优化，并不会有很多对象被创建
	 */
	public final void increment(K key, int count) {
		if (count < 0) throw new IllegalStateException("count < 0: "+count);

		AbstractEntry<K, V> entry = getOrCreateEntry(key);
		if (entry.key != UNDEFINED) {
			Bucket b = ((Entry<K, V>) entry).owner;

			int nextF = b.freq+count;

			setNewFreq((Entry<K, V>) entry, b, nextF);
		} else {
			entry.key = key;
			size++;
			onPut(entry, null);
			entry.setValue(null);
		}
	}
	public final void iterateForward(BiConsumer<Integer, Map.Entry<K, V>> listener) {
		var b = head;
		do {
			var entry = b.entry;
			while (entry != null) {
				listener.accept(b.freq, Helpers.cast(entry));
				entry = entry.lfuNext;
			}

			b = b.next;
		} while (b != null);
	}
	public final void iterateBackward(BiConsumer<Integer, Map.Entry<K, V>> listener) {
		var b = head.prev;
		while (b != head) {
			var entry = b.entry;
			while (entry != null) {
				listener.accept(b.freq, Helpers.cast(entry));
				entry = entry.lfuNext;
			}

			b = b.prev;
		}
	}
	public Map.Entry<K, V> getSmallest() {
		var b = head;
		do {
			var entry = b.entry;
			if (entry != null) return Helpers.cast(entry);
			b = b.next;
		} while (b != null);

		return null;
	}
	public Map.Entry<K, V> getLargest() {
		var b = head.prev;
		while (b != head) {
			var entry = b.entry;
			if (entry != null) return Helpers.cast(entry);
			b = b.prev;
		}

		return null;
	}
	public int getCount(Map.Entry<K, V> a) {return ((Entry<K, V>) a).owner.freq;}
}