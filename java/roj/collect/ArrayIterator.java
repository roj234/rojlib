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
		this.prev = i - 1;
		this.len = j;
	}

	private final E[] list;

	private int i, prev;
	private final int len;

	public boolean hasNext() { return i < len; }
	public E next() {
		if (i >= len) throw new NoSuchElementException();
		return list[prev = i++];
	}
	public int nextIndex() { return i; }

	public boolean hasPrevious() { return i > 0; }
	public E previous() { return list[prev = i--]; }
	public int previousIndex() {return i-1;}

	public void remove() { throw new UnsupportedOperationException(); }
	public void set(E e) { list[prev] = e; }
	public void add(E e) { throw new UnsupportedOperationException(); }
}