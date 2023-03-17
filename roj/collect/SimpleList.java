package roj.collect;

import roj.util.EmptyArrays;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * @author Roj234
 * @since 2021/5/24 23:26
 */
public class SimpleList<E> extends AbstractList<E> implements RandomAccess {
	protected Object[] list;
	protected int size;

	public byte capacityType;

	public SimpleList() {
		list = EmptyArrays.OBJECTS;
	}

	public SimpleList(int size) {
		list = size == 0 ? EmptyArrays.OBJECTS : new Object[size];
	}

	public SimpleList(int size, int cap) {
		list = size == 0 ? EmptyArrays.OBJECTS : new Object[size];
		capacityType = (byte) cap;
	}

	@SafeVarargs
	@SuppressWarnings("varargs")
	public SimpleList(@Nonnull E... es) {
		list = new Object[es.length];
		System.arraycopy(es, 0, list, 0, es.length);
		size = es.length;
	}

	public SimpleList(Collection<? extends E> c) {
		list = c.size() == 0 ? EmptyArrays.OBJECTS : c.toArray();
		size = list.length;
	}

	@Deprecated
	public static <E> List<E> one(E e) {
		return Collections.singletonList(e);
	}

	public void ensureCapacity(int cap) {
		if (list.length < cap) {
			int newCap = nextCap(cap);
			Object[] newList = new Object[newCap];
			if (size > 0) System.arraycopy(list, 0, newList, 0, size);
			list = newList;
		}
	}

	protected int nextCap(int cap) {
		switch (capacityType) {
			case -1:
				throw new ArrayIndexOutOfBoundsException("ArraySize locked to " + list.length);
			case 0:
			default:
				return cap + 10;
			case 1:
				return (cap * 3) >> 1;
			case 2:
				return cap << 1;
		}
	}

	@Override
	public int indexOf(Object key) {
		if (key == null) {
			return indexOfAddress(null);
		}
		int i = 0;
		while (i < size) {
			if (key.equals(list[i])) {
				return i;
			}
			i++;
		}
		return -1;
	}

	public int indexOfAddress(E key) {
		int i = 0;
		while (i < size) {
			if (list[i] == key) {
				return i;
			}
			i++;
		}
		return -1;
	}

	public void trimToSize() {
		if (list.length != size) {
			list = Arrays.copyOf(list, size);
		}
	}

	@Nonnull
	public Iterator<E> iterator() {
		return listIterator(0);
	}

	@Nonnull
	@Override
	public Object[] toArray() {
		if (size == 0) return EmptyArrays.OBJECTS;
		Object[] arr = new Object[size];
		System.arraycopy(list, 0, arr, 0, size);
		return arr;
	}

	@Nonnull
	@Override
	@SuppressWarnings("unchecked")
	public <T> T[] toArray(@Nonnull T[] arr) {
		if (arr.length < size) {
			return (T[]) Arrays.copyOf(list, size, arr.getClass());
		}
		System.arraycopy(list, 0, arr, 0, size);
		return arr;
	}

	public Object[] getRawArray() {
		return list;
	}

	@Deprecated
	public void setRawArray(Object[] arr) {
		list = arr;
	}

	public int size() {
		return size;
	}

	public boolean add(E e) {
		ensureCapacity(size + 1);
		list[size++] = e; // [1,1,1,2]
		return true; //[3]
	}

	@SafeVarargs
	@SuppressWarnings("varargs")
	public final boolean addAll(E... collection) {
		return addAll(collection, size, 0, collection.length);
	}

	@SafeVarargs
	@SuppressWarnings("varargs")
	public final boolean addAll(int i, E... collection) {
		return addAll(collection, i, 0, collection.length);
	}

	public final boolean addAll(E[] collection, int len) {
		return addAll(collection, size, 0, len);
	}

