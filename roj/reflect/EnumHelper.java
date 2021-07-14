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
import roj.collect.MyHashMap;
import roj.util.Helpers;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.*;

/**
 * 动态修改Enum <BR>
 *     推荐preload
 *//**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/2 8:22
 */
public final class EnumHelper<E extends Enum<E>> {
    private static final Field ORDINAL_FIELD;
    private static final DirectFieldAccessor mapSet;

    static {
        Field fl = null;
        try {
            fl = Enum.class.getDeclaredField("ordinal");
            fl.setAccessible(true);
        } catch (NoSuchFieldException ignored) {
        }
        ORDINAL_FIELD = fl;

        DirectFieldAccessor ecd = null;
        try {
            ecd = DirectFieldAccess.get(Class.class, Class.class.getDeclaredField("enumConstantDirectory"));
        } catch (NoSuchFieldException ignored) {}
        mapSet = ecd;
    }

    private final Class<E> clazz;
    private final Field[] fields;
    private Field values;
    private final Collection<Field> switchFields;
    private final Deque<UndoInfo<E>> undoStack = new LinkedList<>();

    private Class<?>[] lastAdditionalTypes;
    private Constructor<?> lastConstructor;

    public String valueName = "$VALUES";

    /**
     * Switch uses ordinal to decide enum;
     */
    public EnumHelper(Class<E> clazz, Class<?>... switchUsers) {
        this.clazz = clazz;
        this.fields = clazz.getDeclaredFields();
        if (!clazz.isEnum())
            throw new IllegalArgumentException("Not an enum");
        try {
            this.switchFields = findSwitchMaps(switchUsers);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not create the class", e);
        }
    }

    public E make(String value, int ordinal) {
        return make(value, ordinal, EmptyList.EMPTY_C, EmptyList.EMPTY);
    }

    public E make(String value, int ordinal, Class<?>[] additionalTypes, Object[] additional) {
        try {
            undoStack.push(new UndoInfo<>(this));

            Constructor<?> cst;
            if(Arrays.equals(additionalTypes, lastAdditionalTypes)) {
                cst = lastConstructor;
            } else {
                lastConstructor = cst = findConstructor(additionalTypes, clazz);
                lastAdditionalTypes = additionalTypes;
            }

            return construct(clazz, cst, value, ordinal, additional);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Could not create enum", e);
        }
    }

    /**
     * Add enum instance, overwrite if exists.
     * <p/>
     * Overwrite:
     * Replace constant field and array.
     * <p/>
     * The ordinal will be set.
     * <p/>
     * Warning: This should probably never be called,
     * since it can cause permanent changes to the enum
     * values.  Use only in extreme conditions.
     */
    public void add(E e) {
        if (e == null)
            throw new NullPointerException();

        try {
            undoStack.push(new UndoInfo<>(this));

            Field vf = findValuesField(valueName);

            E[] values = values();
            for (int i = 0; i < values.length; i++) {
                E value = values[i];

                if (value.name().equals(e.name())) {
                    ORDINAL_FIELD.set(e, value.ordinal());
                    values[i] = e;
                    replace(e.name(), e);

                    return;
                }
            }

            E[] newValues = Arrays.copyOf(values, values.length + 1);
            newValues[newValues.length - 1] = e;
            ReflectionUtils.setFinal(vf, newValues);

            int ordinal = newValues.length - 1;
            ORDINAL_FIELD.set(e, ordinal);

            addSwitch();
        } catch (ReflectiveOperationException ex) {
            throw new IllegalArgumentException("Could not set the enum", ex);
        }
    }

    private Field findValuesField(String valueId) {
        if (values == null) {
            // the values in the enum class
            for (Field field : fields) {
                if (field.getName().equals(valueId)) {
                    values = field;
                    break;
                }
            }
            // we mark it to be public
            values.setAccessible(true);
        }
        return values;
    }

    /**
     * !Set constant to null.
     */
    public boolean delete(E e) {
        if (e == null)
            throw new NullPointerException();

        try {
            undoStack.push(new UndoInfo<>(this));

            E[] values = values();
            for (int i = 0; i < values.length; i++) {
                E value = values[i];

                if (value.name().equals(e.name())) {
                    E[] newValues = Arrays.copyOf(values, values.length - 1);
                    System.arraycopy(values, i + 1, newValues, i, values.length - i - 1);

                    for (int j = i; j < newValues.length; j++) {
                        ORDINAL_FIELD.set(newValues[j], j);
                    }

                    ReflectionUtils.setFinal(this.values, newValues);
                    removeSwitch(i);
                    replace(e.name(), null);

                    return true;
                }
            }
        } catch (ReflectiveOperationException ex) {
            throw new IllegalArgumentException("Could not set the enum", ex);
        }

        return false;
    }

