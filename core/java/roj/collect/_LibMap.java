package roj.collect;

import java.util.Iterator;

/**
 * @author Roj234
 * @since 2020/8/14 17:07
 */
interface _LibMap<T extends _LibEntry> {
	_LibEntry[] __entries();
	void __remove(T t);
	default Iterator<?> __iterator() {return null;}
}