/*
 * This file is a part of MoreItems
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

import roj.asm.util.AccessFlag;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 使用Unsafe的字段访问
 *
 * @author Roj234
 * @since  2020/10/17 18:31
 */
public final class UFA extends FieldAccessor {
    public interface UNSAFE {
        void setUnsafe(Object javaUnsafe);
        Object getUnsafe();

        long allocateMemory(long size);
        long reallocateMemory(long addr, long newsize);
        void setMemory(Object base, long addr, long len, byte val);
        void copyMemory(Object base, long addr, Object tbase, long taddr, long len);
        void freeMemory(long addr);


        long staticFieldOffset(Field field);
        long objectFieldOffset(Field field);
        Object staticFieldBase(Field field);

        int     getInt(Object o, long offset);
        void    putInt(Object o, long offset, int x);
        Object  getObject(Object o, long offset);
        void    putObject(Object o, long offset, Object x);
        boolean getBoolean(Object o, long offset);
        void    putBoolean(Object o, long offset, boolean x);
        byte    getByte(Object o, long offset);
        void    putByte(Object o, long offset, byte x);
        short   getShort(Object o, long offset);
        void    putShort(Object o, long offset, short x);
        char    getChar(Object o, long offset);
        void    putChar(Object o, long offset, char x);
        long    getLong(Object o, long offset);
        void    putLong(Object o, long offset, long x);
        float   getFloat(Object o, long offset);
        void    putFloat(Object o, long offset, float x);
        double  getDouble(Object o, long offset);
        void    putDouble(Object o, long offset, double x);

        Object  getObjectVolatile(Object o, long offset);
        void    putObjectVolatile(Object o, long offset, Object x);
        int     getIntVolatile(Object o, long offset);
        void    putIntVolatile(Object o, long offset, int x);
        boolean getBooleanVolatile(Object o, long offset);
        void    putBooleanVolatile(Object o, long offset, boolean x);
        byte    getByteVolatile(Object o, long offset);
        void    putByteVolatile(Object o, long offset, byte x);
        short   getShortVolatile(Object o, long offset);
        void    putShortVolatile(Object o, long offset, short x);
        char    getCharVolatile(Object o, long offset);
        void    putCharVolatile(Object o, long offset, char x);
        long    getLongVolatile(Object o, long offset);
        void    putLongVolatile(Object o, long offset, long x);
        float   getFloatVolatile(Object o, long offset);
        void    putFloatVolatile(Object o, long offset, float x);
        double  getDoubleVolatile(Object o, long offset);
        void    putDoubleVolatile(Object o, long offset, double x);
    }

    public static final UNSAFE U;
    static {
        Method[] methods = UNSAFE.class.getDeclaredMethods();
        String[] names = new String[methods.length - 2];
        int j = 0;
        for (Method m : methods) {
            if (m.getName().endsWith("Unsafe")) continue;
            names[j++] = m.getName();
        }

        DirectAccessor<UNSAFE> da = DirectAccessor.builder(UNSAFE.class);
        Class<?> unsafe = null;
        try {
            unsafe = sun.misc.Unsafe.class;
        } catch (Throwable e) {
            for (Field f : AtomicInteger.class.getDeclaredFields()) {
                if ((f.getModifiers() & AccessFlag.STATIC) != 0 && !f.getType().isPrimitive()) {
                    unsafe = f.getType();
                    break;
                }
            }
        }

        U = da.makeCache(unsafe, "unsafe", 2)
              .useCache("unsafe")
              .delegate(unsafe, names)
              .access(unsafe, "theUnsafe", "getUnsafe", null)
              .build();
        U.setUnsafe(U.getUnsafe());
    }

    private final long offset;
    private Object instance;

    public boolean checkCast = true;

    UFA(Field field) {
        super(field);
        this.offset = (flag & 16) != 0 ? U.staticFieldOffset(field) : U.objectFieldOffset(field);
        if (this.offset == -1) {
            throw new IllegalArgumentException("Field offset error " + field);
        }
        if ((flag & 16) != 0) {
            this.instance = U.staticFieldBase(field);
        }
    }

    public void setInstance(Object instance) {
        if ((flag & 16) != 0)
            return;
        checkObjectType(instance);
        if (instance == null)
            throw new IllegalArgumentException("Instance can't be null for a non-static field!");
        this.instance = instance;
    }

    public void clearInstance() {
        if ((flag & 16) != 0)
            return;
        this.instance = null;
    }

    private void checkAccess() {
        if (instance == null)
            throw new IllegalArgumentException("Instance can't be null for a non-static field!");
    }

    @Override
    public Object getObject() {
        checkType((byte) 8);
        checkAccess();
        return (flag & 32) != 0 ? U.getObjectVolatile(instance, offset) : U.getObject(instance, offset);
    }

