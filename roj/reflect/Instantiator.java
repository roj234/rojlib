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

import roj.util.EmptyArrays;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

/**
 * @author Roj234
 * @since  2020/12/6 15:04
 */
public final class Instantiator {
    private interface H {
        Constructor<?>[] getDeclaredConstructors0(Class<?> clazz, boolean x);
        Object newInstance0(Constructor<?> constructor, Object[] params) throws
                InstantiationException,
                IllegalArgumentException,
                InvocationTargetException;
    }

    private static H U;
    private static boolean ok;

    public static void tryCache() throws Exception {
        if(ok) return;
        synchronized (H.class) {
            if (ok) return;
            U = DirectAccessor.builder(H.class)
                .delegate(Class.forName("sun.reflect.NativeConstructorAccessorImpl"), "newInstance0")
                .delegate(Class.class, "getDeclaredConstructors0").build();
            ok = true;
        }
    }

    public static Object _new(Class<?> clazz) throws ReflectiveOperationException {
        return _new(clazz, EmptyArrays.CLASSES, EmptyArrays.OBJECTS);
    }

    public static Object _new(Class<?> clazz, Class<?>[] p1, Object... o1) throws ReflectiveOperationException {
        try {
            tryCache();
        } catch (Throwable e) {
            e.printStackTrace();
        }

        Constructor<?> c = findConstructor(clazz, p1);

        if (U == null) {
            c.setAccessible(true);
            return c.newInstance(o1);
        } else {
            return U.newInstance0(c, o1);
        }
    }

    static Constructor<?> findConstructor(Class<?> clazz, Class<?>... pt) throws NoSuchMethodException {
        if (U != null) {
            for (Constructor<?> c : U.getDeclaredConstructors0(clazz, false)) {
                if (c.getParameterCount() == pt.length &&
                   (pt.length == 0 || Arrays.equals(pt, c.getParameterTypes()))) {
                    return c;
                }
            }
            throw new NoSuchMethodException();
        } else {
            return clazz.getConstructor(pt);
        }
    }
}
