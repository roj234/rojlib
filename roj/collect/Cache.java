package roj.collect;

import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2023/3/3 0003 22:34
 */
public interface Cache<V> {
	void setEvictListener(Consumer<V> listener);
	int evict(int amount);
}
