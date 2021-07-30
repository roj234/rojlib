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

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/8/20 14:03
 */
public class ConcurrentFindHashSet<T> implements FindSet<T> {
    private final ConcurrentHashMap<T, T> set = new ConcurrentHashMap<>();

    public ConcurrentFindHashSet(Collection<T> list) {
        addAll(list);
    }

    @Override
    public T find(T t) {
        return set.getOrDefault(t, t);
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
        return set.containsKey(key);
    }

    @Nonnull
    @Override
    public Iterator<T> iterator() {
        throw new ConcurrentModificationException();
    }

    @Nonnull
    @Override
    public Object[] toArray() {
        throw new UnsupportedOperationException();
    }

    @Nonnull
    @Override
    public <T1> T1[] toArray(@Nonnull T1[] a) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean add(T key) {
        if(set.containsKey(key))
            return false;
        set.put(key, key);
        return true;
    }

    @Override
    public boolean remove(Object key) {
        return set.remove(key) != null;
    }

    @Override
    public boolean containsAll(@Nonnull Collection<?> c) {
        for (Object o : c)
            if(!set.containsKey(o))
                return false;
        return true;
    }

    @Override
    public boolean addAll(@Nonnull Collection<? extends T> m) {
        for (T t : m)
            set.put(t, t);
        return true;
    }

    @Override
    public boolean retainAll(@Nonnull Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(@Nonnull Collection<?> c) {
        for(Object o : c)
            set.remove(o);
        return true;
    }

    @Override
    public void clear() {
        set.clear();
    }
}
