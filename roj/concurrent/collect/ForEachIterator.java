package roj.concurrent.collect;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2021/2/1 15:40
 */
public final class ForEachIterator<K> implements Iterator<K>, Consumer<K>, Runnable {
    boolean hasNext;
    K next;
    final AtomicInteger lock = new AtomicInteger();
    final Iterable<K> iterable;

    public ForEachIterator(Iterable<K> iterable) {
        this.iterable = iterable;
    }

    /**
     * Returns {@code true} if the iteration has more elements.
     * (In other words, returns {@code true} if {@link #next} would
     * return an element rather than throwing an exception.)
     *
     * @return {@code true} if the iteration has more elements
     */
    @Override
    public boolean hasNext() {
        while (lock.get() != 0)
            Thread.yield();
        return hasNext;
    }

    /**
     * Returns the next element in the iteration.
     *
     * @return the next element in the iteration
     * @throws NoSuchElementException if the iteration has no more elements
     */
    @Override
    public K next() {
        while (lock.get() != 0)
            Thread.yield();
        if (!hasNext)
            throw new NoSuchElementException();
        K next = this.next;
        hasNext = false;
        synchronized (lock) {
            lock.notifyAll();
        }
        while (!lock.compareAndSet(2, 0))
            Thread.yield();
        return next;
    }

    /**
     * Performs this operation on the given argument.
     *
     * @param k the input argument
     */
    @Override
    public void accept(K k) {
        hasNext = true;
        next = k;
        lock.set(2);
        try {
            synchronized (lock) {
                lock.wait();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * When an object implementing interface <code>Runnable</code> is used
     * to create a thread, starting the thread causes the object's
     * <code>run</code> method to be called in that separately executing
     * thread.
     * <p>
     * The general contract of the method <code>run</code> is that it may
     * take any action whatsoever.
     *
     * @see Thread#run()
     */
    @Override
    public void run() {
        iterable.forEach(this);
    }
}
