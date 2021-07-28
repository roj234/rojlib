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

import roj.collect.FindMap;
import roj.collect.MyHashMap;
import roj.concurrent.SimpleSpinLock;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Map;
import java.util.Set;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/8/20 14:03
 */
public class ConcurrentFindHashMap<K, V> implements FindMap<K, V> {
    private final SimpleSpinLock lock = new SimpleSpinLock();

    private final MyHashMap<K, V> map = new MyHashMap<>();

    public ConcurrentFindHashMap() {
    }

    @Override
    public Entry<K, V> find(K k) {
        lock.enqueueReadLock();
        Entry<K, V> entry = map.find(k);
        lock.releaseReadLock();
        return entry;
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean containsKey(Object key) {
        lock.enqueueReadLock();
        boolean c = map.containsKey(key);
        lock.releaseReadLock();
        return c;
    }

    @Override
    public boolean containsValue(Object value) {
        lock.enqueueReadLock();
        boolean c = map.containsValue(value);
        lock.releaseReadLock();
        return c;
    }

    @Override
    public V get(Object key) {
        lock.enqueueReadLock();
        V v = map.get(key);
        lock.releaseReadLock();
        return v;
    }

    @Override
    public V put(K key, V value) {
        lock.enqueueWriteLock(-1);
        V v = map.put(key, value);
        lock.releaseWriteLock(-1);
        return v;
    }

    @Override
    public V remove(Object key) {
        lock.enqueueWriteLock(-2);
        V v = map.remove(key);
        lock.releaseWriteLock(-2);
        return v;
    }

    @Override
    public void putAll(@Nonnull Map<? extends K, ? extends V> m) {
        lock.enqueueWriteLock(-3);
        map.putAll(m);
        lock.releaseWriteLock(-3);
    }

    @Override
    public void clear() {
        lock.enqueueWriteLock(-4);
        map.clear();
        lock.releaseWriteLock(-4);
    }

    @Nonnull
    @Override
    public Set<K> keySet() {
        throw new ConcurrentModificationException();
    }

    @Nonnull
    @Override
    public Collection<V> values() {
        throw new ConcurrentModificationException();
    }

    @Nonnull
    @Override
    public Set<Entry<K, V>> entrySet() {
        throw new ConcurrentModificationException();
    }
}
