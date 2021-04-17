package roj.concurrent;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2021/7/4 1:54
 */
public class SimpleSpinLock {
    AtomicInteger lock = new AtomicInteger();

    public int enqueueReadLock() {
        if (lock.get() < 0) {
            Thread.yield();
            while (lock.get() < 0)
                LockSupport.parkNanos(10);
        }
        return lock.getAndIncrement();
    }

    public void releaseReadLock() {
        if(lock.get() <= 0)
            throw new IllegalStateException("Not locked: " + lock.get());
        lock.getAndDecrement();
    }

    public void enqueueWriteLock() {
        waitCas(lock, 0, -1);
    }

    public void enqueueWriteLock(int lockId) {
        waitCas(lock, 0, lockId);
    }

    public void releaseWriteLock() {
        if(!lock.compareAndSet(-1, 0))
            throw new IllegalStateException("Not locked: " + lock.get());
    }

    public void releaseWriteLock(int lockId) {
        if(!lock.compareAndSet(lockId, 0))
            throw new IllegalStateException("Not locked: " + lock.get());
    }

    private static void waitCas(AtomicInteger lock, int from, int to) {
        while (!lock.compareAndSet(from, to)) {
            Thread.yield();
            if (lock.compareAndSet(from, to)) break;
            LockSupport.parkNanos(10);
        }
    }

    public void release() {
        lock.set(0);
    }
}
