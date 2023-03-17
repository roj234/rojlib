package roj.concurrent;

import roj.util.ArrayUtil;

import java.util.Arrays;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

import static roj.reflect.FieldAccessor.u;

/**
 * 环形缓冲区多线程版
 *
 * @author Roj234
 * @since 2023/3/04 20:53
 */
public class CASRingBuffer<E> {
	static final class NoReentrantBucketLock extends AbstractQueuedSynchronizer {
		// write lock
		protected final boolean tryAcquire(int acquires) {
			Thread current = Thread.currentThread();

			if (!compareAndSetState(0, -1)) {
				if (getExclusiveOwnerThread() == Thread.currentThread())
					throw new IllegalMonitorStateException("locked in write mode");
				return false;
			}

			setExclusiveOwnerThread(current);
			return true;
		}
		// write unlock
		protected final boolean tryRelease(int releases) {
			if (Thread.currentThread() != getExclusiveOwnerThread())
				throw new IllegalMonitorStateException();
			setExclusiveOwnerThread(null);
			setState(0);
			Thread current = Thread.currentThread();
			return true;
		}

		// read lock
		protected final int tryAcquireShared(int slot) {
			slot = (1 << (slot&31));

			int limit = AQSHelper.INSTANCE.apparentlyFirstQueuedIsExclusive(this) ? 1 : 127;
			while (limit > 0) {
				int c = getState();
				if ((c & slot) != 0) continue;

				if (compareAndSetState(c, c|slot)) return 1;

				limit--;
			}

			return -1;
		}

		// read unlock
		protected final boolean tryReleaseShared(int slot) {
			if (getState() == -1 && getExclusiveOwnerThread() != null)
				throw new IllegalMonitorStateException("locked in write mode");

			slot = (1 << (slot&31));
			for (;;) {
				int c = getState();
				if ((c & slot) == 0) throw new IllegalMonitorStateException();
				int nextc = c & ~slot;
				if (compareAndSetState(c, nextc)) return true;
			}
		}

		protected final boolean isHeldExclusively() {
			// While we must in general read state before owner,
			// we don't need to do so to check if current thread is owner
			return getExclusiveOwnerThread() == Thread.currentThread();
		}

		final ConditionObject newCondition() {
			return new ConditionObject();
		}

		final Thread getOwner() {
			// Must read state before owner to ensure memory consistency
			return (getState() != -1) ? null : getExclusiveOwnerThread();
		}
	}

	protected int maxCap;
	protected Object[] array;

	protected volatile int head, tail;
	static final long HEAD_OFF,TAIL_OFF;
	static {
		try {
			HEAD_OFF = u.objectFieldOffset(CASRingBuffer.class.getDeclaredField("head"));
			TAIL_OFF = u.objectFieldOffset(CASRingBuffer.class.getDeclaredField("tail"));
		} catch (NoSuchFieldException e) {
			throw new RuntimeException(e);
		}
	}

	volatile boolean isEmpty;

	// 分散一点而已，同步无所谓
	private volatile int randomLock;
	int randomLock() {
		int r = randomLock;
		randomLock = (r+1) & 31;

		for (int i = r; i < r+31; i++) {
			if (lock.tryAcquireShared(i) > 0) {
				return i;
			}
		}
		lock.acquireShared(r+=31);
		return r;
	}

