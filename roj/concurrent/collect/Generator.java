/*
 * This file is a part of MI
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 Roj234
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package roj.concurrent.collect;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2021/2/1 15:40
 */
public final class Generator<K> implements Iterator<K>, Consumer<K>, Runnable {
    K next;
    final AtomicInteger lock = new AtomicInteger();
    final Iterable<K> iterable;

    public Generator(Iterable<K> iterable) {
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
        checkNext();
        return lock.get() == 1;
    }

    /**
     * Returns the next element in the iteration.
     *
     * @return the next element in the iteration
     * @throws NoSuchElementException if the iteration has no more elements
     */
    @Override
    public K next() {
        checkNext();
        if (1 != lock.getAndSet(0))
            throw new NoSuchElementException();
        return this.next;
    }

    private void checkNext() {
        if(lock.get() != 0)
            return;
        synchronized (lock) {
            lock.notify();
        }
        if (lock.get() == 0) {
            Thread.yield();
            while (lock.get() == 0)
                LockSupport.parkNanos(20);
        }
    }

    /**
     * Performs this operation on the given argument.
     *
     * @param k the input argument
     */
    @Override
    public void accept(K k) {
        next = k;
        lock.set(1);
        try {
            synchronized (lock) {
                lock.wait();
            }
        } catch (InterruptedException ignored) {}
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
        lock.set(-1);
    }
}
