package roj.collect;

import org.jetbrains.annotations.NotNull;
import roj.util.ArrayUtil;
import roj.util.Helpers;

import java.util.*;

/**
 * @author Roj234
 * @since 2021/2/2 19:59
 */
public class SortedList<E> extends AbstractList<E> {
	static final int DEF_SIZE = 16;

	protected final Comparator<E> cmp;

	protected Object[] entries;
	protected int size;

	protected final int binarySearch(E key) {
		key.getClass();
		return ArrayUtil.binarySearch(entries, 0, size, key, Helpers.cast(cmp));
	}

	public SortedList(Comparator<E> cmp) {
		this(DEF_SIZE, cmp);
	}

	public SortedList(int capacity, Comparator<E> cmp) {
		if (capacity <= 1) capacity = DEF_SIZE;
		this.entries = Helpers.cast(new Object[capacity]);
		this.cmp = cmp == null ? Helpers.cast(Comparator.naturalOrder()) : cmp;
	}

	public void ensureCapacity(int cap) {
		if (entries.length >= cap) return;
		Object[] entriesO = entries;

		Object[] entriesN = new Object[cap];
		System.arraycopy(entriesO, 0, entriesN, 0, size);
		this.entries = entriesN;
	}

	public Object[] array() {
		return entries;
	}

	@Override
	public boolean remove(Object o) {
		int i = indexOf(o);
		return i != -1 && remove(i) != null;
	}

	@Override
	public boolean addAll(int index, @NotNull Collection<? extends E> c) {
		throw new UnsupportedOperationException();
	}

	@SuppressWarnings("unchecked")
	private E doAdd(E o, int doAdd) {
		int i = binarySearch(o);
		if (i >= 0) {
			return doAdd == 1 ? null : (E) entries[i];
		} else {
			if (doAdd == 0) return null;

			i = -i - 1;

			if (size == entries.length-1) ensureCapacity(size<<1);

			Object[] arr = entries;
			if (size - i > 0) System.arraycopy(arr, i, arr, i+1, size-i);
			arr[i] = o;
			size++;
			return o;
		}
	}

	public E find(E e) {
		E e1 = doAdd(e, 0);
		return e1 == null ? e : e1;
	}
	public E intern(E e) { return doAdd(e, -1); }
	public boolean add(E node) { return doAdd(node, 1) != null; }

	@SuppressWarnings("unchecked")
	public E remove(int idx) {
		if (idx >= size) throw new ArrayIndexOutOfBoundsException(idx);

		Object[] data1 = this.entries;
		E e = (E) data1[idx];
		if (size - idx - 1 > 0) System.arraycopy(data1, idx + 1, data1, idx, size - idx - 1);
		data1[--size] = null;
		return e;
	}

	public void removeRange(int begin, int end) {
		if (begin >= end) return;
		System.arraycopy(entries, end, entries, begin, size - end);

		int size1 = size;
		for (int i = size = begin + size - end; i < size1; i++) {
			entries[i] = null;
		}
	}

	public E pop() {
		return remove(0);
	}

	@SuppressWarnings("unchecked")
	public int indexOf(Object o) {
		int index = binarySearch((E) o);

		return index >= 0 ? index : -1;
	}

	@Override
	public int lastIndexOf(Object o) {
		return indexOf(o);
	}

	@SuppressWarnings("unchecked")
	public E top() {
		if (0 >= size) throw new ArrayIndexOutOfBoundsException(0);
		return (E) entries[0];
	}

	@SuppressWarnings("unchecked")
	public E bottom() {
		if (0 >= size) throw new ArrayIndexOutOfBoundsException(-1);
		return (E) entries[size - 1];
	}

	@SuppressWarnings("unchecked")
	public E get(int idx) {
		if (idx >= size) throw new ArrayIndexOutOfBoundsException(idx);
		return (E) entries[idx];
	}

	@Override
	@SuppressWarnings("unchecked")
	public E set(int idx, E el) {
		if (idx >= size) throw new ArrayIndexOutOfBoundsException(idx);
		E oel = (E) entries[idx];
		entries[idx] = el;
		return oel;
	}

	public void clear() {
		for (int i = 0; i < size; i++) {
			entries[i] = null;
		}
		size = 0;
	}

	public int size() {
		return size;
	}

	@Override
	public boolean contains(Object o) {
		return indexOf(o) != -1;
	}

	@NotNull
	@Override
	public Iterator<E> iterator() {
		return listIterator(0);
	}

	@NotNull
	@Override
	@SuppressWarnings("unchecked")
	public ListIterator<E> listIterator(int index) {
		return entries == null || size == 0 ? Collections.emptyListIterator() : new ArrayIterator<>((E[]) entries, index, size);
	}

	@Override
	public void sort(Comparator<? super E> c) {
		if (c != cmp && c != null) throw new UnsupportedOperationException();
	}

	public void i_addNoCmp(E ent) {
		ensureCapacity(size+2);
		entries[size++] = ent;
	}
}