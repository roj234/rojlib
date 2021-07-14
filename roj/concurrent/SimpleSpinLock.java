package roj.concurrent;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2021/7/4 1:54
 */
public class SimpleSpinLock {
    AtomicInteger lock = new AtomicInteger();

    public int enqueueReadLock() throws InterruptedException {
        if (lock.get() < 0) {
            Thread.yield();
            while (lock.get() < 0)
                Thread.sleep(5);
        }
        return lock.getAndIncrement();
    }

    public int enqueueReadLockUI() {
        if (lock.get() < 0) {
            Thread.yield();
            while (lock.get() < 0) {
                try {
                    Thread.sleep(5);
                } catch (InterruptedException ignored) {}
            }
        }
        return lock.getAndIncrement();
    }

    public void releaseReadLock() {
        if(lock.get() < 0)
            throw new IllegalStateException("Not locked");
        lock.getAndDecrement();
    }

    public void enqueueWriteLock() throws InterruptedException {
        waitCas(lock, 0, -1);
    }

    public void enqueueWriteLock(int lockId) throws InterruptedException {
        waitCas(lock, 0, lockId);
    }

    public void enqueueWriteLockUI() {
        waitCasUI(lock, 0, -1);
    }

    public void enqueueWriteLockUI(int lockId) {
        waitCasUI(lock, 0, lockId);
    }

    public void releaseWriteLock() {
        if(!lock.compareAndSet(-1, 0))
            throw new IllegalStateException("Not locked");
    }

    public void releaseWriteLock(int lockId) {
        if(!lock.compareAndSet(lockId, 0))
            throw new IllegalStateException("Not locked");
    }

    private static void waitCas(AtomicInteger lock, int from, int to) throws InterruptedException {
        while (!lock.compareAndSet(from, to)) {
            Thread.yield();
            if (lock.compareAndSet(from, to)) break;
            Thread.sleep(5);
        }
    }

    private static void waitCasUI(AtomicInteger lock, int from, int to) {
        while (!lock.compareAndSet(from, to)) {
            Thread.yield();
            if (lock.compareAndSet(from, to)) break;
            try {
                Thread.sleep(5);
            } catch (InterruptedException ignored) {}
        }
    }
}
