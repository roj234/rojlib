package roj.collect;

import java.util.Iterator;

/**
 * @author Roj234
 * @since 2020/8/14 17:07
 */
interface _Generic_Map<T extends _Generic_Entry> {
	_Generic_Entry[] __entries();
	void __remove(T t);
	default Iterator<?> __iterator() {return null;}
}