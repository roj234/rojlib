package roj.collect;

import roj.util.Helpers;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * @author Roj234
 * @since 2021/5/23 17:39
 */
public class ConcatedCollection<E> extends AbstractCollection<E> {
	Collection<E>[] collections;

	@SafeVarargs
	public ConcatedCollection(Collection<E>... collections) {
		this.collections = collections;
	}

	@Override
	public int size() {
		int size = 0;
		for (Collection<E> e : collections) {
			size += e.size();
		}
		return size;
	}

	@Override
	public boolean isEmpty() {
		for (Collection<E> e : collections) {
			if (!e.isEmpty()) return false;
		}
		return true;
	}

	@Override
	public boolean contains(Object o) {
		for (Collection<E> e : collections) {
			if (e.contains(o)) return true;
		}
		return false;
	}

	@Nonnull
	@Override
	public Iterator<E> iterator() {
		Iterator<E>[] itrs = Helpers.cast(new Iterator<?>[collections.length]);
		for (int i = 0; i < collections.length; i++) {
			itrs[i] = collections[i].iterator();
		}
		return new MergedItr<>(itrs);
	}

	public static class MergedItr<T> extends AbstractIterator<T> {
		final List<Iterator<T>> itrs;
		int i;

		@SafeVarargs
		public MergedItr(Iterator<T>... iterators) {
			this.itrs = Arrays.asList(iterators);
		}

		public MergedItr(List<Iterator<T>> iterators) {
			this.itrs = iterators;
		}

		@Override
		public boolean computeNext() {
			while (i < itrs.size()) {
				Iterator<T> itr = itrs.get(i);
				if (itr.hasNext()) {
					result = itr.next();
					return true;
				}
				i++;
			}
			return false;
		}
	}

	@Override
	public boolean remove(Object o) {
		boolean flag = false;
		for (Collection<E> e : collections)
			flag |= e.remove(o);
		return flag;
	}

	@Override
	@Deprecated
	public boolean containsAll(@Nonnull Collection<?> c) {
		return super.containsAll(c);
	}

	@Override
	public boolean removeAll(@Nonnull Collection<?> c) {
		boolean flag = false;
		for (Collection<E> e : collections)
			flag |= e.removeAll(c);
		return flag;
	}

	@Override
	public void clear() {
		for (Collection<E> e : collections)
			e.clear();
	}
}
