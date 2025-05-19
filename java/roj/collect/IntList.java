package roj.collect;

import org.jetbrains.annotations.NotNull;
import roj.compiler.api.RandomAccessible;
import roj.reflect.Unaligned;
import roj.util.ArrayCache;
import roj.util.TimSortForEveryone;

import java.util.Arrays;
import java.util.ListIterator;
import java.util.PrimitiveIterator;

/**
 * @author Roj234
 * @since 2021/5/27 13:37
 */
@RandomAccessible
public class IntList implements Iterable<Integer> {
	protected int[] list;
	protected int size;

	public IntList() {list = ArrayCache.INTS;}
	public IntList(int size) {list = (int[]) Unaligned.U.allocateUninitializedArray(int.class, size);}

	public void ensureCapacity(int cap) {
		if (list.length < cap) {
			int length = ((cap * 3) >> 1) + 1;
			int[] newList = (int[]) Unaligned.U.allocateUninitializedArray(int.class, length);
			if (size > 0) System.arraycopy(list, 0, newList, 0, size);
			list = newList;
		}
	}

	public boolean isEmpty() {return size == 0;}
	public int size() {return size;}
	public void setSize(int size) {
		ensureCapacity(size);
		this.size = size;
	}
	public boolean contains(int o) {return indexOf(o) != -1;}

	@NotNull
	public PrimitiveIterator.OfInt iterator() {return iterator(0);}
	@NotNull
	public PrimitiveIterator.OfInt iterator(int i) {
		Itr itr = new Itr();
		itr.i = i;
		return itr;
	}

	@NotNull
	public int[] toArray() {
		if (size == 0) return ArrayCache.INTS;
		return Arrays.copyOf(list, size);
	}

	public boolean add(int e) {
		ensureCapacity(size + 1);
		list[size++] = e;
		return true;
	}

	public int pop() {
		if (size == 0) throw new ArrayIndexOutOfBoundsException(-1);
		return list[--size];
	}

	public boolean addAll(int[] ints) {
		ensureCapacity(size + ints.length);
		System.arraycopy(ints, 0, list, size, ints.length);
		size += ints.length;
		return true;
	}

	public void addAll(IntList il) {addAll(il.list, il.size);}

	public boolean addAll(int[] ints, int len) {
		if (len < 0) throw new ArrayIndexOutOfBoundsException(len);
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

	public void clear() {size = 0;}
	public void trimToSize() {if (list.length != size) list = Arrays.copyOf(list, size);}

	public int get(int i) {
		if (i >= size) throw new ArrayIndexOutOfBoundsException(i);
		return list[i];
	}

	public int set(int i, int e) {
		if (i >= size) throw new ArrayIndexOutOfBoundsException(i);
		int o = list[i];
		list[i] = e;
		return o;
	}

	public void add(int i, int e) {
		if (i > size) throw new ArrayIndexOutOfBoundsException(i);
		ensureCapacity(size + 1);
		if (i != size) System.arraycopy(list, i, list, i + 1, size - i);
		list[i] = e;
		size++;
	}

	public int remove(int i) {
		if (i >= size) throw new ArrayIndexOutOfBoundsException(i);

		int val = list[i];
		if (size - 1 - i > 0) System.arraycopy(list, i + 1, list, i, size - 1 - i);
		size--;
		return val;
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

	public void removeRange(int begin, int end) {
		if (begin >= end) return;
		// will throw exceptions if out of bounds...
		System.arraycopy(list, end, list, begin, size - end);
		size -= end - begin;
	}

	public int[] getRawArray() {return list;}

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

	public void sort() {
		Arrays.sort(list, 0, size);
	}
	public void sortUnsigned() {
		TimSortForEveryone.sort(0, size, (refLeft, offLeft, offRight) ->
				Integer.compareUnsigned(Unaligned.U.getInt(refLeft, offLeft), Unaligned.U.getInt(offRight)),
		list, Unaligned.ARRAY_INT_BASE_OFFSET, 4);
	}

	private class Itr implements ListIterator<Integer>, PrimitiveIterator.OfInt {
		int i = 0, mark = -1;

		public boolean hasNext() {return i < size;}
		public Integer next() {return nextInt();}
		public int nextInt() {return list[mark = i++];}
		public boolean hasPrevious() {return i > 0;}
		public Integer previous() {return previousInt();}
		public int nextIndex() {return i;}
		public int previousIndex() {return i - 1;}

		public void remove() {
			if (mark == -1) throw new IllegalStateException();
			IntList.this.remove(mark);
			if (mark < i) i--;
			mark = -1;
		}

		public void set(Integer v) {list[mark] = v;}
		public void add(Integer v) {
			if (mark == -1) throw new IllegalStateException();

			IntList.this.add(i++, v);
			mark = -1;
		}

		public int previousInt() {return list[mark = --i];}
	}
}