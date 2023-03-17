package roj.collect;

import roj.util.Helpers;

import java.util.Iterator;
import java.util.NoSuchElementException;

import static roj.collect.AbstractIterator.*;

/**
 * @author Roj234
 * @since 2020/8/14 17:06
 */
public class MapItr<T extends MapLikeEntry<T>> {
	public T obj;
	int stage = INITIAL;

	public final boolean hasNext() {
		check();
		return stage != ENDED;
	}

	public final T nextT() {
		check();
		if (stage == ENDED) {
			throw new NoSuchElementException();
		}
		stage = GOTTEN;
		return obj;
	}

	private void check() {
		if (stage <= GOTTEN) {
			if (!computeNext()) {
				stage = ENDED;
			} else {
				stage = CHECKED;
			}
		}
	}

	public final void remove() {
		if (stage != GOTTEN) throw new IllegalStateException();
		stage = INITIAL;

		if (itr != null) {
			itr.remove();
		} else {
			T t = obj;
			check();
			remover.removeEntry0(t);
		}
	}

	private final T[] entries;
	private MapLike<T> remover;
	private Iterator<T> itr;
	private int i;

	public void reset() {
		if (itr != null) throw new UnsupportedOperationException();
		if (entries == null) stage = ENDED;
		else {
			obj = null;
			i = 0;
			stage = INITIAL;
		}
	}

	public MapItr(MapLikeEntry<?>[] entries, MapLike<T> remover) {
		this.entries = Helpers.cast(entries);
		if (remover != null) {
			this.itr = remover.entryIterator();
			this.remover = remover;
		}

		if (entries == null) stage = ENDED;
	}

	private boolean computeNext() {
		if (itr != null) {
			boolean flag = itr.hasNext();
			if (flag) obj = itr.next();
			return flag;
		}

		while (true) {
			if (obj == null) {
				while (true) {
					if (i >= entries.length) return false;
					obj = entries[i++];
					if (obj != null) return true;
				}
			}

			obj = obj.nextEntry();
			if (obj != null) return true;
		}
	}
}
