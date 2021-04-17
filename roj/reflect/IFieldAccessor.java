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

import roj.text.TextUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * 抽象字段访问者
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/10/17 18:24
 */
public abstract class IFieldAccessor {
    static final List<String> arr = TextUtil.split(new ArrayList<>(9), "BOOL,BYTE,SHORT,CHAR,INT,LONG,FLOAT,DOUBLE,OBJECT", ',');

    public final Field field;
    public final byte flag;

    public IFieldAccessor(Field field) {
        this.field = field;
        byte flag;
        Class<?> type = field.getType();
        if (type.isPrimitive()) {
            switch (type.getName()) {
                case "int":
                    flag = 4;
                break;
                case "short":
                    flag = 2;
                break;
                case "double":
                    flag = 7;
                break;
                case "long":
                    flag = 5;
                break;
                case "float":
                    flag = 6;
                break;
                case "char":
                    flag = 3;
                break;
                case "byte":
                    flag = 1;
                break;
                case "boolean":
                    flag = 0;
                break;
                default:
                    throw new InternalError("Unknown class " + type);
            }
        } else 
            flag = 8;
        flag |= Modifier.isStatic(field.getModifiers()) ? 16 : 0;
        flag |= Modifier.isVolatile(field.getModifiers()) ? 32 : 0;
        this.flag = flag;
    }

    protected void checkObjectType(Object obj) {
        if (!field.getDeclaringClass().isInstance(obj)) // include null
            throw new IllegalArgumentException("Cannot set instance (not instance) to " + field.getDeclaringClass().getName());
    }

    protected void checkType(byte required) {
        if ((flag & 15) != required)
            throw new IllegalArgumentException(arr.get(flag & 15) + " cannot cast to " + arr.get(required));
    }

    public abstract void setInstance(Object instance);

    public abstract void clearInstance();

    public abstract Object getObject();

    public abstract boolean getBoolean();

    public abstract byte getByte();

    public abstract char getChar();

    public abstract short getShort();

    public abstract int getInt();

    public abstract long getLong();

    public abstract float getFloat();

    public abstract double getDouble();

    public abstract void setObject(Object obj);

    public abstract void setBoolean(boolean value);

    public abstract void setByte(byte value);

    public abstract void setChar(char value);

    public abstract void setShort(short value);

    public abstract void setInt(int value);

    public abstract void setLong(long value);

    public abstract void setFloat(float value);

    public abstract void setDouble(double value);
}
