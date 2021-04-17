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

import roj.util.EmptyArrays;

import java.lang.reflect.Field;

/**
 * Accessor for java9 到头还是逃不过煞笔包装
 *
 * @author Roj233
 * @version 0.1
 * @since 2021/8/15 23:36
 */
class VH extends IFieldAccessor {
    //VarHandle handle;
    Object[] tmpG, tmpS;

    public VH(Field field) {
        super(field);
        field.setAccessible(true);
        //handle = MethodHandles.lookup().unreflectVarHandle(field);
        if((flag & 16) == 0) {
            tmpG = new Object[1];
            tmpS = new Object[2];
        } else {
            tmpG = EmptyArrays.OBJECTS;
            tmpS = new Object[1];
        }
    }

    private void checkAccess() {
        if ((flag & 16) != 0)
            return;
        if (tmpS[0] == null)
            throw new IllegalArgumentException("Instance can't be null for a non-static field!");
    }

    @Override
    public void setInstance(Object instance) {
        if ((flag & 16) != 0)
            return;
        checkObjectType(instance);
        if (instance == null)
            throw new IllegalArgumentException("Instance can't be null for a non-static field!");
        tmpS[0] = instance;
        tmpG[0] = instance;
    }

    @Override
    public void clearInstance() {
        if ((flag & 16) != 0)
            return;
        tmpS[0] = null;
        tmpG[0] = null;
    }

    @Override
    public Object getObject() {
        if ((flag & 16) == 0) {
            checkAccess();
        }
        return null;//(flag & 32) != 0 ? handle.getVolatile(tmpG) : handle.get(tmpG);
    }

    @Override
    public boolean getBoolean() {
        return (boolean) getObject();
    }

    @Override
    public byte getByte() {
        return (byte) getObject();
    }

    @Override
    public char getChar() {
        return (char) getObject();
    }

    @Override
    public short getShort() {
        return (short) getObject();
    }

    @Override
    public int getInt() {
        return (int) getObject();
    }

    @Override
    public long getLong() {
        return (long) getObject();
    }

    @Override
    public float getFloat() {
        return (float) getObject();
    }

    @Override
    public double getDouble() {
        return (double) getObject();
    }

    @Override
    public void setObject(Object obj) {
        if ((flag & 16) == 0)
            checkAccess();
        /*tmpS[tmpS.length - 1] = obj;
        try {
            if ((flag & 32) != 0) {
                handle.putVolatile(tmpS);
            } else {
                handle.put(tmpS);
            }
        } finally {
            tmpS[tmpS.length - 1] = null;
        }*/
    }

    @Override
    public void setBoolean(boolean value) {
        setObject(value);
    }

    @Override
    public void setByte(byte value) {
        setObject(value);
    }

    @Override
    public void setChar(char value) {
        setObject(value);
    }

    @Override
    public void setShort(short value) {
        setObject(value);
    }

    @Override
    public void setInt(int value) {
        setObject(value);
    }

    @Override
    public void setLong(long value) {
        setObject(value);
    }

    @Override
    public void setFloat(float value) {
        setObject(value);
    }

    @Override
    public void setDouble(double value) {
        setObject(value);
    }
}
