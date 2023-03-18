package roj.collect;

import java.util.ListIterator;
import java.util.NoSuchElementException;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public final class ArrayIterator<E> implements ListIterator<E> {
	public ArrayIterator(E[] array, int i, int j) {
		this.list = array;
		this.i = i;
		this.mark = i - 1;
		this.len = j;
	}

	public ArrayIterator(E[] array, int i) {
		this.list = array;
		this.i = i;
		this.mark = i - 1;
		this.len = array.length;
	}

	public ArrayIterator(E[] array) {
		this.list = array;
		this.len = array.length;
	}

	private final E[] list;

	private int i = 0;
	private int mark = -1;
	private final int len;

	public boolean hasNext() { return i < len; }
	public E next() {
		if (i >= len) throw new NoSuchElementException();
		return list[mark = i++];
	}
	public int nextIndex() { return i; }

	public boolean hasPrevious() { return i > 0; }
	public E previous() { return list[mark = i--]; }
	public int previousIndex() {
		return i-1;
	}

	public void remove() { throw new UnsupportedOperationException(); }
	public void set(E e) { list[mark] = e; }
	public void add(E e) { throw new UnsupportedOperationException(); }
}