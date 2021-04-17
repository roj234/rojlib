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
 * Binary set
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/11 22:53
 */
public interface IBitSet extends Iterable<Integer> {
    boolean contains(int key);
    boolean remove(int key);
    boolean add(int key);

    void clear();

    int first();
    int last();

    int size();

    void fill(int len);

    @Nonnull
    PrimitiveIterator.OfInt iterator();

    IBitSet copy();

    default IBitSet addAll(CharSequence s) {
        for (int i = 0; i < s.length(); i++)
            add(s.charAt(i));
        return this;
    }

    default IBitSet addAll(int... array) {
        for (int i : array)
            add(i);
        return this;
    }

    IBitSet addAll(IBitSet ibs);

    default boolean isEmpty() {
        return size() == 0;
    }
}
