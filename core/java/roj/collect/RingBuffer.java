package roj.collect;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import roj.math.MathUtils;
import roj.text.TextUtil;
import roj.util.ArrayCache;

import java.util.*;
import java.util.function.Consumer;

/**
 * 简单的环形缓冲区实现.
 * 还可以作为一个有界的{@link ArrayDeque}使用.
 * This class allow {@code null} elements, but not encouraged to use.
 * This class is likely to be faster than Stack when used as a stack, and faster than LinkedList when used as a queue.
 *
 * @author Roj234
 * @since 2021/4/13 23:25
 */
public class RingBuffer<E> extends AbstractCollection<E> implements Deque<E> {
	protected int maxCapacity;
	protected Object[] elements;

	protected int head, tail, size;

	public static <T> RingBuffer<T> bounded(int capacity) {return new RingBuffer<>(capacity);}
	public static <T> RingBuffer<T> unbounded() {return new RingBuffer<>(0, ArrayCache.MAX_ARRAY_SIZE);}
	public static <T> RingBuffer<T> unbounded(int initialCapacity) {return new RingBuffer<>(initialCapacity, ArrayCache.MAX_ARRAY_SIZE);}
	public static <T> RingBuffer<T> lazy(int maxCapacity) {return new RingBuffer<>(0, maxCapacity);}

	public RingBuffer(int capacity) {
		if (capacity < 0) throw new IllegalArgumentException("Capacity must >= 0");
		if (capacity == 0) {
			/**
			 * Why can be zero
			 * @see roj.config.mapper.ObjectMapperFactory#containerFactory(Class)
			 */
			maxCapacity = ArrayCache.MAX_ARRAY_SIZE;
			elements = ArrayCache.OBJECTS;
		} else {
			maxCapacity = capacity;
			elements = new Object[capacity];
		}
	}
	public RingBuffer(int capacity, int maxCapacity) {
		if (maxCapacity <= 0) throw new IllegalArgumentException("MaxCapacity must > 0");
		this.maxCapacity = maxCapacity;
		elements = capacity == 0 ? ArrayCache.OBJECTS : new Object[capacity];
	}

	public int maxCapacity() {return maxCapacity;}
	public void setMaxCapacity(int capacity) {
		if (capacity < 0) throw new IllegalArgumentException("Capacity must >= 0");
		if (capacity == 0) capacity = ArrayCache.MAX_ARRAY_SIZE;
		maxCapacity = capacity;
		if (elements.length > capacity) {
			if (size > 0) setCapacity(capacity);
			else elements = new Object[capacity];
		}
	}

	public int capacity() {return elements.length;}
	private void setCapacity(int capacity) {
		Object[] newArray = new Object[capacity];
		int oldLen = elements.length;
		int toCopy = Math.min(size, capacity);
		int frontLen = Math.min(toCopy, oldLen - head);
		System.arraycopy(elements, head, newArray, 0, frontLen);
		int backLen = toCopy - frontLen;
		System.arraycopy(elements, 0, newArray, frontLen, backLen);

		elements = newArray;
		head = 0;
		tail = toCopy;
		size = Math.min(toCopy, size);
	}

	private void grow() {
		int capacity = elements.length;
		if (capacity > 64) capacity += MathUtils.nextPowerOfTwo(capacity) >> 1;
		else capacity += capacity + 2;

		if (capacity < 0 || capacity > maxCapacity) capacity = maxCapacity;
		setCapacity(capacity);
	}

	@Override
	public int size() {return size;}
	public int head() {return head;}
	public int tail() {return tail;}
	public int remaining() {return maxCapacity - size;}

	@SuppressWarnings("unchecked")
	@Contract(mutates = "this")
	public E ringAddFirst(E element) {
		int sz = size;
		if (sz < maxCapacity) {
			if (sz == elements.length) grow();
			size = ++sz;
		}

		int h = head;
		int nextH = h == 0 ? elements.length-1 : h-1;
		if (tail == h && sz == maxCapacity) tail = nextH;

		E prev = (E) elements[nextH];
		elements[nextH] = element;

		head = nextH;
		return prev;
	}

