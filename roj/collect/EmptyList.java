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

import roj.util.EmptyArrays;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.UnaryOperator;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/4 14:19索引不变)
 */
public final class EmptyList<E> implements List<E>, Set<E> {
    private static final EmptyList<?> instance = new EmptyList<>();

    private EmptyList() {

    }

    /**
     * 空列表
     */
    @SuppressWarnings("unchecked")
    public static <R> EmptyList<R> getInstance() {
        return (EmptyList<R>) instance;
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public boolean contains(Object o) {
        return false;
    }

    @Nonnull
    @Override
    public Iterator<E> iterator() {
        return listIterator();
    }

    @Nonnull
    @Override
    public Object[] toArray() {
        return EmptyArrays.OBJECTS;
    }

    @Nonnull
    @Override
    public <T> T[] toArray(@Nonnull T[] a) {
        return a;
    }

    @Override
    public boolean add(E e) {
        return false;
    }

    @Override
    public boolean remove(Object o) {
        return false;
    }

    @Override
    public boolean containsAll(@Nonnull Collection<?> c) {
        return c.isEmpty();
    }

    @Override
    public boolean addAll(@Nonnull Collection<? extends E> c) {
        return false;
    }

    @Override
    public boolean addAll(int index, @Nonnull Collection<? extends E> c) {
        return false;
    }

    @Override
    public boolean removeAll(@Nonnull Collection<?> c) {
        return false;
    }

    @Override
    public boolean retainAll(@Nonnull Collection<?> c) {
        return false;
    }

    @Override
    public void replaceAll(UnaryOperator<E> operator) {

    }

    @Override
    public void sort(Comparator<? super E> c) {

    }

    @Override
    public void clear() {
    }

    @Override
    public E get(int index) {
        return null;
    }

    @Override
    public E set(int index, E element) {
        return null;
    }

    @Override
    public void add(int index, E element) {
    }

    @Override
    public E remove(int index) {
        return null;
    }

    @Override
    public int indexOf(Object o) {
        return -1;
    }

    @Override
    public int lastIndexOf(Object o) {
        return -1;
    }

    @Nonnull
    @Override
    public ListIterator<E> listIterator() {
        return listIterator(0);
    }

    @Nonnull
    @Override
    public ListIterator<E> listIterator(int index) {
        return Collections.emptyListIterator();
    }

    @Nonnull
    @Override
    public List<E> subList(int fromIndex, int toIndex) {
        return this;
    }

    @Override
    public Spliterator<E> spliterator() {
        return Spliterators.emptySpliterator();
    }
}