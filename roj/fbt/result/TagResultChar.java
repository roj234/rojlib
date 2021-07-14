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
package roj.fbt.result;

import javax.annotation.Nonnull;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public class TagResultChar implements TagResult {
    public char result;

    public TagResultChar(char result) {
        this.result = result;
    }

    @Override
    public int getInt() {
        return result;
    }

    @Override
    public short getShort() {
        return (short) result;
    }

    @Override
    public long getLong() {
        return result;
    }

    @Override
    public double getDouble() {
        return result;
    }

    @Override
    public char getChar() {
        return result;
    }

    @Override
    public byte getByte() {
        return (byte) result;
    }

    @Override
    public float getFloat() {
        return result;
    }

    @Override
    public void setInt(int i) {
        this.result = (char) i;
    }

    @Override
    public void setShort(short i) {
        this.result = (char) i;
    }

    @Override
    public void setLong(long i) {
        this.result = (char) i;
    }

    @Override
    public void setChar(char i) {
        this.result = i;
    }

    @Override
    public void setFloat(float i) {
        this.result = (char) i;
    }

    @Override
    public void setByte(byte i) {
        this.result = (char) i;
    }

    @Override
    public void setDouble(double i) {
        this.result = (char) i;
    }

    @Nonnull
    @Override
    public TagResultType getType() {
        return TagResultType.CHAR;
    }
}
