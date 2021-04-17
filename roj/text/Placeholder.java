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
package roj.text;

import roj.collect.IntList;
import roj.collect.MyHashMap;

import java.util.Map;
import java.util.function.Function;

/**
 * 更快速的占位符,比起Oracle JVM 的 {@link String#replace(CharSequence, CharSequence)} <Br>
 *     不管怎么样，比 "platform-dependent" 要好, 不是么
 *
 * @author Roj233
 * @version 0.1
 * @since 2021/8/25 16:20
 */
public final class Placeholder {
    static final Function<String, IntList> NEW_INT_LIST = (x) -> new IntList();

    public final String value;
    public final P[] points;

    static final class P {
        final String key;
        // value 22位放offset，10位放length
        final int[] val;

        P(String key, int[] array) {
            this.key = key;
            this.val = array;
        }
    }

    private Placeholder(String value, P[] points) {
        this.value = value;
        this.points = points;
    }

    public static Placeholder assign(char begin, char end, String val) throws IllegalArgumentException {
        MyHashMap<String, IntList> map = new MyHashMap<>();

        int off = 0, offe;
        while ((off = val.indexOf(begin, off)) != -1) {
            if ((offe = val.indexOf(end, off)) != -1) {
                map.computeIfAbsent(val.substring(off + 1, offe), NEW_INT_LIST).add((off << 10) | ((offe - off + 1) & 1023));
                off = offe + 1;
            } else {
                break;
            }
        }
        P[] ps = new P[map.size()];
        int i = 0;
        for (Map.Entry<String, IntList> entry : map.entrySet()) {
            ps[i++] = new P(entry.getKey(), entry.getValue().toArray());
        }

        return new Placeholder(val, ps);
    }

    public String replace(Map<String, String> replacer) throws ArgumentMissingException {
        CharList sb = new CharList().append(value);
        int str, len, off = 0;
        for (P entry : points) {
            String value = replacer.get(entry.key);
            if (value == null) {
                throw new ArgumentMissingException(entry.key);
            }

            for (int i : entry.val) {
                str = i >>> 10;
                len = i & 1023;
                sb.replace(str + off, str + off + len, value);
                off += (value.length() - len);
            }
        }
        return sb.toString();
    }
}