    @Override
    public boolean getBoolean() {
        checkType((byte) 0);
        checkAccess();
        return (flag & 32) != 0 ? U.getBooleanVolatile(instance, offset) : U.getBoolean(instance, offset);
    }

    @Override
    public byte getByte() {
        checkType((byte) 1);
        checkAccess();
        return (flag & 32) != 0 ? U.getByteVolatile(instance, offset) : U.getByte(instance, offset);
    }

    @Override
    public char getChar() {
        checkType((byte) 2);
        checkAccess();
        return (flag & 32) != 0 ? U.getCharVolatile(instance, offset) : U.getChar(instance, offset);
    }

    @Override
    public short getShort() {
        checkType((byte) 3);
        checkAccess();
        return (flag & 32) != 0 ? U.getShortVolatile(instance, offset) : U.getShort(instance, offset);
    }

    @Override
    public int getInt() {
        checkType((byte) 4);
        checkAccess();
        return (flag & 32) != 0 ? U.getIntVolatile(instance, offset) : U.getInt(instance, offset);
    }

    @Override
    public long getLong() {
        checkType((byte) 5);
        checkAccess();
        return (flag & 32) != 0 ? U.getLongVolatile(instance, offset) : U.getLong(instance, offset);
    }

    @Override
    public float getFloat() {
        checkType((byte) 6);
        checkAccess();
        return (flag & 32) != 0 ? U.getFloatVolatile(instance, offset) : U.getFloat(instance, offset);
    }

    @Override
    public double getDouble() {
        checkType((byte) 7);
        checkAccess();
        return (flag & 32) != 0 ? U.getDoubleVolatile(instance, offset) : U.getDouble(instance, offset);
    }

    @Override
    public void setObject(Object obj) {
        if ((flag & 15) != 8) {
            switch (flag & 15) {
                case 0:
                    setBoolean((Boolean) obj);
                    break;
                case 1:
                    setByte((Byte) obj);
                    break;
                case 2:
                    setChar((Character) obj);
                    break;
                case 3:
                    setShort((Short) obj);
                    break;
                case 4:
                    setInt((Integer) obj);
                    break;
                case 5:
                    setLong((Long) obj);
                    break;
                case 6:
                    setFloat((Float) obj);
                    break;
                case 7:
                    setDouble((Double) obj);
                    break;
            }
            return;
        }
        checkType((byte) 8);
        checkAccess();
        if (checkCast && obj != null && !field.getType().isInstance(obj))
            throw new ClassCastException(obj.getClass().getName() + " cannot cast to " + field.getType().getName());
        if ((flag & 32) != 0) {
            U.putObjectVolatile(instance, offset, obj);
        } else {
            U.putObject(instance, offset, obj);
        }
    }

    @Override
    public void setBoolean(boolean value) {
        checkType((byte) 0);
        checkAccess();
        if ((flag & 32) != 0) {
            U.putBooleanVolatile(instance, offset, value);
        } else {
            U.putBoolean(instance, offset, value);
        }
    }

    @Override
    public void setByte(byte value) {
        checkType((byte) 1);
        checkAccess();
        if ((flag & 32) != 0) {
            U.putByteVolatile(instance, offset, value);
        } else {
            U.putByte(instance, offset, value);
        }
    }

    @Override
    public void setChar(char value) {
        checkType((byte) 2);
        checkAccess();
        if ((flag & 32) != 0) {
            U.putCharVolatile(instance, offset, value);
        } else {
            U.putChar(instance, offset, value);
        }
    }

    @Override
    public void setShort(short value) {
        checkType((byte) 3);
        checkAccess();
        if ((flag & 32) != 0) {
            U.putShortVolatile(instance, offset, value);
        } else {
            U.putShort(instance, offset, value);
        }
    }

    @Override
    public void setInt(int value) {
        checkType((byte) 4);
        checkAccess();
        if ((flag & 32) != 0) {
            U.putIntVolatile(instance, offset, value);
        } else {
            U.putInt(instance, offset, value);
        }
    }

    @Override
    public void setLong(long value) {
        checkType((byte) 5);
        checkAccess();
        if ((flag & 32) != 0) {
            U.putLongVolatile(instance, offset, value);
        } else {
            U.putLong(instance, offset, value);
        }
    }

    @Override
    public void setFloat(float value) {
        checkType((byte) 6);
        checkAccess();
        if ((flag & 32) != 0) {
            U.putFloatVolatile(instance, offset, value);
        } else {
            U.putFloat(instance, offset, value);
        }
    }

    @Override
    public void setDouble(double value) {
        checkType((byte) 7);
        checkAccess();
        if ((flag & 32) != 0) {
            U.putDoubleVolatile(instance, offset, value);
        } else {
            U.putDouble(instance, offset, value);
        }
    }
}
