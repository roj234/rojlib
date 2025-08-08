package roj.collect;

import java.util.Iterator;

/**
 * @author Roj234
 * @since 2020/8/14 17:08
 */
interface _LibEntry {
	default _LibEntry __next() { return null; }
	default Iterator<? extends _LibEntry> __iterator() { return null; }
}