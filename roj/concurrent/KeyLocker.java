package roj.concurrent;

import roj.concurrent.task.ITask;
import roj.math.MathUtils;
import sun.misc.Unsafe;

import java.util.concurrent.locks.Lock;

import static roj.reflect.ReflectionUtils.u;

/**
 * @author Roj234
 * @since 2023/11/7 0007 23:51
 */
public class KeyLocker {
	private final SegmentReadWriteLock[] lockMap;
	private final int mask;

	public KeyLocker(int subLockCount) {
		// >>> 2 是因为 SegmentReadWriteLock 还可以1拆32 但是悲观一点，并且少点CAS多点bitor
		this.mask = MathUtils.getMin2PowerOf(subLockCount >>> 2) - 1;
		this.lockMap = new SegmentReadWriteLock[mask+1];
	}

	public Lock getLock(Object key) {
		SegmentReadWriteLock lock;
		int mask = hashA(key)&this.mask;
		while (true) {
			lock = (SegmentReadWriteLock) u.getObjectVolatile(lockMap, Unsafe.ARRAY_OBJECT_BASE_OFFSET + mask * Unsafe.ARRAY_OBJECT_INDEX_SCALE);
			if (lock != null) break;

			if (u.compareAndSwapObject(lockMap, Unsafe.ARRAY_OBJECT_BASE_OFFSET + mask * Unsafe.ARRAY_OBJECT_INDEX_SCALE,
				null, lock = new SegmentReadWriteLock())) {
				break;
			}
		}

		return lock.asReadLock(hashB(key)&31);
	}

	public void executeLocked(Object key, ITask task) throws Exception {
		Lock lock = getLock(key);
		lock.lock();
		try {
			task.execute();
		} finally {
			lock.unlock();
		}
	}

	private static int hashA(Object key) {
		int hash = key.hashCode();
		return hash ^ (hash >>> 16) ^ (hash >>> 24);
	}
	private static int hashB(Object key) {
		int hash = key.hashCode();
		return (int) (hash * 2166136261L);
	}
}
