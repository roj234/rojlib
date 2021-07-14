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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/10/17 18:24
 */
public abstract class IFieldAccessor {
    public final Field field;
    Object instance;
    public final boolean isStatic;

    public IFieldAccessor(Field field) {
        this.field = field;
        this.isStatic = Modifier.isStatic(field.getModifiers());
    }

    public void setInstance(Object instance) {
        if (isStatic)
            return;
        if (instance == null)
            throw new IllegalArgumentException("Instance can't be null in a non-static field!");
        this.instance = instance;
    }

    protected void checkAccess() {
        if (instance == null)
            throw new IllegalArgumentException("Instance can't be null in a non-static field!");
    }

    public void clearInstance() {
        if (isStatic)
            return;
        this.instance = null;
    }

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
