/*
 * This file is a part of MoreItems
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

import roj.collect.MyHashMap;
import roj.collect.TimedHashMap;
import roj.concurrent.SimpleSpinLock;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Your description here
 *
 * @author solo6975
 * @version 0.1
 * @since 2021/7/24 12:21
 */
public class ConcurrentTimedHashMap<K, V> {
    private final SimpleSpinLock lock = new SimpleSpinLock();

    private final TimedHashMap<K, V> map;

    public ConcurrentTimedHashMap(int timeout) {
        map = new TimedHashMap<>(timeout);
    }

    public int size() {
        return map.size();
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public boolean containsKey(Object key) {
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

    public V get(Object key) {
        lock.enqueueReadLock();
        V v = map.get(key);
        lock.releaseReadLock();
        return v;
    }

    public V put(K key, V value) {
        lock.enqueueWriteLock(-1);
        V v = map.put(key, value);
        lock.releaseWriteLock(-1);
        return v;
    }

    public V remove(Object key) {
        lock.enqueueWriteLock(-2);
        V v = map.remove(key);
        lock.releaseWriteLock(-2);
        return v;
    }

    public void putAll(Map<? extends K, ? extends V> m) {
        lock.enqueueWriteLock(-3);
        map.putAll(m);
        lock.releaseWriteLock(-3);
    }

    public void clear() {
        lock.enqueueWriteLock(-4);
        map.clear();
        lock.releaseWriteLock(-4);
    }

    public void clearOutdatedEntry() {
        lock.enqueueWriteLock(-6);
        map.clearOutdatedEntry();
        lock.releaseWriteLock(-6);
    }

    public void retainOutDated(Collection<K> kholder, Collection<V> vholder, Predicate<MyHashMap.Entry<K, V>> kPredicate, int max) {
        lock.enqueueWriteLock(-7);
        map.retainOutDated(kholder, vholder, kPredicate, max);
        lock.releaseWriteLock(-7);
    }

    public void releaseIterationLock() {
        lock.releaseWriteLock(-5);
    }

    /**
     * @important Release Lock Manually !!!
     */
    @Nonnull
    public Set<K> keySet() {
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
    public Set<Map.Entry<K, V>> entrySet() {
        lock.enqueueWriteLock(-5);
        return map.entrySet();
    }

    @Override
    public String toString() {
        return map.toString();
    }

    public V putIfAbsent(K k, V v) {
        lock.enqueueWriteLock(-8);
        v = map.putIfAbsent(k, v);
        lock.enqueueWriteLock(-8);
        return v;
    }

    public void FORCE_RELEASE_LOCK() {
        lock.release();
    }
}
