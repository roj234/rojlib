package roj.collect;

import org.jetbrains.annotations.NotNull;
import roj.math.MathUtils;
import roj.util.ArrayCache;
import roj.util.ArrayUtil;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * @author Roj234
 * @since 2021/5/24 23:26
 */
public class SimpleList<E> implements List<E>, RandomAccess {
	protected Object[] list;
	protected int size;

	public SimpleList() { list = ArrayCache.OBJECTS; }
	public SimpleList(int size) { list = size == 0 ? ArrayCache.OBJECTS : new Object[size]; }
	public SimpleList(Collection<? extends E> c) {
		list = c.isEmpty() ? ArrayCache.OBJECTS : c.toArray();
		size = list.length;
	}

	public void ensureCapacity(int cap) {
		if (list.length < cap) {
			int newCap = list.length == 0 ? cap : cap > 65536 ? MathUtils.getMin2PowerOf(cap) : cap > 512 ? cap+512 : cap+10;
			Object[] newList = new Object[newCap];
			if (size > 0) System.arraycopy(list, 0, newList, 0, size);
			list = newList;
		}
	}

	@SafeVarargs
	public static <T> SimpleList<T> asModifiableList(T... args) {
		SimpleList<T> list = new SimpleList<>();
		list.list = args;
		list.size = args.length;
		return list;
	}

	public static <T> SimpleList<T> withCapacityType(int initialCapacity, int capacityType) {
		return new SimpleList<T>(initialCapacity) {
			int nextCap(int cap) {
				switch (capacityType&3) {
					default: case 0: return cap + 10;
					case 1: return 1 + ((cap*3) >> 1);
					case 2: return MathUtils.getMin2PowerOf(cap+1);
					case 3: throw new ArrayIndexOutOfBoundsException("Capacity locked: " + list.length);
				}
			}

			public void ensureCapacity(int cap) {
				if (list.length < cap) {
					int newCap = list.length == 0 ? cap : nextCap(cap);
					Object[] newList = new Object[newCap];
					if (size > 0) System.arraycopy(list, 0, newList, 0, size);
					list = newList;
				}
			}
		};
	}

	@Override
	public int indexOf(Object key) {
		if (key == null) return indexOfAddress(null);
		return indexOf(key, 0);
	}

