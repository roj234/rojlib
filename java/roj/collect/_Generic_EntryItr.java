package roj.collect;

import java.util.Iterator;

/**
 * @author Roj234
 * @since 2020/8/14 17:16
 */
final class _Generic_EntryItr<E extends _Generic_Entry> extends MapItr<E> implements Iterator<E> {
	_Generic_EntryItr(_Generic_Entry[] entries) { super(entries); }
	_Generic_EntryItr(_Generic_Map<E> map) { super(map); }
	public E next() { return nextT(); }
}