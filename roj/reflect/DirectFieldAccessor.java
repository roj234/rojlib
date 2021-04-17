package roj.reflect;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: DirectFieldAccessor.java
 */
public interface DirectFieldAccessor extends Instanced {
    Object getObject();

    boolean getBoolean();

    byte getByte();

    short getShort();

    char getChar();

    int getInt();

    float getFloat();

    long getLong();

    double getDouble();

    void invoke();

    void setObject(Object o);

    void setBoolean(boolean z);

    void setByte(byte b);

    void setShort(short s);

    void setChar(char c);

    void setInt(int i);

    void setFloat(float f);

    void setLong(long l);

    void setDouble(double d);
}
