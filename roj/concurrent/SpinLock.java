package roj.concurrent;

import java.util.concurrent.locks.LockSupport;

import static roj.reflect.FieldAccessor.u;

/**
 * @author Roj233
 * @since 2021/7/4 1:54
 */
public class SpinLock {
	volatile int lock;
	volatile Thread owner;

	private static final long lockOff;
	static {
		try {
			lockOff = u.objectFieldOffset(SpinLock.class.getDeclaredField("lock"));
		} catch (NoSuchFieldException e) {
			throw new RuntimeException(e);
		}
	}

	public void readLock() {
		int time = 64;
		while (true) {
			int val = lock;
			if (val >= 0) {
				if (u.compareAndSwapInt(this, lockOff, val, val+1)) break;
			}

			Thread.yield();

			if (time < 512) {
				for (int i = 0; i < 100; i++);
			} else {
				LockSupport.parkNanos(time);
			}

			if (time < 1000 << 10) time <<= 1;

			else System.err.println("SpinLock:37: " + lock + " in " + Thread.currentThread().getName());
		}
	}

	public void readUnlock() {
		int v;
		do {
			v = lock;
			if (v <= 0) throw new IllegalStateException("Not locked: " + v);
		} while (!u.compareAndSwapInt(this, lockOff, v, v-1));
	}

	public void writeLock() {
		int time = 64;
		while (true) {
			int val = lock;
			if (val == 0) {
				if (u.compareAndSwapInt(this, lockOff, 0, -1)) {
					owner = Thread.currentThread();
					break;
				}
			} else if (val < 0) {
				if (owner == Thread.currentThread()) {
					lock--;
					break;
				}
			}

			Thread.yield();

			if (time < 512) {
				for (int i = 0; i < 100; i++);
			} else {
				LockSupport.parkNanos(time);
			}

			if (time < 1000 << 10) time <<= 1;
		}
	}

	public void writeUnlock() {
		if (owner != Thread.currentThread()) throw new IllegalStateException("Not holder thread");

		int v = lock;
		if (v >= 0) throw new IllegalStateException("Not locked: " + v);
		if (v == -1) owner = null;

		lock++;
	}

	public boolean isLocked() {
		return lock != 0;
	}
}
