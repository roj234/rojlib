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

package roj.reflect;

import roj.asm.type.NativeType;
import roj.asm.type.ParamHelper;

import java.lang.reflect.Field;

import static roj.asm.type.NativeType.ARRAY;
import static roj.asm.type.NativeType.CLASS;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/18 9:51
 */
@Deprecated
public class DirectFieldAccess {
    public static DirectFieldAccessor get(Class<?> obj, String field) {
        try {
            return get(obj, obj.getDeclaredField(field));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    public static DirectFieldAccessor get(Object obj, java.lang.reflect.Field field) {
        return get(obj, field, DirectFieldAccessor.class);
    }

    public static <T extends Instanced> T get(Object obj, java.lang.reflect.Field field, Class<T> getter_setter_class) {
        Class<?> cache = obj.getClass() == Class.class ? ((Class<?>) obj) : obj.getClass();
        return DirectAccessor
                .builder(getter_setter_class)
                .makeCache(cache)
                .access(cache,
                        new String[]{ field.getName() }, new String[]{ "get" + accessorName(field) }, new String[]{"set" + accessorName(field) }, true)
                .build();
    }

    public static String accessorName(Field field) {
        char c = ParamHelper.classDescriptor(field.getType()).charAt(0);
        switch (c) {
            case ARRAY:
            case CLASS:
                return "Object";
            default:
                StringBuilder s = new StringBuilder(NativeType.toDesc(c));
                s.setCharAt(0, Character.toUpperCase(s.charAt(0)));
                return s.toString();
        }
    }
}