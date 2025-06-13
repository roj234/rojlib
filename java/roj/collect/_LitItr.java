package roj.collect;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;

import static roj.collect.AbstractIterator.*;

/**
 * @author Roj234
 * @since 2020/8/14 17:06
 */
class _LitItr<T extends _LibEntry> {
	public T obj;
	int stage = INITIAL;

	private final _LibEntry[] entries;
	private int i;

	private final _LibMap<T> remover;
	private Iterator<T> itr;

	_LitItr(_LibEntry[] entries, _LibMap<T> remover) {
		this.entries = entries;
		this.remover = remover;

		if (entries == null) stage = ENDED;
	}

	public boolean hasNext() {
		check();
		return stage != ENDED;
	}

	public final T nextT() {
		check();
		if (stage == ENDED) throw new NoSuchElementException();
		stage = GOTTEN;
		return obj;
	}

	private void check() {
		if (stage <= GOTTEN) {
			stage = computeNext() ? CHECKED : ENDED;
		}
	}

	public final void remove() {
		checkConcMod();
		if (stage != GOTTEN) throw new IllegalStateException();
		if (remover == null) throw new UnsupportedOperationException();
		stage = INITIAL;

		T t = obj;
		check();
		remover.__remove(t);
	}

	@SuppressWarnings("unchecked")
	private boolean computeNext() {
		checkConcMod();

		if (itr != null && itr.hasNext()) {
			obj = itr.next();
			return true;
		}

		if (obj != null) obj = (T) obj.__next();

		while (obj == null) {
			if (i >= entries.length) return false;

			obj = (T) entries[i++];
			if (obj != null) {
				Iterator<T> itr = (Iterator<T>) obj.__iterator();
				if (itr != null && itr.hasNext()) {
					obj = itr.next();
					this.itr = itr;
					return true;
				}
			}
		}
		return true;
	}

	private void checkConcMod() {
		if (remover != null && remover.__entries() != entries) throw new ConcurrentModificationException();
	}
}