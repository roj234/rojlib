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
package roj.kscript.util.opm;

import roj.collect.ArrayIterator;
import roj.util.Helpers;

import java.io.Serializable;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2021/4/26 18:16
 */
public final class OneList<E> extends AbstractList<E> implements RandomAccess, Serializable {
    private static final long serialVersionUID = 3093736618740652951L;

    private E e;
    private boolean empty;

    public OneList(E obj)                {e = obj;}

    public Iterator<E> iterator() {
        return Helpers.cast(new ArrayIterator<>(new Object[] {e}));
    }

    public int size()                   {return empty ? 0 : 1;}

    public boolean contains(Object obj) {return Objects.equals(obj, e);}

    public E get(int index) {
        if (index != 0 || empty)
            throw new IndexOutOfBoundsException("Index: "+index+", Size: 1");
        return e;
    }

    @Override
    public E set(int index, E element) {
        if (index != 0 || empty)
            throw new IndexOutOfBoundsException("Index: "+index+", Size: 1");
        E elem = this.e;
        this.e = element;
        return elem;
    }

    // Override default methods for Collection
    @Override
    public void forEach(Consumer<? super E> action) {
        action.accept(e);
    }
    @Override
    public boolean removeIf(Predicate<? super E> filter) {
        throw new UnsupportedOperationException();
    }
    @Override
    public void replaceAll(UnaryOperator<E> operator) {
        e = operator.apply(e);
    }
    @Override
    public void sort(Comparator<? super E> c) {
    }
    @Override
    public Spliterator<E> spliterator() {
        throw new UnsupportedOperationException();
    }

    public void setEmpty(boolean empty) {
        this.empty = empty;
        if(empty)
            e = null;
    }
}
