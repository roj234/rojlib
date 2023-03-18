package roj.concurrent;

import roj.util.Helpers;

import java.util.concurrent.locks.LockSupport;
import java.util.function.Predicate;

import static roj.reflect.FieldAccessor.u;

/**
 * @author Roj234
 * @since 2023/5/17 0017 18:48
 */
public class ManyPutOneGet<T> {
	public static class Entry<T> {
		volatile Entry<T> next;

		protected T ref;
	}

	static {
		try {
			SIZE_OFF = u.objectFieldOffset(ManyPutOneGet.class.getDeclaredField("size"));
			TAIL_OFF = u.objectFieldOffset(ManyPutOneGet.class.getDeclaredField("tail"));
		} catch (NoSuchFieldException ignored) {}
	}
	private static long SIZE_OFF, TAIL_OFF;

	volatile Entry<T> head, tail;
	volatile int size;

	private final int max;

	public ManyPutOneGet(int max) {
		this.head = this.tail = createEntry();
		this.max = max;
	}

	protected Entry<T> createEntry() { return new Entry<>(); }

	protected final boolean offer(Entry<T> entry, boolean waitOnExceed) {
		int i = 0;
		while (true) {
			Entry<T> tail = this.tail;
			if (size < max && u.compareAndSwapObject(this, TAIL_OFF, tail, entry)) {
				while (true) {
					int s = size;
					if (u.compareAndSwapInt(this, SIZE_OFF, s, s+1)) break;
				}

				tail.next = entry;
				break;
			}
			if ((++i & 15) == 0) {
				if (!waitOnExceed && size >= max) return false;
				LockSupport.parkNanos(1);
			}
		}

		return true;
	}

	protected final Entry<T> poll(Predicate<Entry<T>> doRemove) {
		Entry<T> prevEntry = head;
		Entry<T> entry = prevEntry.next;

		if (entry != null) {
			if (!doRemove.test(entry)) return null;

			prevEntry = entry;
			prevEntry.next = null;
		}

		while (true) {
			int s = size;
			if (u.compareAndSwapInt(this, SIZE_OFF, s, s-1)) break;
		}

		this.head = prevEntry;
		return entry;
	}

	public boolean isEmpty() { return size == 0; }
	public int size() { return size; }
	public int remaining() { return max - size; }

	public boolean _addLast(T t) {
		Entry<T> entry = cachedEntry();
		if (entry == null) entry = createEntry();

		entry.ref = t;
		boolean success = offer(entry, false);

		if (!success) cacheEntry(entry);
		return success;
	}
	public T _removeFirst() {
		Entry<T> entry = poll(Helpers.alwaysTrue());
		if (entry == null) return null;
		T t = entry.ref;
		cacheEntry(entry);
		return t;
	}

	public void clear() {
		while (poll(Helpers.alwaysTrue()) != null);
	}

	protected Entry<T> cachedEntry() {
		return null;
	}
	protected void cacheEntry(Entry<T> entry) {

	}
}
