package roj.concurrent;

import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author Roj233
 * @since 2021/7/4 1:54
 */
@Deprecated
public class SpinLock {
	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

	public void readLock() { lock.readLock().lock(); }
	public void readUnlock() { lock.readLock().unlock(); }
	public void writeLock() { lock.writeLock().lock(); }
	public void writeUnlock() { lock.writeLock().unlock(); }
}
