package roj.collect;

import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author Roj234
 * @since 2021/4/21 22:51java
 */
public interface FindMap<K, V> extends Map<K, V> {
	@Nullable Map.Entry<K, V> find(K k);
	void ensureCapacity(int len);
}
