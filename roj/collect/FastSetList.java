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

import java.util.Set;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/8/11 14:59
 */
public class FastSetList<T> extends SimpleList<T> implements Set<T> {
    protected ToIntMap<T> indexer;
    int refOps, slowOps, lastChk;
    byte enable;

    public FastSetList() {
        this(16);
        indexer = new ToIntMap<>(16);
    }

    public FastSetList(int size) {
        ensureCapacity(size);
        indexer = new ToIntMap<>(size);
    }

    public boolean remove(Object o) {
        if(!initFastChk())
            return super.remove(o);

        int v = indexer.getOrDefault(o, -1);
        if (v == -1)
            return super.remove(o);

        super.remove(v);
        return true;
    }

    @Override
    public int indexOf(Object o) {
        if((slowOps++ & 31) == 0)
            update();
        if(!initFastChk())
            return super.indexOf(o);
        return indexer.getOrDefault(o, -1);
    }

    @Override
    public int lastIndexOf(Object o) {
        if((slowOps++ & 31) == 0)
            update();
        if(!initFastChk())
            return super.lastIndexOf(o);
        return indexer.getOrDefault(o, -1);
    }

    public boolean contains(Object e) {
        if((slowOps++ & 31) == 0)
            update();
        if(!initFastChk())
            return super.contains(e);
        return indexer.containsKey(e);
    }

    public void clear() {
        super.clear();
        indexer.clear();
        enable = 0;
    }

    @Override
    protected void handleAdd(int pos, T element) {
        if((refOps++ & 31) == 0)
            update();
        enable &= ~2;
    }

    @SuppressWarnings("unchecked")
    private boolean initFastChk() {
        if((enable & 1) == 1) {
            if((enable & 2) == 0) {
                indexer.clear();
                for (int i = 0; i < size; i++) {
                    indexer.putInt((T) list[i], i);
                }
                enable |= 2;
            }
            return true;
        }
        return false;
    }

    private void update() {
            int ct = (int) System.currentTimeMillis();
            if (ct - lastChk > 10000) {
                lastChk = ct;
                enable = (byte) ((enable & 254) | (refOps < slowOps / 100 ? 1 : 0));
                refOps = slowOps = 0;
            }
    }

    @Override
    protected void handleAdd(int pos, T[] elements, int offset, int length) {
        for (int i = offset; i < length; i++) {
            handleAdd(pos + i - offset, elements[i]);
        }
    }

    @Override
    protected void handleRemove(int pos, T element) {
        if((refOps++ & 31) == 0)
            update();
        enable &= ~2;

        indexer.remove(element);

    }

    @Override
    public void fill(T t) {
        throw new UnsupportedOperationException();
    }
}
