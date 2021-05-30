/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: VList.java
 */
package roj.asm.util.frame;

import roj.collect.ArrayIterator;
import roj.util.ArrayUtil;

import javax.annotation.Nonnull;
import java.util.Iterator;

public final class VList implements Iterable<Var> {
    static final Var[] EMPTY = new Var[0];

    public Var[] list = EMPTY;
    public int size = 0;
    private int cap = 0;

    public VList() {}

    public VList copyFrom(VList other) {
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