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
import roj.collect.IntMap;
import roj.math.MathUtils;

import java.util.List;
import java.util.function.IntFunction;

/**
 * 更快速的占位符,比起Oracle JVM 的 {@link String#replace(CharSequence, CharSequence)} <Br>
 *     不管怎么样，比 "platform-dependent" 要好, 不是么
 *
 * @author Roj233
 * @version 0.1
 * @since 2021/8/26 23:08
 */
public class Argholder {
    static final IntFunction<IntList> NEW_INT_LIST = (x) -> new IntList();

    public final String value;
    // value 22位放offset，10位放length
    private final int[][] points;
    private final CharList tmp;

    Argholder(String value, int[][] points) {
        this.value = value;
        this.points = points;
        this.tmp = new CharList(value.length());
    }

    public static Argholder assign(char begin, char end, String val) throws IllegalArgumentException {
        IntMap<IntList> map = new IntMap<>();

        int off = 0, offe;
        while ((off = val.indexOf(begin, off)) != -1) {
            if ((offe = val.indexOf(end, off)) != -1) {
                map.computeIfAbsent(MathUtils.parseInt(val, off + 1, offe, 10), NEW_INT_LIST).add(off << 10 | ((offe - off + 1) & 1023));
                off = offe + 1;
            } else {
                break;
            }
        }
        int[][] arr = new int[map.size()][];
        for (int i = 0; i < map.size(); i++) {
            IntList list = map.get(i);
            if(list == null)
                throw new IllegalArgumentException("不连续的key: " + i);
            arr[i] = list.toArray();
        }

        return new Argholder(val, arr);
    }

    public String replace(List<String> replacer) throws ArgumentMissingException {
        tmp.clear();
        CharList sb = tmp.append(value);
        int str, len, off = 0;
        for (int j = 0; j < points.length; j++) {
            int[] entry = points[j];
            String value = replacer.get(j);
            if (value == null) {
                throw new ArgumentMissingException("replacer[" + j + "]");
            }

            for (int i : entry) {
                str = i >>> 10;
                len = i & 1023;
                sb.replace(str + off, str + off + len, value);
                off += (value.length() - len);
            }
        }
        return sb.toString();
    }
}
