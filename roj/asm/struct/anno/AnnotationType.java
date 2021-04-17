/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: AnnotationParamType.java
 */
package roj.asm.struct.anno;

import roj.collect.CharMap;
import roj.text.TextUtil;

import java.util.ArrayList;
import java.util.List;

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