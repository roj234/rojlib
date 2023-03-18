package roj.collect;

import java.util.function.BiConsumer;

/**
 * @author Roj234
 * @since 2023/3/3 0003 22:34
 */
public interface Cache<K,V> {
	void setEvictListener(BiConsumer<K,V> listener);
	int evict(int amount);
}