	public boolean addAll(E[] collection, int index, int start, int len) {
		rangeCheck(index);

		if (len == 0) return false;
		if (len < 0) throw new NegativeArraySizeException();
		if (start < 0) throw new ArrayIndexOutOfBoundsException(start);

		if (start + len > collection.length) throw new ArrayIndexOutOfBoundsException(len);

		ensureCapacity(size + len);
		if (size != index) System.arraycopy(list, index, list, index + len, size - index);
		System.arraycopy(collection, start, list, index, len);
		size += len;
		return true;
	}

	@SafeVarargs
	@SuppressWarnings("varargs")
	public final boolean addAllReversed(int i, E... collection) {
		rangeCheck(i);

		if (collection.length == 0) return false;
		ensureCapacity(size + collection.length);
		if (size - i > 0) System.arraycopy(list, i, list, i + collection.length, size - i);
		for (int k = collection.length - 1; k >= 0; k--) {
			final E e = collection[k];
			list[i++] = e;
		}
		size += collection.length;
		return true;
	}

	void rangeCheck(int i) {
		if (i < 0 || i > size) throw new ArrayIndexOutOfBoundsException(i);
	}

	@Override
	public boolean addAll(@Nonnull Collection<? extends E> collection) {
		return addAll(size, collection);
	}

	@Override
	@SuppressWarnings("unchecked")
	public void replaceAll(UnaryOperator<E> operator) {
		for (int i = 0; i < size; i++) {
			list[i] = operator.apply((E) list[i]);
		}
	}

	@Override
	public boolean addAll(int index, Collection<? extends E> collection) {
		rangeCheck(index);

		if (collection.isEmpty()) return false;

		ensureCapacity(size + collection.size());
		if (size != index && size > 0) System.arraycopy(list, index, list, index + collection.size(), size - index);

		Iterator<? extends E> it = collection.iterator();
		for (int j = index; j < index + collection.size(); j++) {
			list[j] = it.next();
		}
		size += collection.size();
		return true;
	}

	@Override
	public Spliterator<E> spliterator() {
		return Spliterators.spliterator(list, 0, size, Spliterator.ORDERED);
	}

