/**
 * This file is a part of MI <br>
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: IntSetL.java
 */
package roj.collect;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;

public class LongBitSet implements IBitSet {
    protected long[] set;
    protected int maxLen;
    protected int max = -1;
    protected int size;

    public LongBitSet() {
        this(1);
    }

    public LongBitSet(int size) {
        this.set = new long[(size >> 6) + 1];
        this.maxLen = set.length << 6;
    }

    public static LongBitSet preFilled(String data) {
        LongBitSet set = new LongBitSet(data.length());
        for (int i = 0; i < data.length(); i++) {
            set.add(data.charAt(i));
        }
        return set;
    }

    public static LongBitSet preFilled(byte... data) {
        LongBitSet set = new LongBitSet(data.length);
        for (byte i : data) {
            set.add(i);
        }
        return set;
    }

    public static LongBitSet preFilled(int... data) {
        LongBitSet set = new LongBitSet(data.length);
        for (int i : data) {
            set.add(i);
        }
        return set;
    }

    public boolean contains(int key) {
        if ((key >>> 6) >= set.length) return false;
        int k = checkLimit(key, false);
        return k != -1 && IBitSet.isBitTrue(set[indexFor(key)], k);
    }

    private int indexFor(int key) {
        return key >> 6;
    }

    private int checkLimit(int i, boolean check) {
        if (i >= maxLen) {
            if (!check)
                return -1;
            long[] newSet = new long[(i >> 6) + 1];

            //if(newSet.length - set.length > 1)
            //	System.err.println("FIS is linear so not auto-increment!!! curr: " + maxLen + ", need: " + i);

            this.maxLen = newSet.length << 6;
            System.arraycopy(set, 0, newSet, 0, set.length);

            this.set = newSet;
        }
        if (i > max)
            max = i;
        return i & 63;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public Integer first() {
        if (max == -1) return null;
        for (int i = 0; i <= max; i++) {
            if (contains(i))
                return i;
        }
        return null;
    }


    @Override
    public IBitSet addAll(IBitSet ibs) {
        if(ibs instanceof SingleBitSet) {
            SingleBitSet ibs1 = (SingleBitSet) ibs;
            long s = ibs1.set,
                    os = s & ~(set[0] & s);

            // added how much?
            // 110000 s
            // 100001 set[0]
            // 100000 os
            // s & ~os
            // 010000

            int ds = 0;
            while (os != 0) {
                if (((os >>>= 1) & 1) == 1) { // both have
                    ds++;
                }
            }

            set[0] |= s;

            size += ds;
        } else if(ibs instanceof LongBitSet) {
            // 100% true ...
            LongBitSet lbs = (LongBitSet) ibs;
            checkLimit(lbs.max, true);
            int ml = (lbs.max >>> 6) + 1;
            int ds = 0;
            for (int i = 0; i < ml; i++) {
                long s = lbs.set[i],
                        os = s & ~(set[i] & s);

                while (os != 0) {
                    if (((os >>>= 1) & 1) == 1) { // both have
                        ds++;
                    }
                }

                set[i] |= s;
            }

            size += ds;
        } else {
            for (PrimitiveIterator.OfInt i = ibs.iterator(); i.hasNext(); ) {
                add(i.nextInt());
            }
        }
        return this;
    }

    public boolean add(int e) {
        int i = checkLimit(e, true);
        if (!IBitSet.isBitTrue(set[indexFor(e)], i)) {
            set[indexFor(e)] |= 1L << i;
            size++;
            return true;
        }
        return false;
    }

    public boolean remove(int i) {
        if (contains(i)) {
            set[indexFor(i)] ^= 1L << checkLimit(i, false);
            size--;
            return true;
        }
        return false;
    }

    @Nonnull
    public IntIterator iterator() {
        return new FItr(this);
    }

    public void fillAll() {
        Arrays.fill(set, 0xffffffffffffffffL);
        size = max = maxLen;
    }

    public void fillAll(int len) {
        int x = Math.min(len >> 6, set.length);
        for (int i = 0; i < x; i++)
            set[i] = 0xffffffffffffffffL;
        int o = len & 63;
        if (o != 0 && x < set.length) {
            long k = 0;
            for (int i = 0; i < o; i++) {
                k |= 1L << i;
            }
            set[x] = k;
        }
        size = max = len;
    }

    public IBitSet copy() {
        LongBitSet copied = new LongBitSet(maxLen);
        System.arraycopy(set, 0, copied.set, 0, set.length);
        copied.max = this.max;
        return copied;
    }

    public void clear() {
        Arrays.fill(set, 0);
        max = -1;
        size = 0;
    }

    @Override
    public String toString() {
        return "FastIntSet{" +
                "set=" + Arrays.toString(set) +
                ", max=" + max +
                ", size=" + size +
                '}';
    }

    public static class FItr implements IntIterator {
        protected boolean get = true;

        private final LongBitSet $this;

        protected int pos;
        protected int entry;
        protected int previous = -1;

        protected FItr(LongBitSet $this) {
            this.$this = $this;
        }

        public boolean hasNext() {
            checkNext(false);
            return entry != -1;
        }

        private void checkNext(boolean next) {
            if (!next && !get)
                return;
            if ($this.isEmpty()) {
                entry = -1;
                return;
            }
            while (pos <= $this.max) {
                if ($this.set[pos >>> 6] == 0) {
                    pos += 64;
                } else {
                    if (($this.set[pos >>> 6] & (1L << (pos & 63))) != 0) {
                        entry = pos++;
                        get = false;
                        return;
                    }
                    pos++;
                }
            }
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