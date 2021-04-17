package roj.reflect;

import roj.collect.EmptyList;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/12/6 15:04
 */
public final class SunReflection {
    private SunReflection() {
    }

    private static final Object[] FIND = new Object[]{
            Boolean.FALSE
    };

    private static final Object[] CONST = new Object[]{
            null,
            new Object[0]
    };

    private static java.lang.reflect.Method newInstance0;
    //private static java.lang.reflect.Method invoke0;
    private static java.lang.reflect.Method getDeclaredConstructors0;

    public static void doSunReflectCache() throws Exception {
        try {
            Class<?> clazz = Class.forName("sun.reflect.NativeConstructorAccessorImpl");
            java.lang.reflect.Method method = clazz.getDeclaredMethod("newInstance0", Constructor.class, Object[].class);
            method.setAccessible(true);
            newInstance0 = method;
            //clazz = Class.forName("sun.reflect.NativeMethodAccessorImpl");
            //method = clazz.getDeclaredMethod("invoke0", java.lang.reflect.Method.class, Object.class, Object[].class);
            //method.setAccessible(true);
            //invoke0 = method;
            clazz = Class.class;
            method = clazz.getDeclaredMethod("getDeclaredConstructors0", boolean.class);
            method.setAccessible(true);
            getDeclaredConstructors0 = method;
        } catch (Throwable e) {
            throw new Exception(e);
        }
    }

    public static Object createClass(Class<?> clazz) throws IllegalAccessException, InvocationTargetException, InstantiationException {
        Constructor<?> constructor = findConstructor(clazz, EmptyList.EMPTY_C);
        if (newInstance0 == null) {
            constructor.setAccessible(true);
            return constructor.newInstance();
        } else {
            synchronized (CONST) {
                CONST[0] = constructor;
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
                Object data = CONST[1];
                CONST[1] = o1;
                Object inst = newInstance0.invoke(null, CONST);
                CONST[1] = data;
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
        } catch (NoSuchMethodException ignored) {
        }
        throw new InvocationTargetException(new NoSuchMethodError(clazz.getName() + ".<init>()V"));
    }
}