	@Override
	@SuppressWarnings("unchecked")
	public void forEach(Consumer<? super E> action) {
		for (int i = 0; i < size; i++) {
			action.accept((E) list[i]);
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public void sort(Comparator<? super E> c) {
		Arrays.sort(list, 0, size, (Comparator<? super Object>) c);
	}

	@SuppressWarnings("unchecked")
	public <X extends Comparable<E>> int binarySearch(int fromIndex, int toIndex, X key) {
		Object[] list = this.list;
		int low = fromIndex;
		int high = toIndex - 1;

		while (low <= high) {
			int mid = (low + high) >>> 1;
			X midVal = (X) list[mid];

			int v = midVal.compareTo((E) key);
			if (v < 0) {low = mid + 1;} else if (v > 0) {high = mid - 1;} else return mid; // key found
		}
		return -(low + 1);
	}

	@Override
	@SuppressWarnings("unchecked")
	public boolean removeIf(Predicate<? super E> filter) {
		boolean changed = false;
		for (int i = size - 1; i >= 0; i--) {
			E o = (E) list[i];
			if (filter.test(o)) {
				remove(i);
				changed = true;
			}
		}
		return changed;
	}

	@Override
	public final boolean removeAll(@Nonnull Collection<?> collection) {
		return batchRemove(collection, true);
	}

	@Override
	public final boolean retainAll(@Nonnull Collection<?> collection) {
		return batchRemove(collection, false);
	}

	@SuppressWarnings("unchecked")
	protected boolean batchRemove(@Nonnull Collection<?> collection, boolean equ) {
		boolean changed = false;
		for (int i = size - 1; i >= 0; i--) {
			E o = (E) list[i];
			if (collection.contains(o) == equ) {
				remove(i);
				changed = true;
			}
		}
		return changed;
	}

	@SuppressWarnings("unchecked")
	public E set(int i, E e) {
		if (i < 0 || i >= size) throw new ArrayIndexOutOfBoundsException(i);
		Object o = list[i];
		list[i] = e;
		return (E) o;
	}

	@Override
	public void add(int i, E e) {
		if (i < 0 || i > size) throw new ArrayIndexOutOfBoundsException(i);
		ensureCapacity(size + 1);
		if (i != size) System.arraycopy(list, i, list, i + 1, size - i);
		list[i] = e;
		size++;
	}

	@Override
	public boolean remove(Object e) {
		int index = indexOf(e);
		if (index >= 0) {
			remove(index);
			return true;
		}

		return false;
	}

	public void removeRange(int begin, int end) {
		if (begin >= end) return;
		// will throw exceptions if out of bounds...
		System.arraycopy(list, end, list, begin, size - end);

		int size1 = size;
		for (int i = size = begin + size - end; i < size1; i++) {
			list[i] = null;
		}
	}

	@SuppressWarnings("unchecked")
	public E remove(int index) {
		if (index >= 0 && index < size) {
			Object o = list[index];
			if (size - 1 - index > 0) {
				System.arraycopy(list, index + 1, list, index, size - 1 - index);
			}

			list[--size] = null;
			return (E) o;
		}
		throw new ArrayIndexOutOfBoundsException(index);
	}

	@Override
	public int lastIndexOf(Object key) {
		int i = size;
		while (i >= 0) {
			if (Objects.equals(key, list[i])) {
				return i;
			}
			i--;
		}
		return -1;
	}

	@Nonnull
	@Override
	public ListIterator<E> listIterator() {
		return listIterator(0);
	}

	@Nonnull
	@Override
	public ListIterator<E> listIterator(int i) {
		return new SimpleItr(i);
	}

	@Override
	public boolean contains(Object o) {
		return indexOf(o) != -1;
	}

	@SuppressWarnings("unchecked")
	public E get(int i) {
		if (i < 0 || i >= size) throw new ArrayIndexOutOfBoundsException(i);
		return (E) list[i];
	}

	public void fastClear() {
		size = 0;
	}

	public void clear() {
		if (list == null || size == 0) return;
		for (int i = 0; i < size; i++) {
			list[i] = null;
		}
		size = 0;
	}

	public boolean equals(Object o) {
		if (o == this) return true;
		if (!(o instanceof List)) return false;
		List<?> l = (List<?>) o;
		if (l.size() != size) return false;
		if (o instanceof RandomAccess) {
			for (int i = 0; i < size; i++) {
				Object o1 = list[i];
				Object o2 = l.get(i);
				if (!(o1 == null ? o2 == null : o1.equals(o2))) return false;
			}
		} else {
			ListIterator<?> e2 = l.listIterator();
			for (int i = 0; i < size; i++) {
				Object o1 = list[i];
				Object o2 = e2.next();
				if (!(o1 == null ? o2 == null : o1.equals(o2))) return false;
			}
		}
		return true;
	}

	public int hashCode() {
		int v = 1;
		for (int i = 0; i < size; i++) {
			Object e = list[i];
			v = 31 * v + (e == null ? 0 : e.hashCode());
		}
		return v;
	}

	public void i_setSize(int i) {
		this.size = i;
	}

	private final class SimpleItr implements ListIterator<E> {
		private int index, prevId;

		public SimpleItr() {
		}

		public SimpleItr(int index) {
			this.index = index;
		}

		@Override
		public boolean hasNext() {
			return index < size;
		}

		@Override
		@SuppressWarnings("unchecked")
		public E next() {
			return (E) SimpleList.this.list[prevId = index++];
		}

		@Override
		public boolean hasPrevious() {
			return index > 0;
		}

		@Override
		@SuppressWarnings("unchecked")
		public E previous() {
			return (E) SimpleList.this.list[prevId = index--];
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
			SimpleList.this.remove(index = prevId);
		}

		@Override
		public void set(E e) {
			SimpleList.this.set(prevId, e);
		}

		@Override
		public void add(E e) {
			SimpleList.this.add(prevId, e);
		}
	}
}