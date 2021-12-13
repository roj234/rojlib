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

package roj.util;

import roj.collect.IBitSet;
import roj.collect.IntIterator;
import roj.collect.LongBitSet;
import roj.collect.SingleBitSet;

/**
 * Idx 槽位筛选器
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public final class Idx {
    private IBitSet list;
    private short size, len;
    private byte str = 0;

    public Idx(int length) {
        list = length >= 64 ? new LongBitSet(length) : new SingleBitSet();
        list.fill(length);
        len = (short) length;
    }

    public Idx(int offset, int length) {
        this(length);
        str = (byte) offset;
    }

    public void reset(int length) {
        if(length >= 64 && !(list instanceof LongBitSet))
            list = new LongBitSet(length);
        list.fill(length);
        len = (short) length;
        size = 0;
    }

    public void add(int id) {
        list.remove(id - str);
        size++;
    }

    public boolean contains(int id) {
        return !list.contains(id - str);
    }

    public boolean isFull() {
        return size == len;
    }

    public IntIterator remains() {
        assert str == 0;
        return (IntIterator) list.iterator();
    }

    @Override
    public String toString() {
        return "Idx{" + list + ", remain=" + (len - size) + ", off=" + str + '}';
    }

    public int size() {
        return size;
    }
}