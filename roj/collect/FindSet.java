package roj.collect;

import java.util.Set;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public interface FindSet<K> extends Set<K> {
	K find(K k);

	K intern(K k);
}
