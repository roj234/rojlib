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

import java.util.List;

/**
 * 更快速的占位符,比起Oracle JVM 的 {@link String#replace(CharSequence, CharSequence)} <Br>
 *     不管怎么样，比 "platform-dependent" 要好, 不是么
 *
 * @author Roj233
 * @version 0.1
 * @since 2021/8/26 23:15
 */
public class Unplaceholder {
    public final String value;
    private final char[] points;
    private final CharList tmp;

    Unplaceholder(String value, char[] points) {
        this.value = value;
        this.points = points;
        this.tmp = new CharList(value.length());
    }

    public static Unplaceholder assign(char begin, char end, String val) {
        CharList list = new CharList();

        int off = 0;
        while ((off = val.indexOf(begin, off)) != -1) {
            if (val.charAt(off + 1) == end) {
                list.append((char) off++);
            }
            off++;
        }

        return new Unplaceholder(val, list.toCharArray());
    }

    public String replace(List<String> replacer) throws ArgumentMissingException {
        tmp.clear();
        CharList sb = tmp.append(value);
        int off = 0;
        for (char str : points) {
            sb.replace(str + off, str + off + 2, value);
            off += (value.length() - 2);
        }
        return sb.toString();
    }
}
