package roj.collect;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2021/4/13 23:25
 */
public class RingBuffer<T> implements List<T> {
    Object[] array;
    int rPtr, wPtr, size;

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public boolean contains(Object o) {
        return false;
    }

    @Nonnull
    @Override
    public Iterator<T> iterator() {
        return null;
    }

    @Override
    public void forEach(Consumer<? super T> action) {

    }

    @Nonnull
    @Override
    public Object[] toArray() {
        return new Object[0];
    }

    @Nonnull
    @Override
    public <T1> T1[] toArray(@Nonnull T1[] a) {
        return null;
    }

    @Override
    public boolean add(T t) {
        return false;
    }

    @Override
    public boolean remove(Object o) {
        return false;
    }

    public T pop() {
        return null;
    }

    public T shift() {
        return null;
    }

    public boolean unshift(T t) {
        return false;
    }

    public boolean push(T t) {
        return false;
    }

    public void removeLast() {

    }

    @Override
    public boolean containsAll(@Nonnull Collection<?> c) {
        return false;
    }

    @Override
    public boolean addAll(@Nonnull Collection<? extends T> c) {
        return false;
    }

    @Override
    public boolean addAll(int index, @Nonnull Collection<? extends T> c) {
        return false;
    }

    @Override
    public boolean removeAll(@Nonnull Collection<?> c) {
        return false;
    }

    @Override
    public boolean removeIf(Predicate<? super T> filter) {
        return false;
    }

    @Override
    public boolean retainAll(@Nonnull Collection<?> c) {
        return false;
    }

    @Override
    public void replaceAll(UnaryOperator<T> operator) {

    }

    @Override
    public void sort(Comparator<? super T> c) {

    }

    @Override
    public void clear() {

    }

    @Override
    public T get(int index) {
        return null;
    }

    @Override
    public T set(int index, T element) {
        return null;
    }

    @Override
    public void add(int index, T element) {

    }

    @Override
    public T remove(int index) {
        return null;
    }

    @Override
    public int indexOf(Object o) {
        return 0;
    }

    @Override
    public int lastIndexOf(Object o) {
        return 0;
    }

    @Nonnull
    @Override
    public ListIterator<T> listIterator() {
        return null;
    }

    @Nonnull
    @Override
    public ListIterator<T> listIterator(int index) {
        return null;
    }

    @Nonnull
    @Override
    public List<T> subList(int fromIndex, int toIndex) {
        return null;
    }

    @Override
    public Spliterator<T> spliterator() {
        return null;
    }

    @Override
    public Stream<T> stream() {
        return null;
    }

    @Override
    public Stream<T> parallelStream() {
        return null;
    }
}
