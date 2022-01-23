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

import roj.text.CharList;
import roj.text.SimpleLineReader;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 后缀树，TT是前缀树
 *
 * @author Roj233
 * @since 2021/8/13 14:02
 */
public final class SuffixTree<V> extends TrieTree<V> {
    public static void main(String[] args) throws IOException {
        SuffixTree<Boolean> suffixTree = new SuffixTree<>();
        CharList tmp = new CharList();
        try (SimpleLineReader scan = new SimpleLineReader(new FileInputStream(args[0]))) {
            for (String ln : scan) {
                if(ln.isEmpty() || ln.startsWith("!"))
                    continue;

                tmp.clear();
                tmp.append(ln)
                   .replace("@@", "")
                   .replace("|", "")
                   .replace("^", "");
                suffixTree.put(tmp, true);
            }
        }
        if (!suffixTree.isEmpty()) {
            Predicate<String> predicate = s -> {
                for (int i = 0; i < s.length(); i++) {
                    if (suffixTree.containsKey(s, i, s.length())) {
                        return true;
                    }
                }
                return false;
            };
            System.out.println(predicate.test("www.ok.com"));
            System.out.println(predicate.test("r.sascdn.com"));
            System.out.println(predicate.test("asdsar.sascdn.com"));
        }
    }

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
            return e - s;
        }

        // abcdef
        //   |  |   2 -> 5
        // fedcba
        // |  |     0 -> 3 == 5 -> 2
        @Override
        public char charAt(int index) {
            return origin.charAt((e - s - 1) - index);
        }

        // fedcba
        //  | |   1 -> 3 == 4 -> 2
        @Override
        public CharSequence subSequence(int start, int end) {
            CharSequence v = origin.subSequence((e - s - 1) - end, (e - s - 1) - start);
            return new ReverseOf(v, 0, v.length());
        }

        @Override
        public String toString() {
            char[] reverse = new char[e - s];
            int j = s + reverse.length;
            for (int i = 0; i < reverse.length; i++) {
                reverse[i] = origin.charAt(--j);
            }
            return new String(reverse);
        }
    }
}
