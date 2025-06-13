package roj.collect;

import java.util.Iterator;

/**
 * @author Roj234
 * @since 2020/8/14 17:16
 */
final class _LibEntryItr<E extends _LibEntry> extends _LitItr<E> implements Iterator<E> {
	_LibEntryItr(_LibEntry[] entries, _LibMap<E> map) { super(entries, map); }
	public E next() { return nextT(); }
}