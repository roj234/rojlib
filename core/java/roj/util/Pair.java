package roj.util;

import roj.ci.annotation.AliasOf;

import java.util.AbstractMap;
import java.util.Map;

/**
 * @author Roj234
 * @since 2025/09/16 23:19
 */
@AliasOf(AbstractMap.SimpleImmutableEntry.class)
public final class Pair<K, V> extends AbstractMap.SimpleImmutableEntry<K, V> {
	public Pair(K key, V value) {super(key, value);}
	public Pair(Map.Entry<? extends K, ? extends V> entry) {super(entry);}
}
