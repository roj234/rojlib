package roj.collect;

import java.util.Iterator;

/**
 * @author Roj234
 * @since 2020/8/14 17:16
 */
public final class EntryItr<E extends _Generic_Entry<E>> extends MapItr<E> implements Iterator<E> {
	public EntryItr(_Generic_Entry<?>[] entries) { super(entries); }
	public EntryItr(_Generic_Map<E> map) { super(map); }
	public E next() { return nextT(); }
}