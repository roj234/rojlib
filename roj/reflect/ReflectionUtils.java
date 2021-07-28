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

import roj.collect.EmptyList;
import roj.concurrent.OperationDone;
import roj.concurrent.Ref;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/17 19:51
 */
public final class ReflectionUtils {

    //private static final J8Util.UFA modifiersField;
    /*private static final Method ACCESSOR_GETTER;

    static {
        //final String MODIFIERS_FIELD = "modifiers";
        try {
            //Field modifiers = Field.class.getDeclaredField(MODIFIERS_FIELD);
            //modifiers.setAccessible(true);
            //modifiersField = J8Util.getUnsafeFieldAccessor(modifiers);
            ACCESSOR_GETTER = getMethod(Field.class, "getFieldAccessor", false, Object.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }*/

    /**
     * 将old的field复制到now
     *
     * @param old        old
     * @param now        new
     * @param fieldNames Field names to copy
     */
    @Deprecated
    public static <T> void dataCopy(T old, T now, String[] fieldNames) throws NoSuchFieldException, IllegalAccessException {
        if (fieldNames == null) {
            List<Field> list = getFields(old.getClass());
            for (Field field : list) {
                Object value = field.get(old);
                setFinal(now, field, value);
            }
        } else {
            for (String name : fieldNames) {
                Object value = getValue(old, name);
                setFinal(now, getField(now.getClass(), name), value);
            }
        }
    }

    /**
     * 获取构造器
     */
    public static Constructor<?> getConstructor(Class<?> clazz, boolean isPublic, Class<?>... param) throws NoSuchMethodException {
        Constructor<?> var3 = isPublic ? clazz.getConstructor(param) : clazz.getDeclaredConstructor(param);
        var3.setAccessible(true);
        return var3;
    }

    /**
     * 在obj中查找类型为targetClass的field
     *
     * @param obj         The class to find field
     * @param targetClass The field class to find
     */
    public static Field getFieldValueByType(Class<?> obj, Class<?> targetClass) {
        for (Field f : getFields(obj)) {

            Class<?> tempClass = f.getType();
            while (tempClass != null) {
                if (tempClass == targetClass) {
                    f.setAccessible(true);
                    return f;
                }
                tempClass = tempClass.getSuperclass();
            }
        }
        return null;
    }

