package roj.collect;

import org.jetbrains.annotations.NotNull;
import roj.math.MathUtils;
import roj.text.TextUtil;
import roj.util.ArrayCache;

import java.util.*;

/**
 * A simple ring buffer
 *
 * @author Roj234
 * @since 2021/4/13 23:25
 */
public class RingBuffer<E> extends AbstractCollection<E> implements Deque<E> {
	public final class Itr extends AbstractIterator<E> {
		int i;
		int dir;
		int fence;

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

		public int getPos() {
			return i;
		}

		@Override
		public String toString() {
			return "Itr{" + i + "+" + dir + " => " + fence + '}';
		}
	}

	protected int maxCap;
	protected Object[] array;

	protected int head, tail, size;

	public RingBuffer(int capacity) {
		this(capacity, true);
	}

	public RingBuffer(int capacity, boolean allocateNow) {
		maxCap = capacity;
		if (capacity < 0) capacity = 10;
		else if (!allocateNow) capacity = Math.min(10, capacity);
		array = new Object[capacity];
	}

	public RingBuffer(int capacity, int maxCapacity) {
		maxCap = maxCapacity;
		array = capacity == 0 ? ArrayCache.OBJECTS : new Object[capacity];
	}

	public static <T> RingBuffer<T> infCap() {
		return new RingBuffer<>(-1, false);
	}

	public void setCapacity(int capacity) {
		if (array.length > capacity && capacity > 0) resize(capacity);
		maxCap = capacity;
	}

	public void ensureCapacity(int capacity) {
		if (capacity > array.length) {
			if (array.length == 0) capacity = 8;
			else capacity = MathUtils.getMin2PowerOf(capacity);

			if (maxCap > 0 && capacity > maxCap) capacity = maxCap;

			if (size > 0) {
				// in loop mode
				resize(capacity);
			} else {
				array = new Object[capacity];
			}
		}
	}

