package roj.collect;

import org.jetbrains.annotations.NotNull;
import roj.math.MathUtils;
import roj.text.TextUtil;
import roj.util.ArrayCache;

import java.util.*;

/**
 * 简单的环形缓冲区实现
 * 除此之外，还可以作为一个有界的ArrayDeque使用
 *
 * @author Roj234
 * @since 2021/4/13 23:25
 */
public class RingBuffer<E> extends AbstractCollection<E> implements Deque<E> {
	protected int maxCap;
	protected Object[] array;

	protected int head, tail, size;

	public static <T> RingBuffer<T> bounded(int capacity) {return new RingBuffer<>(capacity);}
	public static <T> RingBuffer<T> unbounded() {return new RingBuffer<>(0, Integer.MAX_VALUE);}
	public static <T> RingBuffer<T> lazy(int maxCapacity) {return new RingBuffer<>(0, maxCapacity);}

	public RingBuffer(int capacity) {
		// not check == 0, for SerializerFactory
		if (capacity < 0) throw new IllegalArgumentException("Capacity must >= 0");
		maxCap = capacity == 0 ? Integer.MAX_VALUE : capacity;
		array = capacity == 0 ? ArrayCache.OBJECTS : new Object[capacity];
	}
	public RingBuffer(int capacity, int maxCapacity) {
		if (maxCapacity <= 0) throw new IllegalArgumentException("MaxCapacity must > 0");
		maxCap = maxCapacity;
		array = capacity == 0 ? ArrayCache.OBJECTS : new Object[capacity];
	}

	public int capacity() {return maxCap;}
	public void setCapacity(int capacity) {
		maxCap = capacity;
		if (array.length > capacity && capacity > 0) {
			if (size > 0) refit(capacity);
			else array = new Object[capacity];
		}
	}

	private void ensureCapacity() {
		int capacity = array.length;
		if (capacity > 64) capacity = MathUtils.getMin2PowerOf(capacity+1);
		else capacity += 10;

		if (capacity > maxCap) capacity = maxCap;

		if (size > 0) refit(capacity);
		else array = new Object[capacity];
	}

	private void refit(int capacity) {
		Object[] newArray = new Object[capacity];
		int j = 0;

		int i = head;
		int fence = tail;
		Object[] arr = array;
		do {
			if (j == newArray.length) break;
			newArray[j++] = arr[i++];

			if (i == arr.length) i = 0;
		} while (i != fence);

		array = newArray;
		head = 0;
		tail = j;
		size = Math.min(j, size);
	}

	@NotNull
	@Override
	public Iterator<E> iterator() {return size == 0 ? Collections.emptyIterator() : new Itr(false);}
	@NotNull
	@Override
	public Iterator<E> descendingIterator() {return size == 0 ? Collections.emptyIterator() : new Itr(true);}
	private final class Itr extends AbstractIterator<E> {
		int i, dir, fence;

		@SuppressWarnings("unchecked")
		public Itr(boolean rev) {
			if (size == 0) {
				stage = ENDED;
				return;
			}

			if (rev) {
				i = (tail == 0 ? array.length : tail) - 1;
				dir = -1;
				fence = (head == 0 ? array.length : head) - 1;
			} else {
				i = head;
				dir = 1;
				fence = tail;
			}

			stage = CHECKED;
			result = (E) array[i];
			i += dir;
		}

		@Override
		@SuppressWarnings("unchecked")
		public boolean computeNext() {
			if (i == -1) {
				if (size < array.length) return false;
				i = array.length - 1;
			} else if (i == array.length) {
				i = 0;
			}

			if (i == fence) return false;
			result = (E) array[i];
			i += dir;
			return true;
		}
	}

	@Override
	public int size() {return size;}
	public int head() {return head;}
	public int tail() {return tail;}
	public int remaining() {return maxCap - size;}

	// region *** Collection ***
	@Override
	public final boolean contains(Object o) {return indexOf(o) != -1;}
	@Deprecated
	public final boolean add(E e) {addLast(e);return true;}
	@Override
	public final boolean remove(Object o) {return removeFirstOccurrence(o);}
	@Override
	public boolean retainAll(@NotNull Collection<?> c) {throw new UnsupportedOperationException();}

