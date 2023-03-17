package roj.concurrent;

import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * @author Roj234
 * @since 2023/5/16 0016 4:26
 */
public final class SegmentReadWriteLock extends AbstractQueuedSynchronizer {
	private volatile boolean writeLockPending;


	public void lock() { acquire(0); }
	public boolean tryLock() { return tryAcquire(0); }
	public void unlock() { release(0); }
	public void lock(int slot) { acquireShared(slot); }
	public boolean tryLock(int slot) { return _tryLockShared(slot, 1) > 0; }
	public void unlock(int slot) { releaseShared(slot); }

	// write lock
	protected final boolean tryAcquire(int acquires) {
		Thread current = Thread.currentThread();

		if (!compareAndSetState(0, -1)) {
			if (getExclusiveOwnerThread() == current) throw new IllegalMonitorStateException("已锁定为写入模式");
			writeLockPending = true;
			return false;
		}

		writeLockPending = false;
		setExclusiveOwnerThread(current);
		return true;
	}

	// write unlock
	protected final boolean tryRelease(int releases) {
		if (!isHeldExclusively()) throw new IllegalMonitorStateException("未锁定为写入模式");
		setExclusiveOwnerThread(null);
		setState(0);
		return true;
	}

	// read lock
	protected final int tryAcquireShared(int slot) {
		return _tryLockShared(slot, AQSHelper.INSTANCE.apparentlyFirstQueuedIsExclusive(this) ? 1 : 127);
	}

	private int _tryLockShared(int slot, int limit) {
		Thread owner = getExclusiveOwnerThread();
		// write locked
		if (Thread.currentThread() == owner) throw new IllegalMonitorStateException("已锁定为写入模式");

		// writing | writing-wait
		if (writeLockPending || owner != null) return -1;

		slot = (1 << (slot & 31));

		while (limit > 0) {
			int c = getState();
			if ((c & slot) != 0) {
				limit -= 10;
			} else {
				if (compareAndSetState(c, c | slot)) return 1;
				limit--;
			}
		}

		return -1;
	}

	// read unlock
	protected final boolean tryReleaseShared(int slot) {
		if (getOwner() != null) throw new IllegalMonitorStateException("已锁定为写入模式");

		slot = ~(1 << (slot & 31));
		for (; ; ) {
			int c = getState();
			int nextc = c&slot;
			if (c == nextc) throw new IllegalMonitorStateException();
			if (compareAndSetState(c, nextc)) return true;
		}
	}

	protected final boolean isHeldExclusively() {
		// While we must in general read state before owner,
		// we don't need to do so to check if current thread is owner
		return getExclusiveOwnerThread() == Thread.currentThread();
	}

	public final ConditionObject newCondition() {
		return new ConditionObject();
	}

	final Thread getOwner() {
		// Must read state before owner to ensure memory consistency
		return (getState() != -1) ? null : getExclusiveOwnerThread();
	}
}
