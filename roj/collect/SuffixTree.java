/*
 * This file is a part of MoreItems
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

import java.util.List;
import java.util.Map;

/**
 * 后缀树，TT是前缀树
 *
 * @author Roj233
 * @version 0.1
 * @since 2021/8/13 14:02
 */
public final class SuffixTree<V> extends TrieTree<V> {
    public SuffixTree() {}

    public SuffixTree(Map<CharSequence, V> map) {
        super(map);
    }

    @Override
    public boolean startsWith(CharSequence s, int i, int len) {
        return super.startsWith(ReverseOf.reverseOf(s, i, len), i, len);
    }

    @Override
    public Map.Entry<Integer, V> longestMatches(CharSequence s, int i, int len) {
        return super.longestMatches(ReverseOf.reverseOf(s, i, len), i, len);
    }

    @Override
    public List<Map.Entry<String, V>> entryMatches(CharSequence s, int i, int len, int limit) {
        return super.entryMatches(ReverseOf.reverseOf(s, i, len), i, len, limit);
    }

    @Override
    public List<V> valueMatches(CharSequence s, int i, int len, int limit) {
        return super.valueMatches(ReverseOf.reverseOf(s, i, len), i, len, limit);
    }

    @Override
    Entry<V> entryForPut(CharSequence s, int i, int len) {
        return super.entryForPut(ReverseOf.reverseOf(s, i, len), i, len);
    }

    @Override
    public Entry<V> getEntry(CharSequence s, int i, int len) {
        return super.getEntry(ReverseOf.reverseOf(s, i, len), i, len);
    }

    @Override
    V remove(CharSequence s, int i, int len, Object tc) {
        return super.remove(ReverseOf.reverseOf(s, i, len), i, len, tc);
    }

    static final class ReverseOf implements CharSequence {
        CharSequence origin;
        int s, e;

        public ReverseOf(CharSequence origin, int start, int end) {
            this.origin = origin;
            this.s = start;
            this.e = end;
        }

        public static CharSequence reverseOf(CharSequence sequence, int start, int end) {
            return new ReverseOf(sequence, start, end);
        }

        @Override
        public int length() {
            return e;
        }

        // abcdef
        //   |  |   2 -> 5
        // fedcba
        //   |  |   2 -> 5 (should be 0 -> 3)
        @Override
        public char charAt(int index) {
            return origin.charAt(e - index - s);
        }

        // abcdef
        //   |  |   2 -> 5
        // fedcba
        // |  |   0 -> 3 (should be 2 -> 5)
        @Override
        public CharSequence subSequence(int start, int end) {
            return new ReverseOf(origin, start + s, end + s);
        }

        @Override
        public String toString() {
            char[] reverse = new char[e - s];
            for (int i = 0; i < reverse.length; i++) {
                reverse[i] = origin.charAt(reverse.length - i);
            }
            return new String(reverse);
        }
    }
}
