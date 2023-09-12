package roj.collect;

import roj.util.Helpers;

import java.util.NoSuchElementException;

import static roj.collect.AbstractIterator.*;

/**
 * @author Roj234
 * @since 2020/8/14 17:06
 */
public class MapItr<T extends _Generic_Entry<T>> {
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
		if (remover == null) throw new UnsupportedOperationException();
		stage = INITIAL;

		T t = obj;
		check();
		remover.__remove(t);
	}

	private final T[] entries;
	private final _Generic_Map<T> remover;
	private int i;

	public void reset() {
		if (entries == null) stage = ENDED;
		else {
			obj = null;
			i = 0;
			stage = INITIAL;
		}
	}

	public MapItr(_Generic_Map<T> remover) {
		this.entries = Helpers.cast(remover.__entries());
		this.remover = remover;

		if (entries == null) stage = ENDED;
	}
	public MapItr(_Generic_Entry<?>[] entries) {
		this.entries = Helpers.cast(entries);
		this.remover = null;

		if (entries == null) stage = ENDED;
	}

	private boolean computeNext() {
		while (true) {
			if (obj == null) {
				while (true) {
					if (i >= entries.length) return false;
					obj = entries[i++];
					if (obj != null) return true;
				}
			}

			obj = obj.__next();
			if (obj != null) return true;
		}
	}
}
