package roj.collect;

import java.util.Iterator;

/**
 * @author Roj234
 * @since 2020/8/14 17:16
 */
public class EntryItr<E extends MapLikeEntry<E>> extends MapItr<E> implements Iterator<E> {
	public EntryItr(MapLikeEntry<?>[] entries, MapLike<E> map) {
		super(entries, map);
	}

	@Override
	public E next() {
		return nextT();
	}
}