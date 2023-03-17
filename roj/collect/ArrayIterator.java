package roj.collect;

import java.util.ListIterator;
import java.util.NoSuchElementException;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class ArrayIterator<E> implements ListIterator<E> {
	public ArrayIterator(E[] array, int i, int j) {
		this.list = array;
		this.index = i;
		this.prevId = i - 1;
		this.length = j;
	}

	public ArrayIterator(E[] array, int i) {
		this.list = array;
		this.index = i;
		this.prevId = i - 1;
		this.length = array.length;
	}

	public ArrayIterator(E[] array) {
		this.list = array;
		this.length = array.length;
	}

	private final E[] list;

	protected int index = 0;
	protected int prevId = -1;
	protected int length;

	@Override
	public boolean hasNext() {
		return index < length;
	}

	@Override
	public E next() {
		if (index >= length) throw new NoSuchElementException();
		return list[prevId = index++];
	}

	@Override
	public boolean hasPrevious() {
		return index > 0;
	}

	@Override
	public E previous() {
		return list[prevId = index--];
	}

	@Override
	public int nextIndex() {
		return index + 1;
	}

	@Override
	public int previousIndex() {
		return index - 1;
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException();
		//list[prevId] = null;
	}

	@Override
	public void set(E e) {
		list[prevId] = e;
	}

	@Override
	public void add(E e) {
		throw new UnsupportedOperationException();
	}
}