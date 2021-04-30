package roj.asm.util;

import roj.collect.IBitSet;

import javax.annotation.Nonnull;
import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;

/**
 * This file is a part of MI <br>
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * @author Roj234
 * Filename: FlagList.java
 */
public class FlagList implements Iterable<Integer> {
    public short flag;

    public FlagList(int flag) {
        this.flag = (short) flag;
    }

    public FlagList(short... flags) {
        for (short flag : flags) {
            this.flag |= flag;
        }
    }

    public boolean isEmpty() {
        return flag == 0;
    }

    public boolean has(int o) {
        return (this.flag & o) != 0;
    }

    public boolean contains(int o) {
        return (this.flag & o) == o;
    }

    @Nonnull
    public PrimitiveIterator.OfInt iterator() {
        return new FlagIterator();
    }

    public void add(int flag) {
        this.flag |= flag;
    }

    public void remove(int o) {
        this.flag &= ~o;
    }

    public void clear() {
        this.flag = 0;
    }

    public FlagList copy() {
        return new FlagList(flag);
    }

    public String toAcc(String[] arr) {
        if (flag == 0)
            return "";
        StringBuilder sb = new StringBuilder();
        for (PrimitiveIterator.OfInt itr = iterator(); itr.hasNext(); ) {
            sb.append(AccessFlag.get(itr.nextInt(), arr)).append(' ');
        }
        return sb.deleteCharAt(sb.length() - 1).toString();
    }

    public class FlagIterator implements PrimitiveIterator.OfInt {
        private byte pos;

        @Override
        public boolean hasNext() {
            return checkNext();
        }

        @Override
        public int nextInt() {
            if (checkNext()) {
                return 1 << (pos++);
            }
            throw new NoSuchElementException();
        }

        private boolean checkNext() {
            if (pos >= 16)
                return false;
            while (!IBitSet.isBitTrue(FlagList.this.flag, pos)) {
                if (++pos >= 16)
                    return false;
            }
            return true;
        }

        @Override
        public void remove() {
            FlagList.this.flag |= ~(1 << pos);
        }
    }

    @Override
    public String toString() {
        return "Flag{" + getFlag() + '}';
    }

    private String getFlag() {
        StringBuilder sb = new StringBuilder();
        for (PrimitiveIterator.OfInt itr = this.iterator(); itr.hasNext(); ) {
            sb.append(AccessFlag.byIdField(itr.nextInt())).append(' ');
        }
        return sb.toString();
    }
}
