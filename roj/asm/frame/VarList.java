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

package roj.asm.frame;

import roj.collect.ArrayIterator;
import roj.util.ArrayUtil;

import javax.annotation.Nonnull;
import java.util.Iterator;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/2 23:28
 */
public final class VarList implements Iterable<Var> {
    static final Var[] EMPTY = new Var[0];

    public Var[] list = EMPTY;
    public int size = 0;
    private int cap = 0;

    public VarList() {}

    public VarList copyFrom(VarList other) {
        this.size = 0;
        ensureCapacity(other.size + 4);
        this.size = other.size;
        System.arraycopy(other.list, 0, this.list, 0, other.size);
        return this;
    }

    public void ensureCapacity(int size) {
        if (cap >= size) return;
        Var[] newList = new Var[size];
        if (this.size > 0)
            System.arraycopy(list, 0, newList, 0, this.size);
        list = newList;
        cap = size;
    }

    public void add(Var e) {
        if (size >= cap) {
            ensureCapacity(cap + 4);
        }

        list[size++] = e;
    }

    public void set(int index, Var e) {
        if (index >= cap) {
            ensureCapacity(index + 4);
        }

        list[index] = e;

        if (size < index + 1) {
            size = index + 1;
        }
    }

    public void pop(int index) {
        if (size < index) throw new IllegalArgumentException("Size will < 0 after pop.");

        /*for (int i = size - index; i < size; i++) {
            list[i] = null;
        }*/

        size -= index;
    }

    public void removeTo(int index) {
        if (index < 0) throw new IllegalArgumentException("Size will < 0 after pop.");

        ensureCapacity(index);

        /*for (int i = index; i < size; i++) {
            list[i] = null;
        }*/

        size = index;
    }

    public Var get(int index) {
        if (index < 0 || index >= size)
            throw new IllegalArgumentException("Index(" + index + ") > Size(" + size + ")");
        Var v = list[index];
        if(v == null) {
            throw new IllegalArgumentException("Var[" + index + "] is not registered");
        }
        return v;
    }

    public Var at(int index) {
        return list[index];
    }

    public String toString() {
        return ArrayUtil.toString(list, 0, size);
    }

    public void clear() {
        size = 0;/*
        Arrays.fill(list, null);*/
    }

    @Nonnull
    @Override
    public Iterator<Var> iterator() {
        return new ArrayIterator<>(this.list, 0, size);
    }
}