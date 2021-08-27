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

import roj.collect.LongBitSet.FItr;

import javax.annotation.Nonnull;
import java.util.PrimitiveIterator;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/11 22:58
 */
public class SingleBitSet implements IBitSet {
    protected long set;
    protected byte max, size;

    public SingleBitSet() {
        max = -1;
    }

    private SingleBitSet(long set, int max) {
        this.set = set;
        this.max = (byte) max;
    }

    public boolean contains(int key) {
        if (key < 0) return false;
        return (set & (1L << key)) != 0;
    }

    private static void check(int i) {
        if (i < 0 || i >= 64) {
            throw new IllegalArgumentException("SingleBitSet suppport: [0, 63], got " + i);
        }
    }

    public int first() {
        if (max == -1) return -1;
        for (int i = 0; i <= max; i++) {
            if ((set & (1L << i)) != 0)
                return i;
        }
        return -1;
    }

    public int last() {
        return max;
    }

    @Override
    public int size() {
        return size;
    }

    public boolean add(int e) {
        check(e);
        if (e > max)
            max = (byte) e;
        if ((set & (1L << e)) != 0) {
            return false;
        } else {
            set |= 1L << e;
            size++;
            return true;
        }
    }

    public boolean remove(int i) {
        if(i < 0)
            return false;
        if ((set & (1L << i)) != 0) {
            set ^= 1L << i;
            size--;
            return true;
        }
        return false;
    }

    @Nonnull
    public IntIterator iterator() {
        return new Itr(this);
    }

    @Override
    public IBitSet copy() {
        return new SingleBitSet(set, max);
    }

    public void fill(int len) {
        check(len);
        long k = 0;
        for (int i = 0; i < len; i++) {
            k |= 1L << i;
        }
        set = k;
        size = (byte) len;
        max = (byte) (len - 1);
    }

    @Override
    public IBitSet addAll(IBitSet ibs) {
        if(ibs instanceof SingleBitSet) {
            SingleBitSet ibs1 = (SingleBitSet) ibs;
            set |= ibs1.set;
            max = (byte) Math.max(max, ibs1.max);
        } else {
            for (PrimitiveIterator.OfInt i = ibs.iterator(); i.hasNext(); ) {
                int e = i.nextInt();
                if(e >= 64)
                    throw new IndexOutOfBoundsException("SBS only supports at most 64 values");
                add(e);
            }
        }
        return this;
    }

    public void clear() {
        set = 0;
        max = -1;
        size = 0;
    }

    @Override
    public String toString() {
        return "1x{" + Long.toBinaryString(set) + '}';
    }

    static final class Itr extends FItr {
        Itr(SingleBitSet $this) {
            super($this);
        }

        void checkNext(boolean next) {
            if (!next && !get)
                return;
            SingleBitSet $this = (SingleBitSet) this.$this;
            int pos = this.pos;
            while (pos <= $this.max) {
                if ((($this.set >>> pos) & 1L) != 0L) {
                    this.pos = (entry = pos) + 1;
                    get = false;
                    return;
                }
                pos++;
            }
            this.pos = pos;
            entry = -1;
        }
    }
}