package roj.collect;

import java.util.Map;

/**
 * @author Roj234
 * @since 2020/9/19 17:44
 */
public interface Flippable<K, V> extends Map<K, V> {
	Flippable<V, K> flip();

	V forcePut(K key, V e);
}
