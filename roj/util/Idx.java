/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: Idx.java
 */
package roj.util;

import roj.collect.LongBitSet;

import java.util.PrimitiveIterator;

/**
 * Idx 槽位筛选器
 */
public final class Idx {
    private final LongBitSet list;
    private short adds;
    private final short len;
    private byte str = 0;

    public Idx(int length) {
        list = new LongBitSet(length);
        list.fillAll(length);
        len = (short) length;
    }

    public Idx(int offset, int length) {
        this(length);
        str = (byte) offset;
    }

    public void add(int id) {
        list.remove(id - str);
        adds++;
    }

    public boolean contains(int id) {
        return !list.contains(id - str);
    }

    public boolean isFull() {
        return adds == len;
    }

    public PrimitiveIterator.OfInt remains() {
        return list.iterator();
    }

    public int size() {
        return adds;
    }
}