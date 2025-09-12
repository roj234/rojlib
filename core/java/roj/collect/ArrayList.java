package roj.collect;

import org.jetbrains.annotations.Contract;
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
public class ArrayList<E> extends AbstractCollection<E> implements List<E>, RandomAccess {
	protected Object[] list;
	protected int size;

	public ArrayList() { list = ArrayCache.OBJECTS; }
	public ArrayList(int size) { list = size == 0 ? ArrayCache.OBJECTS : new Object[size]; }
	public ArrayList(Collection<? extends E> c) {
		list = c.isEmpty() ? ArrayCache.OBJECTS : c.toArray();
		size = list.length;
	}

	public void ensureCapacity(int cap) {
		if (list.length < cap) {
			int newCap;
			if (list.length == 0) newCap = cap;
			else if (cap > 1000) newCap = MathUtils.nextPowerOfTwo(cap);
			else if (cap > 100) newCap = cap + (cap >> 1);
			else newCap = cap + 11;

			Object[] newList = new Object[newCap];
			if (size > 0) System.arraycopy(list, 0, newList, 0, size);
			list = newList;
		}
	}

	@SafeVarargs
	public static <T> ArrayList<T> asModifiableList(T... args) {
		ArrayList<T> list = new ArrayList<>();
		list.list = args;
		list.size = args.length;
		return list;
	}

