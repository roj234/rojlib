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
package roj.collect;

import javax.annotation.Nonnull;
import java.lang.reflect.Array;
import java.util.*;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2021/5/23 17:39
 */
public class ConcatedCollection<T> implements Collection<T> {
    Collection<T>[] collections;

    @SafeVarargs
    public ConcatedCollection(Collection<T>... collections) {
        this.collections = collections;
    }

    /**
     * Returns the number of elements in this collection.  If this collection
     * contains more than <tt>Integer.MAX_VALUE</tt> elements, returns
     * <tt>Integer.MAX_VALUE</tt>.
     *
     * @return the number of elements in this collection
     */
    @Override
    public int size() {
        int size = 0;
        for (Collection<T> t : collections) {
            size += t.size();
        }
        return size;
    }

    /**
     * Returns <tt>true</tt> if this collection contains no elements.
     *
     * @return <tt>true</tt> if this collection contains no elements
     */
    @Override
    public boolean isEmpty() {
        for (Collection<T> t : collections) {
            if (!t.isEmpty())
                return false;
        }
        return true;
    }

    /**
     * Returns <tt>true</tt> if this collection contains the specified element.
     * More formally, returns <tt>true</tt> if and only if this collection
     * contains at least one element <tt>e</tt> such that
     * <tt>(o==null&nbsp;?&nbsp;e==null&nbsp;:&nbsp;o.equals(e))</tt>.
     *
     * @param o element whose presence in this collection is to be tested
     * @return <tt>true</tt> if this collection contains the specified
     * element
     * @throws ClassCastException   if the type of the specified element
     *                              is incompatible with this collection
     *                              (<a href="#optional-restrictions">optional</a>)
     * @throws NullPointerException if the specified element is null and this
     *                              collection does not permit null elements
     *                              (<a href="#optional-restrictions">optional</a>)
     */
    @Override
    public boolean contains(Object o) {
        for(Collection<T> t : collections) {
            if(t.contains(o))
                return true;
        }
        return false;
    }

    @Nonnull
    @Override
    public Iterator<T> iterator() {
        List<Iterator<T>> itrs = new ArrayList<>();
        for (Collection<T> t : collections) {
            Iterator<T> itr = t.iterator();
            if(itr.hasNext())
                itrs.add(itr);
        }
        return new MergedItr<>(itrs);
    }

    public static class MergedItr<T> extends AbstractIterator<T> {
        final List<Iterator<T>> itrs;
        int i;

        @SafeVarargs
        public MergedItr(Iterator<T> ... iterators) {
            this.itrs = Arrays.asList(iterators);
        }

        public MergedItr(List<Iterator<T>> iterators) {
            this.itrs = iterators;
        }

        @Override
        public boolean computeNext() {
            while (i < itrs.size()) {
                Iterator<T> itr = itrs.get(i);
                if(itr.hasNext()) {
                    result = itr.next();
                    return true;
                }
                i++;
            }
            return false;
        }
    }

    @Nonnull
    @Override
    public Object[] toArray() {
        Object[] a = new Object[size()];

        int i = 0;
        for (Collection<T> t : collections) {
            for (T tt : t) {
                a[i++] = tt;
            }
        }
        return a;
    }

    @Nonnull
    @Override
    @SuppressWarnings("unchecked")
    public <T1> T1[] toArray(@Nonnull T1[] a) {
        int size = size();
        if (a.length < size) {
            Class<?> newType = a.getClass();
            a = (newType == Object[].class)
                    ? (T1[]) new Object[size]
                    : (T1[]) Array.newInstance(newType.getComponentType(), size);
        }

        int i = 0;
        for (Collection<T> t : collections) {
            for (T tt : t) {
                a[i++] = (T1) tt;
            }
        }
        return a;
    }

    @Override
    public boolean add(T t) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object o) {
        boolean flag = false;
        for (Collection<T> t : collections)
            flag |= t.remove(o);
        return flag;
    }

    /**
     * Returns <tt>true</tt> if this collection contains all of the elements
     * in the specified collection.
     *
     * @param c collection to be checked for containment in this collection
     * @return <tt>true</tt> if this collection contains all of the elements
     * in the specified collection
     * @throws ClassCastException   if the types of one or more elements
     *                              in the specified collection are incompatible with this
     *                              collection
     *                              (<a href="#optional-restrictions">optional</a>)
     * @throws NullPointerException if the specified collection contains one
     *                              or more null elements and this collection does not permit null
     *                              elements
     *                              (<a href="#optional-restrictions">optional</a>),
     *                              or if the specified collection is null.
     * @see #contains(Object)
     */
    @Override
    public boolean containsAll(@Nonnull Collection<?> c) {
        return false;
    }

    @Override
    public boolean addAll(@Nonnull Collection<? extends T> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(@Nonnull Collection<?> c) {
        boolean flag = false;
        for (Collection<T> t : collections)
            flag |= t.removeAll(c);
        return flag;
    }

    @Override
    public boolean retainAll(@Nonnull Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        for (Collection<T> t : collections)
            t.clear();
    }
}
