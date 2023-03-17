package roj.collect;

import java.util.function.Consumer;

/**
 * @author Roj233
 * @since 2023/3/3 22:32
 */
public class LRUCache<K, V> extends LinkedMyHashMap<K, V> implements Cache<V> {
	private final int maximumCapacity, removeAtOnce;
	private Consumer<V> listener;

	public LRUCache(int maxCap) {
		this(maxCap, 1);
	}

	public LRUCache(int maxCap, int evictOnce) {
		this.maximumCapacity = maxCap;
		this.removeAtOnce = evictOnce;
		setAccessOrder(true);
	}

	@Override
	public Entry<K, V> getOrCreateEntry(K id) {
		if (size > maximumCapacity) evict(size-maximumCapacity);
		return super.getOrCreateEntry(id);
	}

	@Override
	public void setEvictListener(Consumer<V> listener) {
		this.listener = listener;
	}

	public int evict(int amount) {
		int exceptCount = amount;

		LinkedEntry<K, V> entry = head;
		while (entry != null && amount > 0) {
			LinkedEntry<K, V> tmp = entry.n;

			V v = entry.v;
			remove(entry.k);
			if (listener != null) listener.accept(v);
			amount--;

			entry = tmp;
		}

		return exceptCount-amount;
	}
}
