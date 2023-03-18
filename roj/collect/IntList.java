package roj.collect;

import roj.util.ArrayCache;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.ListIterator;
import java.util.PrimitiveIterator;

/**
 * @author Roj234
 * @since 2021/5/27 13:37
 */
public class IntList implements Iterable<Integer> {
	public static final int DEFAULT_VALUE = -1;

	protected int[] list;
	protected int size = 0;

	public IntList() {
		list = ArrayCache.INTS;
	}

	public IntList(int size) {
		list = new int[size];
		Arrays.fill(list, DEFAULT_VALUE);
	}

	public void ensureCapacity(int cap) {
		if (list.length < cap) {
			int length = ((cap * 3) >> 1) + 1;
			int[] newList = new int[length];
			if (size > 0) System.arraycopy(list, 0, newList, 0, size);
			list = newList;
			Arrays.fill(list, size, length, DEFAULT_VALUE);
		}
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("IntList[");
		if (size > 0) {
			int i = 0;
			while (true) {
				sb.append(list[i++]);
				if (i == size) break;
				sb.append(", ");
			}
		}
		return sb.append(']').toString();
	}

	@Nonnull
	public int[] toArray() {
		if (size == 0) return ArrayCache.INTS;
		int[] arr = new int[size];
		System.arraycopy(list, 0, arr, 0, size);
		return arr;
	}

	public int[] getRawArray() {
		return list;
	}

	public void setSize(int size) {
		ensureCapacity(size);
		this.size = size;
	}

	public int size() {
		return size;
	}

	public boolean add(int e) {
		ensureCapacity(size + 1);
		list[size++] = e;
		return true;
	}

	public boolean addAll(int[] ints) {
		ensureCapacity(size + ints.length);
		System.arraycopy(ints, 0, list, size, ints.length);
		size += ints.length;
		return true;
	}

	public void trimToSize() {
		if (list.length != size) list = Arrays.copyOf(list, size);
	}

	public void addAll(IntList il) {
		addAll(il.list, il.size);
	}

	public boolean addAll(int[] ints, int len) {
		if (len < 0) throw new NegativeArraySizeException();
		if (len == 0) return false;
		ensureCapacity(size + len);
		System.arraycopy(ints, 0, list, size, len);
		size += len;
		return true;
	}

	public boolean addAll(int i, int[] ints) {
		if (i > size) throw new ArrayIndexOutOfBoundsException(i);
		ensureCapacity(size + ints.length);
		System.arraycopy(list, i, list, i + ints.length, size - i);
		System.arraycopy(ints, 0, list, i, ints.length);
		size += ints.length;
		return true;
	}

	public boolean addAllReversed(int i, int[] collection) {
		if (i > size) throw new ArrayIndexOutOfBoundsException(i);
		ensureCapacity(size + collection.length);
		System.arraycopy(list, i, list, i + collection.length, size - i);
		for (int k = collection.length - 1; k >= 0; k--) {
			list[i++] = collection[k];
		}
		size += collection.length;
		return true;
	}

	public int set(int i, int e) {
		if(i < 0 || i >= size) throw new ArrayIndexOutOfBoundsException(i);
		int o = list[i];
		list[i] = e;
		return o;
	}

	public void add(int i, int e) {
		if (i > size) throw new ArrayIndexOutOfBoundsException(i);
		System.arraycopy(list, i, list, i + 1, size - i);
		list[i] = e;
	}

	public boolean remove(int i) {
		if (i >= 0 && i < size) {
			if (size - 1 - i >= 0) {
				System.arraycopy(list, i + 1, list, i, size - 1 - i);
			}
			size--;
			return true;
		}
		throw new ArrayIndexOutOfBoundsException(i);
	}

	public void removeByValue(int e) {
		int i = indexOf(e);
		if (i >= 0) remove(i);
	}

	public int indexOf(int key) {
		int i = 0;
		while (i < size) {
			if (key == list[i]) return i;
			i++;
		}
		return -1;
	}

	public int lastIndexOf(int key) {
		int i = size;
		while (i >= 0) {
			if (key == list[i]) return i;
			i--;
		}
		return -1;
	}

	@Nonnull
	public PrimitiveIterator.OfInt iterator() {
		return iterator(0);
	}

	@Nonnull
	public PrimitiveIterator.OfInt iterator(int i) {
		Itr itr = new Itr();
		itr.i = i;
		return itr;
	}

	public boolean isEmpty() {
		return size == 0;
	}

	public boolean contains(int o) {
		return indexOf(o) != -1;
	}

	public int get(int i) {
		if (i > size) throw new ArrayIndexOutOfBoundsException(i);
		return list[i];
	}

	public void clear() {
		size = 0;
	}

	private class Itr implements ListIterator<Integer>, PrimitiveIterator.OfInt {
		int i = 0, mark = -1;

		public boolean hasNext() { return i < size; }
		public int nextInt() { return list[mark = i++]; }
		public Integer next() { return nextInt(); }
		public int nextIndex() { return i; }

		public boolean hasPrevious() { return i > 0; }
		public int previousInt() { return list[mark = --i]; }
		public Integer previous() { return previousInt(); }
		public int previousIndex() { return i-1; }

		public void remove() {
			if (mark == -1) throw new IllegalStateException();
			IntList.this.remove(mark);
			if (mark < i) i--;
			mark = -1;
		}
		public void set(Integer v) { list[mark] = v; }
		public void add(Integer v) {
			if (mark == -1) throw new IllegalStateException();

			IntList.this.add(i++, v);
			mark = -1;
		}
	}
}