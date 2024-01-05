package roj.collect;

import java.util.Iterator;

/**
 * @author Roj234
 * @since 2020/8/14 17:08
 */
interface _Generic_Entry {
	_Generic_Entry __next();
	default Iterator<? extends _Generic_Entry> __iterator() { return null; }
}