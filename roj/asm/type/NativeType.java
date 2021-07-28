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
package roj.asm.type;

import roj.collect.CharMap;
import roj.text.TextUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/18 9:51
 */
public final class NativeType {
    public static final char
            ARRAY = '[', CLASS = 'L', VOID = 'V', BOOLEAN = 'Z', BYTE = 'B', CHAR = 'C', SHORT = 'S', INT = 'I', FLOAT = 'F', DOUBLE = 'D', LONG = 'J';

    private static final CharMap<String> toNameMap = new CharMap<>(16), toDescMap = new CharMap<>(16);

    static {
        final String s = "[LVZBCSIFDJ";
        final List<String> s1 = TextUtil.split(new ArrayList<>(), "array class void boolean byte char short int float double long", ' ');
        for (int i = 0; i < s.length(); i++) {
            toNameMap.put(s.charAt(i), s1.get(i));
            toDescMap.put(s.charAt(i), String.valueOf(s.charAt(i)));
        }
    }

    public static String toDesc(char type) {
        return toDescMap.get(type);
    }

    public static String toString(char type) {
        return toNameMap.get(type);
    }

    public static boolean isValidate(char c) {
        return toDescMap.containsKey(c);
    }

    public static char validate(char c) {
        if (!toDescMap.containsKey(c))
            throw new IllegalArgumentException("Illegal native type desc " + c);
        return c;
    }
}
