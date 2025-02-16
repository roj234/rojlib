package roj.collect;

import java.util.function.BiConsumer;

/**
 * @author Roj233
 * @since 2023/3/3 22:32
 */
public class LRUCache<K, V> extends LinkedMyHashMap<K, V> implements Cache<K,V> {
	private final int maximumCapacity;
	private BiConsumer<K,V> listener;

	public LRUCache(int maxCap) {
		this.maximumCapacity = maxCap;
		setAccessOrder(true);
	}

	@Override
	public AbstractEntry<K, V> getOrCreateEntry(K key) {
		if (maximumCapacity > 0 && size > maximumCapacity) evict(size - maximumCapacity);
		return super.getOrCreateEntry(key);
	}

	@Override
	public void setEvictListener(BiConsumer<K,V> listener) {
		this.listener = listener;
	}

	public int evict(int amount) {
		int exceptCount = amount;

		LinkedEntry<K, V> entry = head;
		while (entry != null && amount > 0) {
			LinkedEntry<K, V> next = entry.n;

			K k = entry.k;
			V v = entry.v;
			remove(k);
			if (listener != null) listener.accept(k,v);
			amount--;

			entry = next;
		}

		return exceptCount-amount;
	}
}