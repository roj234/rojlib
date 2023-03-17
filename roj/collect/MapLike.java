package roj.collect;

import java.util.Iterator;

/**
 * @author Roj234
 * @since 2020/8/14 17:07
 */
interface MapLike<T extends MapLikeEntry<T>> {
	default void removeEntry0(T t) {
		throw new UnsupportedOperationException();
	}

	default Iterator<T> entryIterator() {
		return null;
	}
}
