package roj.collect;

import java.util.Collection;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.function.Predicate;

/**
 * @author Roj234
 * @since 2021/5/4 14:32
 */
public class TimedHashMap<K, V> extends MyHashMap<K, V> {
	protected long timeout, lastCheck;

	protected boolean update;

	public TimedHashMap(int timeOut) {
		this.timeout = (update = timeOut < 0) ? -timeOut : timeOut;
	}

	LinkedList<TimedEntry<K, V>> list = new LinkedList<>();

	public void retainOutDated(Collection<K> kholder, Collection<V> vholder, Predicate<Entry<K, V>> kPredicate, int max) {
		long curr = System.currentTimeMillis();

		if (!this.list.isEmpty()) {
			for (ListIterator<TimedEntry<K, V>> iterator = this.list.listIterator(this.list.size() - 1); iterator.hasPrevious(); ) {
				TimedEntry<K, V> entry = iterator.previous();
				if (curr - entry.timestamp >= timeout && max-- > 0) {
					if (kPredicate.test(entry)) {
						kholder.add(entry.k);
						vholder.add(entry.v);
					}
					remove(entry.k);
					iterator.remove();
				} else {
					break;
				}
			}
		}

		lastCheck = curr;
	}

	public static class TimedEntry<K, V> extends MyHashMap.Entry<K, V> {
		protected long timestamp = System.currentTimeMillis();

		protected TimedEntry(K k, V v) {
			super(k, v);
		}
	}

	@Override
	void afterRemove(Entry<K, V> entry) {
		list.remove(entry);
		clearOutdatedEntry();
	}

	@Override
	void afterPut(Entry<K, V> entry) {
		clearOutdatedEntry();
		list.add(0, (TimedEntry<K, V>) entry);
	}

	@Override
	protected Entry<K, V> createEntry(K id) {
		return new TimedEntry<>(id, null);
	}

	@Override
	public Entry<K, V> getEntry(K id) {
		Entry<K, V> entry = super.getEntry(id);
		if (entry == null) return null;
		TimedEntry<?, ?> entry1 = (TimedEntry<?, ?>) entry;
		long t = System.currentTimeMillis() - entry1.timestamp;
		if (t >= timeout) {
			if (entry.v != IntMap.UNDEFINED) {
				remove(entry.k);
			}
			return null;
		}
		if (update) entry1.timestamp += t;
		return entry;
	}

	boolean clear;

	public void clearOutdatedEntry() {
		if (clear) return;
		clear = true;
		try {
			long curr = System.currentTimeMillis();

			if (!this.list.isEmpty()) {
				for (ListIterator<TimedEntry<K, V>> iterator = this.list.listIterator(this.list.size() - 1); iterator.hasPrevious(); ) {
					TimedEntry<K, V> entry = iterator.previous();
					if (curr - entry.timestamp >= timeout) {
						remove(entry.k);
						iterator.remove();
					} else if (!update) {
						break;
					}
				}
			}

			lastCheck = curr;
		} finally {
			clear = false;
		}
	}
}
