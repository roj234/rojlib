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
import java.util.Arrays;
import java.util.NoSuchElementException;

/**
 * @author Roj234
 * @since 2021/5/11 23:9
 */
public class MyBitSet implements Iterable<Integer> {
    protected long[] set;
    protected int cap;
    protected int max = -1;
    protected int size;

    public MyBitSet() {
        this(1);
    }

    public MyBitSet(int size) {
        this.set = new long[(size >> 6) + 1];
        this.cap = set.length << 6;
    }

    public static MyBitSet from(String data) {
        MyBitSet set = new MyBitSet(data.length());
        for (int i = 0; i < data.length(); i++) {
            set.add(data.charAt(i));
        }
        return set;
    }

    public static MyBitSet from(byte... data) {
        MyBitSet set = new MyBitSet(data.length);
        for (byte i : data) {
            set.add(i);
        }
        return set;
    }

    public static MyBitSet from(int... data) {
        MyBitSet set = new MyBitSet(data.length);
        for (int i : data) {
            set.add(i);
        }
        return set;
    }

    public static MyBitSet fromRange(int from, int to) {
        MyBitSet set = new MyBitSet(to);
        while (from < to) set.add(from++);
        return set;
    }

    public boolean contains(int key) {
        if (key < 0 || (key >>> 6) >= set.length) return false;
        return (set[key >>> 6] & (1L << (key & 63))) != 0;
    }

    private void expand(int i) {
        if (i >= cap) {
            long[] newSet = new long[(i >> 6) + 1];

            this.cap = newSet.length << 6;
            System.arraycopy(set, 0, newSet, 0, set.length);

            this.set = newSet;
        }
    }

    public int size() {
        return size;
    }

    public int first() {
        if (max == -1) return -1;
        for (int i = 0; i <= max; i++) {
            if (contains(i))
                return i;
        }
        return -1;
    }

    public int last() {
        return max;
    }

    public MyBitSet addAll(MyBitSet ibs) {
        // 100% true ...
        expand(ibs.max);
        int ml = (ibs.max >>> 6) + 1;
        int ds = 0;
        for (int i = 0; i < ml; i++) {
            long s = ibs.set[i],
                    os = s & ~(set[i] & s);

            while (os != 0) {
                if (((os >>>= 1) & 1) == 1) { // both have
                    ds++;
                }
            }

            set[i] |= s;
        }

        size += ds;
        return this;
    }

    public boolean add(int e) {
        if(e < 0) throw new IllegalArgumentException();
        if (e > max)
            max = e;
        expand(e);
        long v = set[e >>> 6];
        if (v != (v = v | 1L << (e & 63))) {
            set[e >>> 6] = v;
            size++;
            return true;
        }
        return false;
    }

    /**
     * @return 第n个真bit
     */
    public int nthTrue(int n) {
        if (n < 0 || n > max) return -1;

        int i = 0;
        for (int j = 0; j < set.length; j++) {
            long k = set[j];
            while (k != 0) {
                if ((k & 1) != 0) {
                    if (n-- == 0) return i;
                }

                i++;
                k >>>= 1;
            }
        }

        return -1;
    }

    public boolean remove(int e) {
        if (e < 0 || (e >>> 6) >= set.length) return false;
        expand(e);
        if ((set[e >>> 6] & 1L << (e & 63)) != 0) {
            set[e >>> 6] &= ~(1L << (e & 63));
            if (e == max) {
                int o = e >>> 6;
                while (true) {
                    long v = set[o];

                    if(v != 0) {
                        int pos = 63;
                        while ((v & Long.MIN_VALUE) == 0) {
                            v <<= 1;
                            pos--;
                        }
                        max = o << 6 | pos;
                        break;
                    }

                    if(--o == -1) {
                        max = -1;
                        break;
                    }
                }
            }
            size--;
            return true;
        }
        return false;
    }

    @Nonnull
    public IntIterator iterator() {
        return new FItr(this);
    }

    public void fill(int len) {
        expand(len);
        int x = len >> 6;
        for (int i = 0; i < x; i++)
            set[i] = 0xffffffffffffffffL;
        int o = len & 63;
        if (o != 0) {
            long k = 0;
            for (int i = 0; i < o; i++) {
                k |= 1L << i;
            }
            set[x] = k;
        }
        size = len;
        max = len - 1;
    }

    public MyBitSet copy() {
        MyBitSet copied = new MyBitSet(cap);
        System.arraycopy(set, 0, copied.set, 0, set.length);
        copied.max = this.max;
        copied.size = this.size;
        return copied;
    }

    public void clear() {
        Arrays.fill(set, 0);
        max = -1;
        size = 0;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(max + 5).append(set.length).append("x{");
        if(max != -1) {
            for (int k = (max >> 6), i = k; i >= 0; i--) {
                long se = set[i];
                for (int j = i == k ? max & 63 : 63; j >= 0; j--) {
                    sb.append((se & (1L << j)) == 0 ? "0" : "1");
                }
            }
        }
        return sb.append('}').toString();
    }

    static class FItr implements IntIterator {
        boolean get;

        final MyBitSet $this;

        int pos, entry, previous;

        protected FItr(MyBitSet $this) {
            this.$this = $this;
            this.get = true;
            this.entry = -1;
            this.previous = -1;
        }

        public boolean hasNext() {
            checkNext(false);
            return entry != -1;
        }

        void checkNext(boolean next) {
            if (!next && !get)
                return;
            MyBitSet $this = this.$this;
            int pos = this.pos;
            while (pos <= $this.max) {
                long set = $this.set[pos >>> 6];
                if (set == 0) {
                    pos += 64;
                } else {
                    if (((set >>> (pos & 63L)) & 1L) != 0L) {
                        this.pos = (entry = pos) + 1;
                        get = false;
                        return;
                    }
                    pos++;
                }
            }
            this.pos = pos;
            entry = -1;
        }

        public int nextInt() {
            checkNext(false);
            if (this.entry == -1)
                throw new NoSuchElementException();

            get = true;
            previous = this.entry;
            checkNext(true);
            return previous;
        }

        @Nonnull
        public FItr reset() {
            get = true;
            pos = 0;
            entry = -1;
            previous = -1;
            return this;
        }

        @Override
        public void remove() {
            if (this.previous == -1)
                throw new IllegalStateException();
            $this.remove(this.previous);
            this.previous = -1;
        }
    }
}