	public int indexOf(Object key, int i) {
		while (i < size) {
			if (key.equals(list[i])) return i;
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

	public void trimToSize() { if (list.length != size) list = Arrays.copyOf(list, size); }

	@SuppressWarnings("unchecked")
	public E pop() {
		return size == 0 ? null : (E) list[--size];
	}

	@NotNull
	public Iterator<E> iterator() { return listIterator(0); }

	@NotNull
	@Override
	public Object[] toArray() {
		if (size == 0) return ArrayCache.OBJECTS;
		return Arrays.copyOf(list, size);
	}

	@NotNull
	@Override
	@SuppressWarnings("unchecked")
	public <T> T[] toArray(@NotNull T[] a) {
		if (a.length < size) return (T[]) Arrays.copyOf(list, size, a.getClass());

		System.arraycopy(list, 0, a, 0, size);
		if (a.length > size) a[size] = null;

		return a;
	}

	public Object[] getInternalArray() {
		return list;
	}

	@Deprecated
	public void setRawArray(Object[] arr) {
		list = arr;
	}

	public boolean isEmpty() { return size == 0; }
	public int size() { return size; }

	public boolean add(E e) {
		ensureCapacity(size+1);
		list[size++] = e;
		return true;
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
	public boolean addAll(@NotNull Collection<? extends E> collection) {
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
	public void sort(Comparator<? super E> c) { Arrays.sort(list, 0, size, (Comparator<? super Object>) c); }
	@SuppressWarnings("unchecked")
	public void sort(int from, int to, Comparator<? super E> c) { Arrays.sort(list, from, to, (Comparator<? super Object>) c); }

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
	public final boolean removeAll(@NotNull Collection<?> collection) {
		return batchRemove(collection, true);
	}

	@Override
	public final boolean retainAll(@NotNull Collection<?> collection) {
		return batchRemove(collection, false);
	}

	@SuppressWarnings("unchecked")
	protected boolean batchRemove(@NotNull Collection<?> collection, boolean equ) {
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

	@Override
	public boolean containsAll(@NotNull Collection<?> c) {
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

	@NotNull
	@Override
	public ListIterator<E> listIterator() { return listIterator(0); }
	@NotNull
	@Override
	public ListIterator<E> listIterator(int i) { return new Itr(i); }

	@NotNull
	@Override
	public List<E> subList(int fromIndex, int toIndex) { return new SubList<>(this, fromIndex, toIndex); }

	@Override
	public boolean contains(Object o) { return indexOf(o) != -1; }

	@SuppressWarnings("unchecked")
	public E get(int i) {
		if (i < 0 || i >= size) throw new ArrayIndexOutOfBoundsException(i);
		return (E) list[i];
	}

	public E getLast() { return getLast(null); }
	@SuppressWarnings("unchecked")
	public E getLast(E def) {
		return size == 0 ? def : (E) list[size-1];
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

	@Override
	public String toString() { return ArrayUtil.toString(list,0,size); }

	private final class Itr implements ListIterator<E> {
		int i, mark = -1;

		Itr(int pos) { this.i = pos; }

		public boolean hasNext() { return i < size; }
		@SuppressWarnings("unchecked")
		public E next() { return (E) list[mark = i++]; }
		public int nextIndex() { return i; }

		public boolean hasPrevious() { return i > 0; }
		@SuppressWarnings("unchecked")
		public E previous() { return (E) list[mark = --i]; }
		public int previousIndex() { return i-1; }

		public void remove() {
			if (mark == -1) throw new IllegalStateException();
			SimpleList.this.remove(mark);
			if (mark < i) i--;
			mark = -1;
		}
		public void set(E v) { list[mark] = v; }
		public void add(E v) {
			if (mark == -1) throw new IllegalStateException();

			SimpleList.this.add(i++, v);
			mark = -1;
		}
	}

	private static final
	class SubList<E> extends AbstractList<E> {
		private final SimpleList<E> l;
		private final int offset;
		private int size;

		SubList(SimpleList<E> list, int fromIndex, int toIndex) {
			if (fromIndex < 0)
				throw new IndexOutOfBoundsException("fromIndex = " + fromIndex);
			if (toIndex > list.size())
				throw new IndexOutOfBoundsException("toIndex = " + toIndex);
			if (fromIndex > toIndex)
				throw new IllegalArgumentException("fromIndex(" + fromIndex +
					") > toIndex(" + toIndex + ")");
			l = list;
			offset = fromIndex;
			size = toIndex - fromIndex;
		}

		public E set(int index, E element) {
			rangeCheck(index);
			return l.set(index+offset, element);
		}

		public E get(int index) {
			rangeCheck(index);
			return l.get(index+offset);
		}

		public int size() {
			return size;
		}

		public void add(int index, E element) {
			rangeCheckForAdd(index);
			l.add(index+offset, element);
			size++;
		}

		public E remove(int index) {
			rangeCheck(index);
			E result = l.remove(index+offset);
			size--;
			return result;
		}

		protected void removeRange(int fromIndex, int toIndex) {
			l.removeRange(fromIndex+offset, toIndex+offset);
			size -= (toIndex-fromIndex);
		}

		public boolean addAll(Collection<? extends E> c) {
			return addAll(size, c);
		}

		public boolean addAll(int index, Collection<? extends E> c) {
			rangeCheckForAdd(index);
			int cSize = c.size();
			if (cSize==0)
				return false;

			l.addAll(offset+index, c);
			size += cSize;
			return true;
		}

		public Iterator<E> iterator() { return listIterator(); }
		public ListIterator<E> listIterator(final int index) {
			rangeCheckForAdd(index);

			return new ListIterator<E>() {
				private final ListIterator<E> i = l.listIterator(index+offset);

				public boolean hasNext() {
					return nextIndex() < size;
				}

				public E next() {
					if (hasNext())
						return i.next();
					else
						throw new NoSuchElementException();
				}

				public boolean hasPrevious() {
					return previousIndex() >= 0;
				}

				public E previous() {
					if (hasPrevious())
						return i.previous();
					else
						throw new NoSuchElementException();
				}

				public int nextIndex() {
					return i.nextIndex() - offset;
				}

				public int previousIndex() {
					return i.previousIndex() - offset;
				}

				public void remove() {
					i.remove();
					size--;
				}

				public void set(E e) {
					i.set(e);
				}

				public void add(E e) {
					i.add(e);
					size++;
				}
			};
		}

		public List<E> subList(int fromIndex, int toIndex) {
			return new SubList<>(l, offset+fromIndex, offset+toIndex);
		}

		private void rangeCheck(int index) {
			if (index < 0 || index >= size)
				throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
		}

		private void rangeCheckForAdd(int index) {
			if (index < 0 || index > size)
				throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
		}

		private String outOfBoundsMsg(int index) {
			return "Index: "+index+", Size: "+size;
		}
	}
}