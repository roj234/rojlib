package roj.collect;

import javax.annotation.Nonnull;
import java.util.PrimitiveIterator;

/**
 * This file is a part of MI <br>
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究
 * <p>
 * @author Roj234
 * Filename: SingleBitSet.java
 */
public class SingleBitSet implements IBitSet {
    protected long set;
    protected int max = -1;

    public SingleBitSet() {
    }

    private SingleBitSet(long set, int max) {
        this.set = set;
        this.max = max;
    }

    public boolean contains(int key) {
        if (key > 63) return false;
        return IBitSet.isBitTrue(set, checkLimit(key));
    }

    private int checkLimit(int i) {
        if (i >= 64) {
            throw new IllegalArgumentException("FastIntSetL can only contains 64 bits");
        }
        if (i > max)
            max = i;
        return i & 63;
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
        int i = checkLimit(e);
        if (contains(e)) {
            return false;
        } else {
            set |= 1 << i;
            return true;
        }
    }

    public boolean remove(int i) {
        if (contains(i)) {
            set ^= 1 << checkLimit(i);
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
        max = 64;
    }

    public void fillAll(int len) {
        int o = len & 63;
        long k = 0;
        for (int i = 0; i < o; i++) {
            k |= 1 << i;
        }
        set = k;
        max = len;
    }

    @Override
    public IBitSet addAll(IBitSet ibs) {
        if(ibs instanceof SingleBitSet) {
            SingleBitSet ibs1 = (SingleBitSet) ibs;
            set |= ibs1.set;
            max = Math.max(max, ibs1.max);
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