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

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;

/**
 * Your description here
 *
 * @author Roj233
 * @version 0.1
 * @since 2021/8/15 23:36
 */
class VarHandleAccessor extends IFieldAccessor {
    // todo for java9
    // VarHandle handle;

    public VarHandleAccessor(Field field) {
        super(field);
        field.setAccessible(true);
        MethodHandles.Lookup lookup = MethodHandles.lookup();

    }

    @Override
    public Object getObject() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public boolean getBoolean() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public byte getByte() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public char getChar() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public short getShort() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public int getInt() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public long getLong() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public float getFloat() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public double getDouble() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public void setObject(Object obj) {

    }

    @Override
    public void setBoolean(boolean value) {

    }

    @Override
    public void setByte(byte value) {

    }

    @Override
    public void setChar(char value) {

    }

    @Override
    public void setShort(short value) {

    }

    @Override
    public void setInt(int value) {

    }

    @Override
    public void setLong(long value) {

    }

    @Override
    public void setFloat(float value) {

    }

    @Override
    public void setDouble(double value) {

    }
}