	final NoReentrantBucketLock lock = new NoReentrantBucketLock();

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder().append("RingBuffer{\n  ");
		sb.append("size=").append("<unknown>").append('\n');
		for (int i = 0; i < array.length; i++) {
			sb.append(i).append(' ');
		}
		sb.append("\n  ");
		for (int i = 0; i < head; i++) {
			sb.append("  ");
		}
		sb.append("S\n  ");
		for (int i = 0; i < tail; i++) {
			sb.append("  ");
		}
		return sb.append("E\n  ").append(ArrayUtil.toString(array, 0, array.length)).toString();
	}

	public CASRingBuffer(int capacity) {
		array = new Object[capacity];
	}
	public CASRingBuffer(int capacity, int max) {
		array = new Object[capacity];
		maxCap = max;
	}

	public boolean isEmpty() {
		return isEmpty;
	}

	public boolean isFull() {
		return head == tail && !isEmpty;
	}

	final int readLock(long off) {
		int v;
		while (true) {
			v = u.getIntVolatile(this, off);

			lock.acquireShared(v);

			int v1 = v;
			if (off == HEAD_OFF) {
				if (++v1 == array.length) v1 = 0;
			} else {
				v1 = v == 0 ? array.length-1 : v-1;
			}

			if (u.compareAndSwapInt(this, off, v, v1)) break;

			lock.releaseShared(v);
		}
		return v;
	}

	public E removeFirst() {
		E v;

		int head = readLock(HEAD_OFF);
		Object[] array = this.array;

		try {
			v = remove(array, head);
			if (v == null) isEmpty = true;
		} finally {
			lock.releaseShared(head);
		}

		return v;
	}

	public E removeLast() {
		if (isEmpty) return null;

		E v;

		int tail = readLock(TAIL_OFF);
		Object[] array = this.array;
		try {
			int e = tail-1;
			if (e < 0) e = array.length-1;

			v = remove(array, e);
			if (v == null) isEmpty = true;
		} finally {
			lock.releaseShared(tail);
		}

		return v;
	}

	public E peekFirst() {
		int r = randomLock();
		E v = peek(array, head);
		lock.releaseShared(r);
		return v;
	}

	public E peekLast() {
		int r = randomLock();
		E v = peek(array, tail);
		lock.releaseShared(r);
		return v;
	}

	final int writeLock(long off) {
		int v;

		while (true) {
			v = u.getIntVolatile(this, off);

			lock.acquireShared(v);

			int v1 = v;
			if (off == TAIL_OFF) {
				if (++v1 == array.length) v1 = 0;

				if (head == v && expand()) continue;
				u.compareAndSwapInt(this, HEAD_OFF, v, v1);
			} else {
				v1 = v == 0 ? array.length-1 : v-1;

				if (tail == v && expand()) continue;
				u.compareAndSwapInt(this, TAIL_OFF, v, v1);
			}

			if (u.compareAndSwapInt(this, off, v, v1)) break;

			lock.releaseShared(v);
		}
		return v;
	}

	private boolean expand() {
		if (array.length < maxCap) {
			lock.acquire(0);
			boolean success = false;
			if (array.length < maxCap) {
				success = true;

				Object[] arr1 = new Object[Math.min(maxCap, array.length+10)];
				int j = 0;
				boolean 我真的不会CAS啊 = true;

				int i = head;
				int fence = tail;
				Object[] arr = array;
				while (true) {
					arr1[j++] = arr[i++];

					if (i == arr.length) i = 0;
					if (i == fence) {
						if (!我真的不会CAS啊) break;
						我真的不会CAS啊 = false;
					}
				}

				array = arr1;
				head = 0;
				tail = j;
			}
			lock.release(0);
			return success;
		}
		return false;
	}

	public E ringAddFirst(E e) {
		if (e == null) throw new NullPointerException();

		E v;

		int head = writeLock(HEAD_OFF);
		Object[] array = this.array;

		try {
			v = insert(array, head, e);
			isEmpty = false;
		} finally {
			lock.releaseShared(head);
		}

		return v;
	}

	public E ringAddLast(E e) {
		if (e == null) throw new NullPointerException();

		E v;

		int tail = writeLock(TAIL_OFF);
		Object[] array = this.array;

		try {
			v = insert(array, tail, e);
			isEmpty = false;
		} finally {
			lock.releaseShared(tail);
		}

		return v;
	}

	public void clear() {
		lock.acquire(0);

		head = tail = 0;
		Arrays.fill(array, null);
		isEmpty = true;

		lock.release(0);
	}

	@SuppressWarnings("unchecked")
	protected E insert(Object[] array, int i, E e) {
		E v = (E) array[i];
		array[i] = e;
		return v;
	}

	@SuppressWarnings("unchecked")
	protected E remove(Object[] array, int i) {
		E v = (E) array[i];
		array[i] = null;
		return v;
	}

	@SuppressWarnings("unchecked")
	protected E peek(Object[] array, int i) {
		return (E) array[i];
	}
}
