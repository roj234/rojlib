package roj.kscript.util.opm;

import roj.collect.ArrayIterator;
import roj.util.Helpers;

import java.io.Serializable;
import java.util.AbstractList;
import java.util.Iterator;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/26 18:16
 */
public final class OneList<E> extends AbstractList<E> implements RandomAccess, Serializable {
	private static final long serialVersionUID = 3093736618740652951L;

	private E e;
	private boolean empty;

	public OneList(E obj) {e = obj;}

	public Iterator<E> iterator() {
		return Helpers.cast(new ArrayIterator<>(new Object[] {e}));
	}

	public int size() {return empty ? 0 : 1;}

	public boolean contains(Object obj) {return Objects.equals(obj, e);}

	public E get(int index) {
		if (index != 0 || empty) throw new IndexOutOfBoundsException("Index: " + index + ", Size: 1");
		return e;
	}

	@Override
	public E set(int index, E element) {
		if (index != 0 || empty) throw new IndexOutOfBoundsException("Index: " + index + ", Size: 1");
		E elem = this.e;
		this.e = element;
		return elem;
	}

	@Override
	public void forEach(Consumer<? super E> action) {
		action.accept(e);
	}

	@Override
	public void replaceAll(UnaryOperator<E> operator) {
		e = operator.apply(e);
	}

	public void setEmpty(boolean empty) {
		this.empty = empty;
		if (empty) e = null;
	}
}
