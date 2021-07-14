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
package roj.asm.util;

import roj.collect.IBitSet;

import javax.annotation.Nonnull;
import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
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