    public void restore() {
        UndoInfo<E> info = undoStack.peekLast();
        if (info != null) {
            try {
                info.undo();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
            undoStack.clear();
        }
    }

    public boolean undo() {
        UndoInfo<E> info = undoStack.poll();
        if (info == null) {
            return false;
        }

        try {
            info.undo();
            return true;
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private Constructor<?> findConstructor(Class<?>[] add, Class<E> clazz) throws NoSuchMethodException {
        Class<?>[] paramType = new Class<?>[add.length + 2];
        paramType[0] = String.class;
        paramType[1] = int.class;
        if (add.length > 0)
            System.arraycopy(add, 0, paramType, 2, add.length);

        return clazz.getDeclaredConstructor(paramType);
    }

    private E construct(Class<E> clazz, Constructor<?> cst, String value, int ordinal, Object[] add) throws ReflectiveOperationException {
        Object[] param = new Object[add.length + 2];
        param[0] = value;
        param[1] = ordinal;
        if (add.length > 0)
            System.arraycopy(add, 0, param, 2, add.length);

        E cast = clazz.cast(cst.newInstance(param));

        mapSet.setInstance(clazz);
        if(mapSet.getObject() != null) {
            Map<String, E> map = Helpers.cast(mapSet.getObject());
            map.put(value, cast);
        }

        return cast;
    }

    private void replace(String name, Object val) {
        for (Field field : fields) {
            if (field.getName().equals(name)) {
                ReflectionUtils.setFinal(field, val);
            }
        }
    }

    private Collection<Field> findSwitchMaps(Class<?>[] switchUsers) {
        Collection<Field> result = new LinkedList<>();

        try {
            for (Class<?> switchUser : switchUsers) {
                String name = switchUser.getName();
                int i = 0;

                while (true) {
                    try {
                        Class<?> suspect = Class.forName(String.format("%s$%d", name, ++i));
                        Field[] fields = suspect.getDeclaredFields();

                        for (Field field : fields) {
                            String fieldName = field.getName();

                            if (fieldName.startsWith("$SwitchMap$") && fieldName.endsWith(clazz.getSimpleName())) {
                                field.setAccessible(true);
                                result.add(field);
                            }
                        }
                    } catch (ClassNotFoundException e) {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not get switch map", e);
        }

        return result;
    }

    private void addSwitch() {
        try {
            for (Field field : switchFields) {
                int[] switches = (int[]) field.get(null);
                switches = Arrays.copyOf(switches, switches.length + 1);
                ReflectionUtils.setFinal(field, switches);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private void removeSwitch(int ordinal) {
        try {
            for (Field switchField : switchFields) {
                int[] old = (int[]) switchField.get(null);
                int[] now = Arrays.copyOf(old, old.length - 1);
                System.arraycopy(old, ordinal + 1, now, ordinal, old.length - ordinal - 1);
                ReflectionUtils.setFinal(switchField, now);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public E[] values() throws IllegalAccessException {
        return (E[]) findValuesField(valueName).get(null);
    }

    private static final class UndoInfo<E extends Enum<E>> {
        private final E[] values;
        private final MyHashMap<Field, int[]> switchValues;
        private final EnumHelper<E> helper;

        private UndoInfo(EnumHelper<E> helper) {
            try {
                this.helper = helper;
                this.values = helper.values().clone();
                this.switchValues = new MyHashMap<>(helper.switchFields.size());

                for (Field switchField : helper.switchFields) {
                    int[] arr = (int[]) switchField.get(null);
                    switchValues.put(switchField, arr.clone());
                }
            } catch (ReflectiveOperationException e) {
                throw new IllegalArgumentException("Could not record undo", e);
            }
        }

        private void undo() throws IllegalAccessException {
            Field vf = helper.findValuesField(helper.valueName);
            ReflectionUtils.setFinal(vf, values);

            for (int i = 0; i < values.length; i++) {
                ORDINAL_FIELD.set(values[i], i);
            }

            // reset all of the constants defined inside the enum
            Map<String, E> valueOf = new MyHashMap<>(values.length);

            for (E e : values) {
                valueOf.put(e.name(), e);
            }

            for (Field field : helper.fields) {
                E e = valueOf.get(field.getName());

                if (e != null) {
                    ReflectionUtils.setFinal(field, e);
                }
            }

            for (Map.Entry<Field, int[]> entry : switchValues.entrySet()) {
                Field field = entry.getKey();
                int[] mappings = entry.getValue();
                ReflectionUtils.setFinal(field, mappings);
            }
        }
    }
}
