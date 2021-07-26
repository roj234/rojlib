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

import roj.collect.IntMap;
import roj.collect.IntMap.Entry;
import roj.concurrent.SimpleSpinLock;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Set;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/8/20 14:03
 */
public class ConcurrentIntMap<V> {
    private final SimpleSpinLock lock = new SimpleSpinLock();

    private final IntMap<V> map = new IntMap<>();

    public ConcurrentIntMap() {
    }

    public int size() {
        return map.size();
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public boolean containsKey(int key) {
        lock.enqueueReadLock();
        boolean c = map.containsKey(key);
        lock.releaseReadLock();
        return c;
    }

    public boolean containsValue(Object value) {
        lock.enqueueReadLock();
        boolean c = map.containsValue(value);
        lock.releaseReadLock();
        return c;
    }

    public V get(int key) {
        lock.enqueueReadLock();
        V v = map.get(key);
        lock.releaseReadLock();
        return v;
    }

    public V put(int key, V value) {
        lock.enqueueWriteLock(-1);
        V v = map.put(key, value);
        lock.releaseWriteLock(-1);
        return v;
    }

    public V remove(int key) {
        lock.enqueueWriteLock(-2);
        V v = map.remove(key);
        lock.releaseWriteLock(-2);
        return v;
    }

    public void putAll(IntMap<? extends V> m) {
        lock.enqueueWriteLock(-3);
        map.putAll(m);
        lock.releaseWriteLock(-3);
    }

    public void clear() {
        lock.enqueueWriteLock(-4);
        map.clear();
        lock.releaseWriteLock(-4);
    }

    public void releaseIterationLock() {
        lock.releaseWriteLock(-5);
    }

    /**
     * @important Release Lock Manually !!!
     */
    @Nonnull
    public IntMap.KeySet<V> keySet() {
        lock.enqueueWriteLock(-5);
        return map.keySet();
    }

    /**
     * @important Release Lock Manually !!!
     */
    @Nonnull
    public Collection<V> values() {
        lock.enqueueWriteLock(-5);
        return map.values();
    }

    /**
     * @important Release Lock Manually !!!
     */
    @Nonnull
    public Set<Entry<V>> entrySet() {
        lock.enqueueWriteLock(-5);
        return map.entrySet();
    }
}
