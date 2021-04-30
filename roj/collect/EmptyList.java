/**
 * This file is a part of MI <br>
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: SimpleList.java(索引不变)
 */
package roj.collect;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.UnaryOperator;

public class EmptyList<E> implements List<E>, Set<E> {
    public static final Object[] EMPTY = new Object[0];
    public static final Class<?>[] EMPTY_C = new Class<?>[0];

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
        return false;
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
        return new Object[0];
    }

    @Nonnull
    @Override
    public <T> T[] toArray(@Nonnull T[] a) {
        return a;
    }

    @Override
    public boolean add(E e) {
        return true;
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