	protected void resize(int capacity) {
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

	public int capacity() {
		return maxCap;
	}

	public int head() {
		return head;
	}

	public int tail() {
		return tail;
	}

	public Object[] getArray() {
		return array;
	}

	@Override
	public int size() {
		return size;
	}

	public int remaining() {
		return maxCap - size;
	}

	@Override
	public boolean isEmpty() {
		return size == 0;
	}

	@Override
	public boolean contains(Object o) {
		return indexOf(o) != -1;
	}

	@NotNull
	@Override
	public Iterator<E> iterator() {
		return size == 0 ? Collections.emptyIterator() : new Itr(false);
	}

	@NotNull
	@Override
	public Iterator<E> descendingIterator() {
		return size == 0 ? Collections.emptyIterator() : new Itr(true);
	}

	public Itr listItr(boolean reverse) {
		return new Itr(reverse);
	}

	@Override
	public E pollFirst() {
		return size == 0 ? null : removeFirst();
	}
	@Override
	public E pollLast() {
		return size == 0 ? null : removeLast();
	}

	@SuppressWarnings("unchecked")
	@Override
	public E removeFirst() {
		if (size == 0) throw new NoSuchElementException();

		int h = head;

		E val = (E) array[h];
		array[h] = null;

		if (--size == 0) head = tail = 0;
		else head = ++h == array.length ? 0 : h;

		return val;
	}
	@SuppressWarnings("unchecked")
	public E removeLast() {
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
	@SuppressWarnings("unchecked")
	public E getFirst() {
		if (size == 0) throw new NoSuchElementException();
		return (E) array[head];
	}
	@Override
	@SuppressWarnings("unchecked")
	public E getLast() {
		if (size == 0) throw new NoSuchElementException();
		return (E) array[tail];
	}

	@Override
	@SuppressWarnings("unchecked")
	public E peekFirst() {
		return size == 0 ? null : (E) array[head];
	}
	@Override
	@SuppressWarnings("unchecked")
	public E peekLast() {
		return size == 0 ? null : (E) array[tail];
	}

	// region *** Deque incompatible methods ***
	@Deprecated
	public void addFirst(E e) {
		ringAddFirst(e);
	}
	@Deprecated
	public void addLast(E e) {
		ringAddLast(e);
	}
	@Deprecated
	public boolean offerFirst(E e) {
		ringAddFirst(e);
		return true;
	}
	@Deprecated
	public boolean offerLast(E e) {
		ringAddLast(e);
		return true;
	}
	// endregion
	// region *** Queue methods ***
	@Deprecated
	public boolean add(E e) {
		ringAddLast(e);
		return true;
	}
	@Deprecated
	public boolean offer(E e) {
		ringAddLast(e);
		return true;
	}
	@Deprecated
	public E remove() {
		return removeFirst();
	}
	@Deprecated
	public E poll() {
		return size == 0 ? null : removeFirst();
	}
	@Deprecated
	public E element() {
		return getFirst();
	}
	@Deprecated
	public E peek() {
		return peekFirst();
	}
	// endregion
	// region *** Stack methods ***
	@Deprecated
	public void push(E e) {
		ringAddLast(e);
	}
	@Deprecated
	public E pop() {
		return removeLast();
	}
	// endregion
	// region *** Collection methods ***
	@Override
	public boolean remove(Object o) {
		return removeFirstOccurrence(o);
	}
	@Override
	public boolean retainAll(@NotNull Collection<?> c) {
		throw new UnsupportedOperationException();
	}
	// endregion

	@SuppressWarnings("unchecked")
	public E ringAddFirst(E e) {
		int s = size;
		if (s < maxCap) {
			if (s == array.length) ensureCapacity(array.length + 10);
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
			if (s == array.length) ensureCapacity(array.length + 10);
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

	@Override
	public void clear() {
		head = tail = size = 0;
		Arrays.fill(array, null);
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

	@SuppressWarnings("unchecked")
	public E getArray(int i) {
		checkArrayBound(i);
		return (E) array[i];
	}

	@SuppressWarnings("unchecked")
	public E get(int i) {
		if (i < 0 || i >= size) throw new ArrayIndexOutOfBoundsException(i);

		i = head - i;
		return (E) (i < 0 ? array[i + array.length] : array[i]);
	}

	@SuppressWarnings("unchecked")
	public E set(int i, E val) {
		checkArrayBound(i);
		E orig = (E) array[i];
		array[i] = val;
		return orig;
	}

	@Override
	public boolean removeFirstOccurrence(Object o) {
		int i = indexOf(o);
		if (i < 0) return false;
		remove(i);
		return true;
	}

	public int indexOf(Object o) {
		Object[] arr = array;
		int i = head;
		while (i != tail) {
			if (o == null ? arr[i] == null : o.equals(arr[i])) return i;
			if (--i < 0) i = arr.length - 1;
		}
		return -1;
	}

	@Override
	public boolean removeLastOccurrence(Object o) {
		int i = lastIndexOf(o);
		if (i < 0) return false;
		remove(i);
		return true;
	}

	public int lastIndexOf(Object o) {
		Object[] arr = array;
		int i = tail;
		while (i != head) {
			if (o == null ? arr[i] == null : o.equals(arr[i])) return i;
			if (++i == arr.length) i = 0;
		}
		return -1;
	}

	@SuppressWarnings("unchecked")
	public E remove(int i) {
		checkArrayBound(i);
		Object[] array = this.array;
		E e = (E) array[i];
		int t = tail;

		if (i >= t) {
			// [head, array.length]
			if (head < array.length-1) System.arraycopy(array, head, array, head+1, i-head);
			array[head] = null;
			head = head == array.length-1 ? 0 : head+1;
		} else {
			// [0, tail)
			if (i > 0 && t-i-1 > 0) System.arraycopy(array, i+1, array, i, t-i-1);
			array[tail = t-1] = null;
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

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder().append("RingBuffer{\n  size=").append(size).append(",length=").append(array.length);

		SimpleList<Object> data = new SimpleList<>();

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