package roj.fbt.result;

import javax.annotation.Nonnull;

/**
 * This file is a part of MI <br>
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * @author Roj234
 * Filename: TagResult.java
 */
public interface TagResult {
    byte[] EMPTY_BYTE_ARRAY = new byte[0];

    default int getInt() {
        return 0;
    }

    default short getShort() {
        return 0;
    }

    default long getLong() {
        return 0;
    }

    default float getFloat() {
        return 0;
    }

    default double getDouble() {
        return 0;
    }

    default char getChar() {
        return 0;
    }

    default byte getByte() {
        return 0;
    }

    @Nonnull
    default String getString() {
        return "";
    }

    default byte[] getByteArray() {
        return EMPTY_BYTE_ARRAY;
    }

    default void setInt(int i) {
    }

    default void setShort(short i) {
    }

    default void setLong(long i) {
    }

    default void setChar(char i) {
    }

    default void setFloat(float i) {
    }

    default void setByte(byte i) {
    }

    default void setDouble(double i) {
    }

    @Nonnull
    TagResultType getType();
}
