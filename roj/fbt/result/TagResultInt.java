package roj.fbt.result;

import javax.annotation.Nonnull;

/**
 * This file is a part of MI <br>
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * @author Roj234
 * Filename: TagResultInt.java
 */
public class TagResultInt implements TagResult {
    public int result;

    public TagResultInt(int result) {
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
        this.result = i;
    }

    @Override
    public void setShort(short i) {
        this.result = i;
    }

    @Override
    public void setLong(long i) {
        this.result = (int) i;
    }

    @Override
    public void setChar(char i) {
        this.result = i;
    }

    @Override
    public void setFloat(float i) {
        this.result = (int) i;
    }

    @Override
    public void setByte(byte i) {
        this.result = i;
    }

    @Override
    public void setDouble(double i) {
        this.result = (int) i;
    }

    @Nonnull
    @Override
    public TagResultType getType() {
        return TagResultType.INT;
    }
}
