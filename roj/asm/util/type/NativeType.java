package roj.asm.util.type;

import roj.collect.CharMap;
import roj.text.TextUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: NativeType.java
 */
public final class NativeType {
    public static final char
            ARRAY = '[', CLASS = 'L', VOID = 'V', BOOLEAN = 'Z', BYTE = 'B', CHAR = 'C', SHORT = 'S', INT = 'I', FLOAT = 'F', DOUBLE = 'D', LONG = 'J';

    private static final CharMap<String> toStringMap = new CharMap<>(16);

    static {
        final String s = "[LVZBCSIFDJ";
        final List<String> s1 = TextUtil.splitStringF(new ArrayList<>(), "array class void boolean byte char short int float double long", ' ');
        for (int i = 0; i < s.length(); i++) {
            toStringMap.put(s.charAt(i), s1.get(i));
        }
    }

    public static String toString(char type) {
        return toStringMap.get(type);
    }

    public static boolean isValidate(char c) {
        return toStringMap.containsKey(c);
    }

    public static char validate(char c) {
        if (!toStringMap.containsKey(c))
            throw new IllegalArgumentException("Illegal native type desc " + c);
        return c;
    }
}
