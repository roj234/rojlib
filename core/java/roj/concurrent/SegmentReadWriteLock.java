package roj.concurrent;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * @author Roj234
 * @since 2023/5/16 4:26
 */
public final class SegmentReadWriteLock extends AbstractQueuedSynchronizer {
	private volatile boolean writeLockPending;

	public void lockAll() { acquire(0); }
	public boolean tryLockAll() { return tryAcquire(0); }
	public void unlockAll() { release(0); }

	public void lock(int slot) { acquireShared(slot); }
	public boolean tryLock(int slot) { return _tryLockShared(slot, 1) > 0; }
	public void unlock(int slot) { releaseShared(slot); }

	public Lock asWriteLock() { return new SubLock(-1); }
	public Lock asReadLock(int slot) { return new SubLock(slot); }

	final class SubLock implements Lock {
		private final int slot;

		SubLock(int slot) { this.slot = slot; }

		@Override
		public void lock() {
			if (slot < 0) acquire(0);
			else acquireShared(slot);
		}

		@Override
		public void lockInterruptibly() throws InterruptedException {
			if (slot < 0) acquireInterruptibly(0);
			else acquireSharedInterruptibly(slot);
		}

		@Override
		public boolean tryLock() {
			if (slot < 0) return tryAcquire(0);
			else return tryAcquireShared(slot) >= 0;
		}

		@Override
		public boolean tryLock(long time, @NotNull TimeUnit unit) throws InterruptedException {
			if (slot < 0) return tryAcquireNanos(0, unit.toNanos(time));
			else return tryAcquireSharedNanos(slot, unit.toNanos(time));
		}

		@Override
		public void unlock() {
			if (slot < 0) release(0);
			else releaseShared(slot);
		}

		@NotNull
		@Override
		public Condition newCondition() {
			return SegmentReadWriteLock.this.newCondition();
		}
	}

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

	public final ConditionObject newCondition() { return new ConditionObject(); }

	final Thread getOwner() {
		// Must read state before owner to ensure memory consistency
		return (getState() != -1) ? null : getExclusiveOwnerThread();
	}
}