	public static <T> ArrayList<T> hugeCapacity(int initialCapacity) {
		return new ArrayList<>(initialCapacity) {
			public void ensureCapacity(int cap) {
				if (list.length < cap) {
					int newCap = list.length == 0 ? cap : MathUtils.nextPowerOfTwo(cap);
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

	@Contract(mutates = "this")
	public void trimToSize() { if (list.length != size) list = Arrays.copyOf(list, size); }

	@SuppressWarnings("unchecked")
	@Contract(mutates = "this")
	public E pop() {
		if (size == 0) return null;
		Object o = list[--size];
		list[size] = null;
		return (E) o;
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

	public Object[] getInternalArray() {return list;}
	public void _setArray(Object[] arr) {list = arr;}
	public void _setSize(int i) {this.size = i;}

	public int size() { return size; }

	public boolean add(E e) {
		ensureCapacity(size+1);
		list[size++] = e;
		return true;
	}

	@SafeVarargs
	@Contract(mutates = "this")
	@SuppressWarnings("varargs")
	public final boolean addAll(E... collection) {
		return addAll(collection, size, 0, collection.length);
	}

	@SafeVarargs
	@Contract(mutates = "this")
	@SuppressWarnings("varargs")
	public final boolean addAll(int i, E... collection) {
		return addAll(collection, i, 0, collection.length);
	}

	@Contract(mutates = "this")
	public final boolean addAll(E[] collection, int len) {
		return addAll(collection, size, 0, len);
	}

	@Contract(mutates = "this")
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
		int j = index;
		do {
			list[j++] = it.next();
		} while (it.hasNext() && j != index + collection.size());
		size += j - index;
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
	@Contract(mutates = "this")
	public void sort(int from, int to, Comparator<? super E> c) { Arrays.sort(list, from, to, (Comparator<? super Object>) c); }

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
	public final boolean removeAll(@NotNull Collection<?> collection) {return batchRemove(collection, true);}
	@Override
	public final boolean retainAll(@NotNull Collection<?> collection) {return batchRemove(collection, false);}

	@SuppressWarnings("unchecked")
	@Contract(mutates = "this")
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
		if (i >= size) throw new ArrayIndexOutOfBoundsException(i);
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

	@Contract(mutates = "this")
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
		if (index >= size) throw new ArrayIndexOutOfBoundsException(index);
		Object o = list[index];
		if (size - 1 - index > 0) System.arraycopy(list, index + 1, list, index, size - 1 - index);
		list[--size] = null;
		return (E) o;

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
	public List<E> subList(int fromIndex, int toIndex) { return new SubList(fromIndex, toIndex); }

	@Override
	public boolean contains(Object o) { return indexOf(o) != -1; }

	@SuppressWarnings("unchecked")
	public E get(int i) {
		if (i < 0 || i >= size) throw new ArrayIndexOutOfBoundsException(i);
		return (E) list[i];
	}
	@SuppressWarnings("unchecked")
	public E get(int i, E def) {
		if (i < 0 || i >= size) return def;
		return (E) list[i];
	}

	public E getLast() {return getLast(null);}
	@SuppressWarnings("unchecked")
	public E getLast(E def) {return size == 0 ? def : (E) list[size-1];}

	public void clear() {
		if (list == null || size == 0) return;
		for (int i = 0; i < size; i++) {
			list[i] = null;
		}
		size = 0;
	}

	public boolean equals(Object o) {
		if (o == this) return true;
		if (!(o instanceof List<?> l)) return false;
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

	@Override
	public String toString() { return ArrayUtil.toString(list,0,size); }

	private class Itr implements ListIterator<E> {
		int cursor, mark = -1;

		Itr(int pos) { this.cursor = pos; }

		public boolean hasNext() { return cursor < size; }
		@SuppressWarnings("unchecked")
		public E next() { return (E) list[mark = cursor++]; }
		public int nextIndex() { return cursor; }

		public boolean hasPrevious() { return cursor > 0; }
		@SuppressWarnings("unchecked")
		public E previous() { return (E) list[mark = --cursor]; }
		public int previousIndex() { return cursor-1; }

		public void remove() {
			if (mark == -1) throw new IllegalStateException();
			ArrayList.this.remove(mark);
			if (mark < cursor) cursor--;
			mark = -1;
		}
		public void set(E v) { list[mark] = v; }
		public void add(E v) {
			if (mark == -1) throw new IllegalStateException();

			ArrayList.this.add(cursor++, v);
			mark = -1;
		}
	}

	private final class SubList extends AbstractList<E> {
		private final int offset;
		private int size;

		SubList(int fromIndex, int toIndex) {
			if (fromIndex < 0) throw new IndexOutOfBoundsException("fromIndex = " + fromIndex);
			if (toIndex > ArrayList.this.size()) throw new IndexOutOfBoundsException("toIndex = " + toIndex);
			if (fromIndex > toIndex) throw new IllegalArgumentException("fromIndex(" + fromIndex + ") > toIndex(" + toIndex + ")");

			offset = fromIndex;
			size = toIndex - fromIndex;
		}

		public E set(int index, E element) {
			rangeCheck(index);
			return ArrayList.this.set(index+offset, element);
		}

		public E get(int index) {
			rangeCheck(index);
			return ArrayList.this.get(index+offset);
		}

		public int size() {return size;}

		public void add(int index, E element) {
			rangeCheckForAdd(index);
			ArrayList.this.add(index+offset, element);
			size++;
		}

		public E remove(int index) {
			rangeCheck(index);
			E result =  ArrayList.this.remove(index+offset);
			size--;
			return result;
		}

		protected void removeRange(int fromIndex, int toIndex) {
			ArrayList.this.removeRange(fromIndex+offset, toIndex+offset);
			size -= (toIndex-fromIndex);
		}

		public boolean addAll(Collection<? extends E> c) {return addAll(size, c);}

		public boolean addAll(int index, Collection<? extends E> c) {
			rangeCheckForAdd(index);
			int cSize = c.size();
			if (cSize==0)
				return false;

			ArrayList.this.addAll(offset+index, c);
			size += cSize;
			return true;
		}

		public Iterator<E> iterator() { return listIterator(); }
		public ListIterator<E> listIterator(final int index) {
			rangeCheckForAdd(index);

			return new Itr(index+offset) {
				public boolean hasNext() {return nextIndex() < size;}

				public E next() {
					if (hasNext()) return super.next();
					else throw new NoSuchElementException();
				}

				public boolean hasPrevious() {return previousIndex() >= 0;}

				public E previous() {
					if (hasPrevious()) return super.previous();
					else throw new NoSuchElementException();
				}

				public int nextIndex() {return super.nextIndex() - offset;}
				public int previousIndex() {return super.previousIndex() - offset;}

				public void remove() {super.remove();size--;}
				public void set(E e) {super.set(e);}
				public void add(E e) {super.add(e);size++;}
			};
		}

		public List<E> subList(int fromIndex, int toIndex) {
			return new SubList(offset+fromIndex, offset+toIndex);
		}

		private void rangeCheck(int index) {
			if (index < 0 || index >= size) throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
		}

		private void rangeCheckForAdd(int index) {
			if (index < 0 || index > size) throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
		}

		private String outOfBoundsMsg(int index) {return "Index: "+index+", Size: "+size;}
	}
}