	@SuppressWarnings("unchecked")
	@Contract(mutates = "this")
	public E ringAddLast(E element) {
		int sz = size;
		if (sz < maxCapacity) {
			if (sz == elements.length) grow();
			size = sz+1;
		}

		int t = tail;
		int nextT = t == elements.length-1 ? 0 : t+1;
		if (head == t && size == maxCapacity) head = nextT;

		E prev = (E) elements[t];
		elements[t] = element;

		tail = nextT;
		return prev;
	}

	public final int indexOf(Object o) {
		if (size > 0) {
			Object[] arr = elements;
			int i = head;
			do {
				if (o == null ? arr[i] == null : o.equals(arr[i])) return i;
				if (++i == arr.length) i = 0;
			} while (i != tail);
		}
		return -1;
	}
	public final int lastIndexOf(Object o) {
		if (size > 0) {
			Object[] arr = elements;
			int i = tail;
			do {
				if (o == null ? arr[i] == null : o.equals(arr[i])) return i;
				if (--i < 0) i = arr.length - 1;
			} while (i != head);
		}
		return -1;
	}

	/**
	 * 删除下标为{@code index}的元素，并返回移动的是否为tail指针.
	 * @see Itr#remove()
	 * @param index 要删除的元素的下标
	 * @return 移动的是否为tail指针
	 */
	@Contract(mutates = "this")
	protected boolean delete(int index) {
		Object[] elements = this.elements;
		int h = head, t = tail;

		deleteFromEnd: {
			if (h < t) {
				// [head, tail)
				if (index < h || index >= t) throw new ArrayIndexOutOfBoundsException(index);
				// 如果从后删除更经济
				if (index - h >= t - index) break deleteFromEnd;
			} else {
				// [head, elements.length) ∪ [0, tail)
				if (index < h && index >= t) throw new ArrayIndexOutOfBoundsException(index);
				if (index < t) break deleteFromEnd;
			}

			size--;
			if (h < elements.length - 1) {
				System.arraycopy(elements, h, elements, h + 1, index - h);
				head = h + 1;
			} else {
				head = 0;
			}
			elements[h] = null;
			return false;
		}

		size--;
		t--;
		System.arraycopy(elements, index + 1, elements, index, t - index);
		tail = t;
		elements[t] = null;
		return true;
	}

	// region *** Collection ***
	@Override public final boolean contains(Object o) {return indexOf(o) != -1;}
	@Override public final boolean add(E e) {if (size >= maxCapacity) return false;addLast(e);return true;}
	@Override public final boolean remove(Object o) {return removeFirstOccurrence(o);}

