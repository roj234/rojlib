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

import roj.collect.FindSet;
import roj.collect.MyHashSet;
import roj.concurrent.SimpleSpinLock;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/8/20 14:03
 */
public class ConcurrentFindHashSet<T> implements FindSet<T> {
    private final SimpleSpinLock lock = new SimpleSpinLock();

    private final MyHashSet<T> set = new MyHashSet<>();

    public ConcurrentFindHashSet(Collection<T> list) {
        addAll(list);
    }

    @Override
    public T find(T t) {
        lock.enqueueReadLock();
        t = set.find(t);
        lock.releaseReadLock();
        return t;
    }

    @Override
    public int size() {
        return set.size();
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean contains(Object key) {
        lock.enqueueReadLock();
        boolean c = set.contains(key);
        lock.releaseReadLock();
        return c;
    }

    @Nonnull
    @Override
    public Iterator<T> iterator() {
        throw new ConcurrentModificationException();
    }

    @Nonnull
    @Override
    public Object[] toArray() {
        lock.enqueueReadLock();
        Object[] o = set.toArray();
        lock.releaseReadLock();
        return o;
    }

    @Nonnull
    @Override
    public <T1> T1[] toArray(@Nonnull T1[] a) {
        lock.enqueueReadLock();
        T1[] t1 = set.toArray(a);
        lock.releaseReadLock();
        return t1;
    }

    @Override
    public boolean add(T key) {
        lock.enqueueWriteLock(-1);
        boolean b = set.add(key);
        lock.releaseWriteLock(-1);
        return b;
    }

    @Override
    public boolean remove(Object key) {
        lock.enqueueWriteLock(-2);
        boolean b = set.remove(key);
        lock.releaseWriteLock(-2);
        return b;
    }

    @Override
    public boolean containsAll(@Nonnull Collection<?> c) {
        lock.enqueueReadLock();
        boolean b = set.containsAll(c);
        lock.releaseReadLock();
        return b;
    }

    @Override
    public boolean addAll(@Nonnull Collection<? extends T> m) {
        lock.enqueueWriteLock(-3);
        boolean b = set.addAll(m);
        lock.releaseWriteLock(-3);
        return b;
    }

    @Override
    public boolean retainAll(@Nonnull Collection<?> c) {
        lock.enqueueWriteLock(-4);
        boolean b = set.retainAll(c);
        lock.releaseWriteLock(-4);
        return b;
    }

    @Override
    public boolean removeAll(@Nonnull Collection<?> c) {
        lock.enqueueWriteLock(-5);
        boolean b = set.removeAll(c);
        lock.releaseWriteLock(-5);
        return b;
    }

    @Override
    public void clear() {
        lock.enqueueWriteLock(-6);
        set.clear();
        lock.releaseWriteLock(-6);
    }
}
