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

import roj.text.CharList;

import javax.annotation.Nonnull;
import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;

/**
 * Access flag list
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public class FlagList implements Iterable<Integer> {
    public char flag;

    public FlagList() {}

    public FlagList(int flag) {
        this.flag = (char) flag;
    }

    public boolean hasAny(int o) {
        return (this.flag & o) != 0;
    }

    public boolean hasAll(int o) {
        return (this.flag & o) == o;
    }

    @Nonnull
    public PrimitiveIterator.OfInt iterator() {
        return new FlagIterator(this);
    }

    public void add(int flag) {
        this.flag |= flag;
    }

    public void remove(int o) {
        this.flag &= ~o;
    }

    public FlagList copy() {
        return new FlagList(flag);
    }

    static final class FlagIterator implements PrimitiveIterator.OfInt {
        private byte pos;
        private final FlagList fl;

        public FlagIterator(FlagList fl) {
            this.fl = fl;
        }

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
            while ((fl.flag & (1 << pos)) == 0) {
                if (++pos >= 16)
                    return false;
            }
            return true;
        }

        @Override
        public void remove() {
            fl.flag |= ~(1 << pos);
        }
    }

    @Override
    public String toString() {
        return "[" + getFlag() + ']';
    }

    public static final String[] ALL_ACC_STRING = new String[]{
            "public", "private", "protected", "static", "final", "synchronized", "bridge", "varargs", "native", "interface", "abstract", "strictfp", "synthetic", "annotation", "enum", "module"
    };

    public static String get(int val, String[] strings) {
        for (int i = 0; i < 16; i++) {
            if (val == 1 << i) {
                String s = strings[i];
                if (s == null) {
                    break;
                }
                return s;
            }
        }
        return Integer.toString(val);
    }

    private String getFlag() {
        CharList sb = new CharList();
        for (PrimitiveIterator.OfInt itr = this.iterator(); itr.hasNext(); ) {
            sb.append(get(itr.nextInt(), ALL_ACC_STRING)).append(' ');
        }
        if(sb.length() > 0)
            sb.setIndex(sb.length() - 1);
        return sb.toString();
    }
}
