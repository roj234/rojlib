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
    protected byte max;

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
        int i = 0;
        long set = this.set;
        while (set != 0) {
            if (((set >>>= 1) & 1) == 1) {
                i++;
            }
        }
        return i;
    }

    public boolean add(int e) {
        check(e);
        if (e > max)
            max = (byte) e;
        if ((set & (1L << e)) != 0) {
            return false;
        } else {
            set |= 1 << e;
            return true;
        }
    }

    public boolean remove(int i) {
        if(i < 0)
            return false;
        if ((set & (1L << i)) != 0) {
            set ^= 1 << i;
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

    public void fillAll() {
        set = 0xffffffffffffffffL;
        max = 63;
    }

    public void fillAll(int len) {
        check(len);
        long k = 0;
        for (int i = 0; i <= len; i++) {
            k |= 1 << i;
        }
        set = k;
        max = (byte) len;
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
    }

    private static class Itr implements IntIterator {
        protected boolean get;

        private final SingleBitSet $this;

        protected int pos;
        protected int entry;

        protected Itr(SingleBitSet $this) {
            this.$this = $this;
        }

        public boolean hasNext() {
            checkNext(false);
            return entry != -1;
        }

        private void checkNext(boolean next) {
            if (!next && !get)
                return;
            while (pos < 64) {
                if ((($this.set >>> pos) & 1) == 1) {
                    entry = pos++;
                    get = false;
                    return;
                }
                pos++;
            }
            entry = -1;
        }

        public int nextInt() {
            get = true;
            int ent = this.entry;
            checkNext(true);
            return ent;
        }

        @Nonnull
        public Itr reset() {
            get = true;
            pos = 0;
            return this;
        }
    }
}