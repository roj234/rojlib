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
import roj.asm.util.AccessFlag;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.concurrent.OperationDone;
import roj.concurrent.Ref;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.WeakHashMap;
import java.util.function.Consumer;

import static roj.asm.type.NativeType.ARRAY;
import static roj.asm.type.NativeType.CLASS;

/**
 * @author Roj234
 * @since 2021/6/17 19:51
 */
public final class ReflectionUtils {
    /**
     * 在obj中查找类型为targetClass的field
     *
     * @param obj         The class to find field
     * @param targetClass The field class to find
     */
    public static Field getFieldValueByType(Class<?> obj, Class<?> targetClass) {
        for (Field f : getFields(obj)) {

            Class<?> tmp = f.getType();
            while (tmp != Object.class) {
                if (tmp == targetClass) {
                    f.setAccessible(true);
                    return f;
                }
                tmp = tmp.getSuperclass();
            }
        }
        return null;
    }

    /**
     * 获取current + 父类 所有Field
     */
    public static List<Field> getFields(Class<?> clazz) {
        MyHashSet<Field> fields = new MyHashSet<>();
        while (clazz != Object.class) {
            Collections.addAll(fields, clazz.getDeclaredFields());
            clazz = clazz.getSuperclass();
        }
        return new ArrayList<>(fields);
    }

    public static void consumeFields(Class<?> clazz, Consumer<Field> consumer) {
        while (clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields())
                consumer.accept(field);
            clazz = clazz.getSuperclass();
        }
    }

    /**
     * 获取current + 父类 所有Method
     */
    public static List<Method> getMethods(Class<?> clazz) {
        MyHashSet<Method> methods = new MyHashSet<>();
        while (clazz != Object.class) {
            methods.addAll(clazz.getDeclaredMethods());
            clazz = clazz.getSuperclass();
        }
        return new ArrayList<>(methods);
    }

    /**
     * 获取字段
     */
    public static Field getField(Class<?> clazz, String name) throws NoSuchFieldException {
        Ref<Field> ref = Ref.from();
        try {
            consumeFields(clazz, field -> {
                if (field.getName().equals(name)) {
                    ref.set(field);
                    throw OperationDone.INSTANCE;
                }
            });
        } catch (OperationDone ex) {
            ref.get().setAccessible(true);
            return ref.get();
        }

        throw new NoSuchFieldException(clazz.getName() + '.' + name);
    }

    /**
     * 获取字段值
     *
     * @param instance 实例
     * @param clazz    实例类
     * @param name     name
     * @return value
     */
    @SuppressWarnings("unchecked")
    public static <T> T getValue(Object instance, Class<?> clazz, String name) {
        try {
            return (T) getField(clazz, name).get(instance);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 获取本类字段值
     *
     * @param instance 实例
     * @param name     name
     * @return value
     */
    public static <T> T getValue(Object instance, String name) {
        return getValue(instance, instance.getClass(), name);
    }

    /**
     * 设置字段值
     *
     * @param instance 实例
     * @param name     name
     * @param value    new value
     */
    public static void setValue(Object instance, @Nonnull Class<?> clazz, String name, Object value) throws NoSuchFieldException, IllegalAccessException {
        getField(clazz, name).set(instance, value);
    }

    /**
     * 设置static final字段值
     *
     * @param clazz     class
     * @param fieldName name
     * @param value     new value
     */
    public static void setFinal(Class<?> clazz, String fieldName, Object value) throws NoSuchFieldException {
        setFinal(null, clazz, fieldName, value);
    }

    /**
     * 设置final字段值
     *
     * @param o         instance of this
     * @param fieldName name
     * @param value     new value
     */
    public static void setFinal(@Nonnull Object o, String fieldName, Object value) throws NoSuchFieldException {
        setFinal(o, getField(o.getClass(), fieldName), value);
    }

    /**
     * 设置final字段值
     *
     * @param o         instance
     * @param clazz     class
     * @param fieldName name
     * @param value     new value
     */
    public static void setFinal(Object o, Class<?> clazz, String fieldName, Object value)
            throws NoSuchFieldException {
        setFinal(o, getField(clazz, fieldName), value);
    }

    /**
     * 设置final字段值
     *
     * @param field The field
     * @param value new value
     */
    public static void setFinal(Field field, Object value) {
        setFinal(null, field, value);
    }

    public static void setFinal(Object o, Field field, Object value) {
        FieldAccessor acc = access(field);
        acc.setInstance(o);
        acc.setObject(value);
    }

    private static final WeakHashMap<Field, FieldAccessor> accessors = new WeakHashMap<>();
    public static FieldAccessor access(@Nonnull Field field) {
        FieldAccessor acc = accessors.get(field);
        if(acc != null)
            return acc;
        try {
            return new UFA(field);
        } catch (Throwable e) {
            if((field.getModifiers() & AccessFlag.FINAL) == 0) {
                acc = DirectAccessor
                        .builder(FieldAccessor.class)
                        .makeCache(field.getDeclaringClass())
                        .useCache()
                        .access(field.getDeclaringClass(), field.getName(),
                                "get" + accessorName(field),
                                "set" + accessorName(field))
                        .build();
            } else {
                acc = new VHFA(field);
            }
            e.printStackTrace();
            accessors.put(field, acc);
            return acc;
        }
    }

    public static List<Class<?>> getFathers(Object target) {
        return getFathers(target instanceof Class ? (Class<?>) target : target.getClass());
    }

    public static List<Class<?>> getFathers(Class<?> clazz) {
        List<Class<?>> classes = new ArrayList<>();
        while (clazz != Object.class) {
            classes.add(clazz);
            Collections.addAll(classes, clazz.getInterfaces());
            clazz = clazz.getSuperclass();
        }
        return classes;
    }

    public static List<Class<?>> getFathersAndItfOrdered(Class<?> clazz) {
        ArrayList<Class<?>> classes = new ArrayList<>();
        SimpleList<Class<?>> pending = new SimpleList<>();
        pending.add(clazz);

        while (!pending.isEmpty()) {
            int size = pending.size();
            for (int i = 0; i < size; i++) {
                Class<?> c = pending.get(i);
                if(!classes.contains(c)) {
                    classes.add(c);

                    Collections.addAll(pending, c.getInterfaces());
                    Class<?> s = c.getSuperclass();
                    if(s != Object.class && s != null)
                        pending.add(s);
                }
            }
            pending.removeRange(0, size);
        }
        return classes;
    }

    public static String accessorName(Field field) {
        char c = ParamHelper.classDescriptor(field.getType()).charAt(0);
        switch (c) {
            case ARRAY:
            case CLASS:
                return "Object";
            default:
                StringBuilder s = new StringBuilder(NativeType.toString((byte) c));
                s.setCharAt(0, Character.toUpperCase(s.charAt(0)));
                return s.toString();
        }
    }
}