    /**
     * 获取current + 父类 所有Field
     */
    public static List<Field> getFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        while (clazz != null) {
            Collections.addAll(fields, clazz.getDeclaredFields());
            clazz = clazz.getSuperclass();
        }
        return fields;
    }

    public static void consumeFields(Class<?> clazz, Consumer<Field> consumer) {
        while (clazz != null) {
            for (Field field : clazz.getDeclaredFields())
                consumer.accept(field);
            clazz = clazz.getSuperclass();
        }
    }

    /**
     * 获取current + 父类 所有Method
     */
    public static List<Method> getMethods(Class<?> clazz) {
        List<Method> methods = new ArrayList<>();
        while (clazz != null) {
            Collections.addAll(methods, clazz.getDeclaredMethods());
            clazz = clazz.getSuperclass();
        }
        return methods;
    }

    public static void consumeMethods(Class<?> clazz, Consumer<Method> consumer) {
        while (clazz != null) {
            for (Method method : clazz.getDeclaredMethods())
                consumer.accept(method);
            clazz = clazz.getSuperclass();
        }
    }

    /**
     * 实例化对象
     */
    public static Object instantiateObject(Class<?> clazz, boolean isPublic, Class<?>[] classes, Object[] paramValues) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        return getConstructor(clazz, isPublic, classes).newInstance(paramValues);
    }


    /**
     * 获取方法
     */
    @Nonnull
    public static Method getMethod(@Nonnull Class<?> clazz, @Nonnull String name, @Nullable Class<?>... param) throws NoSuchMethodException {
        if (param == null)
            param = EmptyList.EMPTY_C;

        Class<?>[] finalParam = param;

        Ref<Method> ref = Ref.from();
        try {
            consumeMethods(clazz, method -> {
                if (method.getName().equals(name) && method.getParameterCount() == finalParam.length) {
                    Class<?>[] arr = method.getParameterTypes();
                    for (int i = 0; i < finalParam.length; i++) {
                        if (arr[i] != finalParam[i])
                            return;
                    }
                    ref.set(method);
                    throw OperationDone.INSTANCE;
                }
            });
        } catch (OperationDone ex) {
            ref.get().setAccessible(true);
            return ref.get();
        }

        throw new NoSuchMethodException(clazz.getName() + '.' + name);
    }

    /**
     * 调用无参私有方法
     */
    public static Object invokeMethod(@Nonnull Object obj, @Nonnull String name) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        return getMethod(obj.getClass(), name, (Class<?>) null).invoke(obj, EmptyList.EMPTY);
    }

    /**
     * 调用有参私有方法
     */
    public static Object invokeMethod(Object obj, String name, @Nullable Class<?>[] param, @Nullable Object[] paramValue) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        return getMethod(obj.getClass(), name, param).invoke(obj, paramValue == null ? EmptyList.EMPTY : paramValue);
    }

    /**
     * 获取字段
     */
    public static Field getField(Class<?> clazz, String name) throws NoSuchFieldException {
        Ref<Field> ref = Ref.from();
        try {
            //Class<?> type = null;
            consumeFields(clazz, field -> {
                if (field.getName().equals(name) /*&& (type == null || field.getType() == type)*/) {
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
     * 设置本类字段值
     *
     * @param instance 实例or class
     * @param name     name
     * @param value    new value
     */
    public static void setValue(@Nonnull Object instance, String name, Object value) throws NoSuchFieldException, IllegalAccessException {
        if (instance.getClass() == Class.class) {
            setValue(null, (Class<?>) instance, name, value);
        } else {
            setValue(instance, instance.getClass(), name, value);
        }
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

    /**
     * 警告: 需要事先知道类型
     * 警告：直接调用native代码
     * 警告：不能在java9使用
     *
     * @see DirectFieldAccess
     */
    @Deprecated
    public static IFieldAccessor fasterField(Field field) {
        return unsafeAccIfPresent(field);
    }

    public static void setFinal(Object o, Field field, Object value) {
        // 获得 public 权限(绕过权限检测)
        //field.setAccessible(true);

        // 将modifiers域设为非final,这样就可以修改了

        //modifiersField.setInstance(field);
        //int modifiers = modifiersField.getInt();
        // 去掉 final 标志位
        //modifiers &= ~Modifier.FINAL;
        //modifiersField.setInt(modifiers);

        //FieldAccessor fa = ;
        //fastField(field, o).set(o, value);

        IFieldAccessor acc = unsafeAccIfPresent(field);
        acc.setInstance(o);
        acc.setObject(value);
    }

    public static IFieldAccessor unsafeAccIfPresent(@Nonnull Field field) {
        try {
            return new U.UFA(field);
        } catch (Throwable e) {
            return new FDA(field);
        }
    }

    public static List<Class<?>> getFathers(Object target) {
        return getFathers(target instanceof Class ? (Class<?>) target : target.getClass());
    }

    public static List<Class<?>> getFathers(Class<?> clazz) {
        List<Class<?>> classes = new ArrayList<>();
        while (clazz != null) {
            classes.add(clazz);
            Collections.addAll(classes, clazz.getInterfaces());
            clazz = clazz.getSuperclass();
        }
        return classes;
    }

    public static List<Class<?>> getFathersAndItfOrdered(Class<?> clazz) {
        ArrayList<Class<?>> classes = new ArrayList<>();
        MyList<Class<?>> pending = new MyList<>();
        pending.add(clazz);

        while (!pending.isEmpty()) {
            int size = pending.size();
            for (int i = 0; i < size; i++) {
                Class<?> c = pending.get(i);
                if(!classes.contains(c)) {
                    classes.add(c);

                    for (Class<?> itf : c.getInterfaces()) {
                        pending.add(itf);
                    }
                    Class<?> s = c.getSuperclass();
                    if(s != null)
                        pending.add(s);
                }
            }
            pending.removeRange(0, size);
        }
        return classes;
    }

    private static final class FDA extends IFieldAccessor {
        public FDA(Field field) {
            super(field);
            field.setAccessible(true);
        }

        @Override
        public Object getObject() {
            try {
                return field.get(instance);
            } catch (IllegalAccessException e) {
                throw OperationDone.NEVER;
            }
        }

        @Override
        public boolean getBoolean() {
            try {
                return field.getBoolean(instance);
            } catch (IllegalAccessException e) {
                throw OperationDone.NEVER;
            }
        }

        @Override
        public byte getByte() {
            try {
                return field.getByte(instance);
            } catch (IllegalAccessException e) {
                throw OperationDone.NEVER;
            }
        }

        @Override
        public char getChar() {
            try {
                return field.getChar(instance);
            } catch (IllegalAccessException e) {
                throw OperationDone.NEVER;
            }
        }

        @Override
        public short getShort() {
            try {
                return field.getShort(instance);
            } catch (IllegalAccessException e) {
                throw OperationDone.NEVER;
            }
        }

        @Override
        public int getInt() {
            try {
                return field.getInt(instance);
            } catch (IllegalAccessException e) {
                throw OperationDone.NEVER;
            }
        }

        @Override
        public long getLong() {
            try {
                return field.getLong(instance);
            } catch (IllegalAccessException e) {
                throw OperationDone.NEVER;
            }
        }

        @Override
        public float getFloat() {
            try {
                return field.getFloat(instance);
            } catch (IllegalAccessException e) {
                throw OperationDone.NEVER;
            }
        }

        @Override
        public double getDouble() {
            try {
                return field.getDouble(instance);
            } catch (IllegalAccessException e) {
                throw OperationDone.NEVER;
            }
        }

        @Override
        public void setObject(Object obj) {
            try {
                field.set(instance, obj);
            } catch (IllegalAccessException e) {
                throw OperationDone.NEVER;
            }
        }

        @Override
        public void setBoolean(boolean value) {
            try {
                field.setBoolean(instance, value);
            } catch (IllegalAccessException e) {
                throw OperationDone.NEVER;
            }
        }

        @Override
        public void setByte(byte value) {

            try {
                field.setByte(instance, value);
            } catch (IllegalAccessException e) {
                throw OperationDone.NEVER;
            }
        }

        @Override
        public void setChar(char value) {

            try {
                field.setChar(instance, value);
            } catch (IllegalAccessException e) {
                throw OperationDone.NEVER;
            }
        }

        @Override
        public void setShort(short value) {

            try {
                field.setShort(instance, value);
            } catch (IllegalAccessException e) {
                throw OperationDone.NEVER;
            }
        }

        @Override
        public void setInt(int value) {

            try {
                field.setInt(instance, value);
            } catch (IllegalAccessException e) {
                throw OperationDone.NEVER;
            }
        }

        @Override
        public void setLong(long value) {

            try {
                field.setLong(instance, value);
            } catch (IllegalAccessException e) {
                throw OperationDone.NEVER;
            }
        }

        @Override
        public void setFloat(float value) {

            try {
                field.setFloat(instance, value);
            } catch (IllegalAccessException e) {
                throw OperationDone.NEVER;
            }
        }

        @Override
        public void setDouble(double value) {

            try {
                field.setDouble(instance, value);
            } catch (IllegalAccessException e) {
                throw OperationDone.NEVER;
            }
        }
    }

    private static class MyList<T> extends ArrayList<T> {
        static final long serialVersionUID = 1;

        @Override
        public void removeRange(int fromIndex, int toIndex) {
            super.removeRange(fromIndex, toIndex);
        }
    }
}
