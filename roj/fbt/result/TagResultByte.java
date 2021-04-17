package roj.fbt.result;

import javax.annotation.Nonnull;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: TagResultByte.java
 */
public class TagResultByte implements TagResult {
    public byte result;

    public TagResultByte(byte result) {
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
        return result;
    }

    @Override
    public float getFloat() {
        return result;
    }

    @Override
    public void setInt(int i) {
        this.result = (byte) i;
    }

    @Override
    public void setShort(short i) {
        this.result = (byte) i;
    }

    @Override
    public void setLong(long i) {
        this.result = (byte) i;
    }

    @Override
    public void setChar(char i) {
        this.result = (byte) i;
    }

    @Override
    public void setFloat(float i) {
        this.result = (byte) i;
    }

    @Override
    public void setByte(byte i) {
        this.result = i;
    }

    @Override
    public void setDouble(double i) {
        this.result = (byte) i;
    }

    @Nonnull
    @Override
    public TagResultType getType() {
        return TagResultType.BYTE;
    }
}
