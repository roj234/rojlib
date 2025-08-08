package roj.collect;

import java.util.Map;

/**
 * @author Roj234
 * @since 2020/9/19 17:44
 */
public interface BiMap<K, V> extends Map<K, V> {
	BiMap<V, K> inverse();
	V forcePut(K key, V value);
}
