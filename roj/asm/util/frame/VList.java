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
    public Var[] list = new Var[4];
    public int size = 0;
    private int length = 4;

    public VList() {}

    public VList copyFrom(VList other) {
        this.size = 0;
        ensureCapacity(other.size + 4);
        this.size = other.size;
        System.arraycopy(other.list, 0, this.list, 0, other.size);
        return this;
    }

    public void ensureCapacity(int size) {
        if (length >= size) return;
        Var[] newList = new Var[size];
        if (this.size > 0)
            System.arraycopy(list, 0, newList, 0, this.size);
        list = newList;
        length = size;
    }

    public int add(Var e) {
        list[size++] = e;

        if (size >= length) {
            ensureCapacity(length + 4);
        }

        return (e.type == VarType.DOUBLE || e.type == VarType.LONG) ? 2 : 1;
    }

    public void set(int index, Var e) {
        if (index >= length) {
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