	@Override
	public void clear() {
		head = tail = size = 0;
		Arrays.fill(elements, null);
	}
	// endregion
	// region *** Deque ***
	public final void addFirst(E e) {
		if (size >= maxCapacity) throw new IllegalStateException("RingBuffer is full");
		ringAddFirst(e);
	}
	public final void addLast(E e) {
		if (size >= maxCapacity) throw new IllegalStateException("RingBuffer is full");
		ringAddLast(e);
	}
	public final boolean offerFirst(E e) {
		if (size < maxCapacity) {
			ringAddFirst(e);
			return true;
		}
		return false;
	}
	public final boolean offerLast(E e) {
		if (size < maxCapacity) {
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

		E val = (E) elements[h];
		elements[h] = null;

		if (--size == 0) head = tail = 0;
		else head = ++h == elements.length ? 0 : h;

		return val;
	}
	@SuppressWarnings("unchecked")
	public final E removeLast() {
		if (size == 0) throw new NoSuchElementException();

		int t = tail;
		if (t == 0) t = elements.length-1;
		else t--;

		E val = (E) elements[t];
		elements[t] = null;

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
		return (E) elements[head];
	}
	@Override
	@SuppressWarnings("unchecked")
	public final E getLast() {
		if (size == 0) throw new NoSuchElementException();
		return (E) elements[tail];
	}

	@Override
	@SuppressWarnings("unchecked")
	public final E peekFirst() {return size == 0 ? null : (E) elements[head];}
	@Override
	@SuppressWarnings("unchecked")
	public final E peekLast() {return size == 0 ? null : (E) elements[tail];}

	@Override
	public final boolean removeFirstOccurrence(Object o) {
		int i = indexOf(o);
		if (i < 0) return false;
		delete(i);
		return true;
	}
	@Override
	public final boolean removeLastOccurrence(Object o) {
		int i = lastIndexOf(o);
		if (i < 0) return false;
		delete(i);
		return true;
	}
	// endregion
	// region *** Queue ***
	public final boolean offer(E e) {return offerLast(e);}
	public final E remove() {return removeFirst();}
	public final E poll() {return size == 0 ? null : removeFirst();}
	public final E element() {return getFirst();}
	public final E peek() {return peekFirst();}
	// endregion
	// region *** Stack ***
	public void push(E e) {addLast(e);}
	public E pop() {return removeLast();}
	// endregion

	@NotNull
	@Override
	public Iterator<E> iterator() {return size == 0 ? Collections.emptyIterator() : new Itr(false);}
	@NotNull
	@Override
	public Iterator<E> descendingIterator() {return size == 0 ? Collections.emptyIterator() : new Itr(true);}
	private final class Itr implements Iterator<E> {
		byte direction;
		int cursor, remaining;
		int lastRet; // 尽管可以用cursor-direction来得到这个值，但是我同时使用它以避免多次remove

		public Itr(boolean descending) {
			remaining = size;
			if (descending) {
				cursor = head == tail ? tail : tail-1;
				direction = -1;
			} else {
				cursor = head;
				direction = 1;
			}
		}

		@Override public boolean hasNext() {return remaining > 0;}

		@Override
		@SuppressWarnings("unchecked")
		public E next() {
			if (remaining <= 0) throw new NoSuchElementException();
			remaining--;

			int cur = cursor;
			lastRet = cur; // 记录当前光标位置，用于remove
			E element = (E) elements[cur];

			advance(direction);
			return element;
		}

		@Override
		public void remove() {
			if (lastRet < 0) throw new IllegalStateException();

			boolean tailMoved = RingBuffer.this.delete(lastRet);
			if (tailMoved) {
				if (direction > 0 && lastRet != head) advance(-1);
			} else {
				if (direction < 0 && lastRet != tail) advance(1);
			}

			lastRet = -1;
		}

		private void advance(int direction) {
			int cur = cursor + direction;
			if (cur < 0) cur = elements.length - 1;
			else if (cur == elements.length) cur = 0;
			cursor = cur;
		}
	}

	@Override
	public final void forEach(Consumer<? super E> action) {forEach(action, false, 0, size);}
	@SuppressWarnings("unchecked")
	public void forEach(Consumer<? super E> action, boolean descending, int skip, int remaining) {
		byte direction;
		int cursor;

		if (descending) {
			cursor = tail;
			direction = -1;
		} else {
			cursor = head;
			direction = 1;
		}

		if (skip > 0) {
			cursor = MathUtils.positiveMod(cursor + direction * skip, elements.length);
		}

		while (remaining-- > 0) {
			E element = (E) elements[cursor];

			cursor += direction;
			if (cursor < 0) cursor = elements.length - 1;
			else if (cursor == elements.length) cursor = 0;

			action.accept(element);
		}
	}

	@Override
	public String toString() {
		var sb = new StringBuilder().append("RingBuffer{\n  size=").append(size).append('/').append(elements.length).append('/').append(maxCapacity);

		ArrayList<Object> data = new ArrayList<>();
		data.add("#");
		data.add("P");
		data.add("Element");
		data.add(IntMap.UNDEFINED);

		for (int i = 0; i < elements.length; i++) {
			data.add(i);
			if (i == head) {
				data.add(i == tail ? "*" : "H");
			} else if (i == tail) data.add("T");
			else data.add("");
			data.add(elements[i]);
			data.add(IntMap.UNDEFINED);
		}

		TextUtil.prettyTable(sb, "  ", data.toArray());
		return sb.append('}').toString();
	}
}