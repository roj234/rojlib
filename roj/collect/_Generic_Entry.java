package roj.collect;

import java.util.Iterator;

/**
 * @author Roj234
 * @since 2020/8/14 17:08
 */
interface _Generic_Entry<T> {
	T __next();
	default Iterator<T> __iterator() { return null; }
}
