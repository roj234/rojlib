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
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/12/6 15:04
 */
public final class InstantiationUtil {
    private InstantiationUtil() {
    }

    private static final Object[] FIND = new Object[]{
            Boolean.FALSE
    };

    private static final Object[] CONST = new Object[]{
            null,
            EmptyArrays.OBJECTS
    };

    private static java.lang.reflect.Method newInstance0;
    private static java.lang.reflect.Method getDeclaredConstructors0;

    public static synchronized void doSunReflectCache() throws Exception {
        if(newInstance0 != null) return;
        try {
            Class<?> clazz = Class.forName("sun.reflect.NativeConstructorAccessorImpl");
            java.lang.reflect.Method method = clazz.getDeclaredMethod("newInstance0", Constructor.class, Object[].class);
            method.setAccessible(true);
            newInstance0 = method;
            clazz = Class.class;
            method = clazz.getDeclaredMethod("getDeclaredConstructors0", boolean.class);
            method.setAccessible(true);
            getDeclaredConstructors0 = method;
        } catch (Throwable e) {
            throw new Exception(e);
        }
    }

    public static Object createClass(Class<?> clazz) throws IllegalAccessException, InvocationTargetException, InstantiationException {
        Constructor<?> constructor = findConstructor(clazz, EmptyArrays.CLASSES);
        if (newInstance0 == null) {
            constructor.setAccessible(true);
            return constructor.newInstance();
        } else {
            synchronized (CONST) {
                CONST[0] = constructor;
                CONST[1] = EmptyArrays.OBJECTS;
                return newInstance0.invoke(null, CONST);
            }
        }
    }

    public static Object createClass(Class<?> clazz, Class<?>[] p1, Object... o1) throws IllegalAccessException, InvocationTargetException, InstantiationException {
        Constructor<?> constructor = findConstructor(clazz, p1);

        if (newInstance0 == null) {
            constructor.setAccessible(true);
            return constructor.newInstance(o1);
        } else {
            synchronized (CONST) {
                CONST[0] = constructor;
                CONST[1] = o1;
                Object inst = newInstance0.invoke(null, CONST);
                CONST[1] = EmptyArrays.OBJECTS;
                return inst;
            }
        }
    }

    static Constructor<?> findConstructor(Class<?> clazz, Class<?>... types) throws IllegalAccessException, InvocationTargetException {
        if (getDeclaredConstructors0 != null) {
            Constructor<?>[] constructors = (Constructor<?>[]) getDeclaredConstructors0.invoke(clazz, FIND);
            for (Constructor<?> constructor : constructors) {
                if (constructor.getParameterCount() == types.length && (types.length == 0 || Arrays.equals(types, constructor.getParameterTypes()))) {
                    return constructor;
                }
            }
        }
        try {
            return clazz.getConstructor(types);
        } catch (NoSuchMethodException e) {
            throw new InvocationTargetException(e);
        }
    }
}