	@Override
	public void clear() {
		head = tail = size = 0;
		Arrays.fill(array, null);
	}
	// endregion
	public final int indexOf(Object o) {
		Object[] arr = array;
		int i = head;
		while (i != tail) {
			if (o == null ? arr[i] == null : o.equals(arr[i])) return i;
			if (--i < 0) i = arr.length - 1;
		}
		return -1;
	}
	public final int lastIndexOf(Object o) {
		Object[] arr = array;
		int i = tail;
		while (i != head) {
			if (o == null ? arr[i] == null : o.equals(arr[i])) return i;
			if (++i == arr.length) i = 0;
		}
		return -1;
	}
	// region *** Deque ***
	public final void addFirst(E e) {
		if (size >= maxCap) throw new IllegalStateException("RingBuffer is full");
		ringAddFirst(e);
	}
	public final void addLast(E e) {
		if (size >= maxCap) throw new IllegalStateException("RingBuffer is full");
		ringAddLast(e);
	}
	public final boolean offerFirst(E e) {
		if (size < maxCap) {
			ringAddFirst(e);
			return true;
		}
		return false;
	}
	public final boolean offerLast(E e) {
		if (size < maxCap) {
			ringAddLast(e);
			return true;
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	@Override
	public final E removeFirst() {
		if (size == 0) throw new NoSuchElementException();

		int h = head;

		E val = (E) array[h];
		array[h] = null;

		if (--size == 0) head = tail = 0;
		else head = ++h == array.length ? 0 : h;

		return val;
	}
	@SuppressWarnings("unchecked")
	public final E removeLast() {
		if (size == 0) throw new NoSuchElementException();

		int t = tail;
		if (t == 0) t = array.length-1;
		else t--;

		E val = (E) array[t];
		array[t] = null;

		if (--size == 0) head = tail = 0;
		else tail = t;

		return val;
	}

	@Override
	public final E pollFirst() {return size == 0 ? null : removeFirst();}
	@Override
	public final E pollLast() {return size == 0 ? null : removeLast();}

	@Override
	@SuppressWarnings("unchecked")
	public final E getFirst() {
		if (size == 0) throw new NoSuchElementException();
		return (E) array[head];
	}
	@Override
	@SuppressWarnings("unchecked")
	public final E getLast() {
		if (size == 0) throw new NoSuchElementException();
		return (E) array[tail];
	}

	@Override
	@SuppressWarnings("unchecked")
	public final E peekFirst() {return size == 0 ? null : (E) array[head];}
	@Override
	@SuppressWarnings("unchecked")
	public final E peekLast() {return size == 0 ? null : (E) array[tail];}

	@Override
	public final boolean removeFirstOccurrence(Object o) {
		int i = indexOf(o);
		if (i < 0) return false;
		remove(i);
		return true;
	}
	@Override
	public final boolean removeLastOccurrence(Object o) {
		int i = lastIndexOf(o);
		if (i < 0) return false;
		remove(i);
		return true;
	}
	// endregion
	/**
	 * 删除数组下标i的元素，并保证缓冲区依然可用
	 */
	@SuppressWarnings("unchecked")
	public E remove(int i) {
		checkArrayBound(i);
		Object[] array = this.array;
		E e = (E) array[i];
		int t = tail;

		if (i >= t) {
			// [head, array.length]
			if (head < array.length - 1) System.arraycopy(array, head, array, head + 1, i - head);
			array[head] = null;
			head = head == array.length - 1 ? 0 : head + 1;
		} else {
			// [0, tail)
			if (i > 0 && t - i - 1 > 0) System.arraycopy(array, i + 1, array, i, t - i - 1);
			array[tail = t - 1] = null;
		}

		size--;
		return e;
	}

	private void checkArrayBound(int i) {
		if (size == array.length) return;
		if (head > tail) {
			if (i >= tail && i < head) throw new ArrayIndexOutOfBoundsException(i);
		} else if (i < head || (i >= tail)) throw new ArrayIndexOutOfBoundsException(i);
	}
	// region *** Queue ***
	public final boolean offer(E e) {return offerLast(e);}
	public final E remove() {return removeFirst();}
	public final E poll() {return size == 0 ? null : removeFirst();}
	public final E element() {return getFirst();}
	public final E peek() {return peekFirst();}
	// endregion
	// region *** Stack ***
	@Deprecated
	public void push(E e) {addLast(e);}
	@Deprecated
	public E pop() {return removeLast();}
	// endregion
	@SuppressWarnings("unchecked")
	public E ringAddFirst(E e) {
		int s = size;
		if (s < maxCap) {
			if (s == array.length) ensureCapacity();
			size = s+1;
		}

		int h = head;
		int nextH = h == 0 ? array.length-1 : h-1;
		if (tail == h && size == maxCap) tail = nextH;

		E v = (E) array[nextH];
		array[nextH] = e;

		head = nextH;
		return v;
	}

	@SuppressWarnings("unchecked")
	public E ringAddLast(E e) {
		int s = size;
		if (s < maxCap) {
			if (s == array.length) ensureCapacity();
			size = s+1;
		}

		int t = tail;
		int nextT = t == array.length-1 ? 0 : t+1;
		if (head == t && size == maxCap) head = nextT;

		E v = (E) array[t];
		array[t] = e;

		tail = nextT;
		return v;
	}

	@SuppressWarnings("unchecked")
	public void getSome(int dir, int i, int fence, List<E> dst) {
		if (size == 0) return;
		Object[] arr = array;
		do {
			dst.add((E) arr[i]);
			i += dir;

			if (i == arr.length) i = 0;
			else if (i < 0) i = arr.length - 1;
		} while (i != fence);
	}

	@SuppressWarnings("unchecked")
	public void getSome(int dir, int i, int fence, List<E> dst, int off, int len) {
		if (size == 0) return;
		Object[] arr = array;
		do {
			if (off != 0) off--;
			else if (len-- > 0) dst.add((E) arr[i]);
			i += dir;

			if (i == arr.length) i = 0;
			else if (i < 0) i = arr.length - 1;
		} while (i != fence);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder().append("RingBuffer{\n  size=").append(size).append(",length=").append(array.length);

		ArrayList<Object> data = new ArrayList<>();

		for (int i = 0; i < array.length; i++) data.add(i);
		data.add(IntMap.UNDEFINED);

		for (int i = 0; i < head; i++) data.add(" ");
		data.add("H");
		data.add(IntMap.UNDEFINED);

		for (int i = 0; i < tail; i++) data.add(" ");
		data.add("T");
		data.add(IntMap.UNDEFINED);

		data.addAll(array);
		TextUtil.prettyTable(sb, "  ", data.toArray(), " ", " ");
		return sb.append("\n}").toString();
	}
}