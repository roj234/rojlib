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

package roj.asm.struct.anno;

import roj.collect.CharMap;
import roj.text.TextUtil;

import java.util.ArrayList;
import java.util.List;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/1/3 15:59
 */
public final class AnnotationType {
    public static final char BYTE = 'B', CHAR = 'C', DOUBLE = 'D', FLOAT = 'F', INT = 'I', LONG = 'J', SHORT = 'S', BOOLEAN = 'Z', STRING = 's',
            ENUM = 'e', CLASS = 'c', ANNOTATION = '@', ARRAY = '[';

    public static final CharMap<String> toStringMap = new CharMap<>(16);

    static {
        final String s = "BCDFIJSZsec@[";
        final List<String> s1 = TextUtil.splitStringF(new ArrayList<>(), "byte char double float int long short boolean string enum class annotation array", ' ');
        for (int i = 0; i < s.length(); i++) {
            toStringMap.put(s.charAt(i), s1.get(i));
        }
    }

    public static char verify(short c) {
        if (!toStringMap.containsKey((char) c))
            throw new IllegalArgumentException("Unknown annotation type '" + c + '\'');
        return (char) c;
    }

    public static String toString(char c) {
        return toStringMap.get(c);
    }
}