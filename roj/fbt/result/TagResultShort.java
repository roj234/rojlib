package roj.fbt.result;

import javax.annotation.Nonnull;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: TagResultShort.java
 */
public class TagResultShort implements TagResult {
    public short result;

    public TagResultShort(short result) {
        this.result = result;
    }

    @Override
    public int getInt() {
        return result;
    }

    @Override
    public short getShort() {
        return result;
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
        return (char) result;
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
        this.result = (short) i;
    }

    @Override
    public void setShort(short i) {
        this.result = i;
    }

    @Override
    public void setLong(long i) {
        this.result = (short) i;
    }

    @Override
    public void setChar(char i) {
        this.result = (short) i;
    }

    @Override
    public void setFloat(float i) {
        this.result = (short) i;
    }

    @Override
    public void setByte(byte i) {
        this.result = i;
    }

    @Override
    public void setDouble(double i) {
        this.result = (short) i;
    }

    @Nonnull
    @Override
    public TagResultType getType() {
        return TagResultType.SHORT;
    }
}
