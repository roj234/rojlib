package roj.reflect.misc;

import roj.reflect.Instanced;

/**
 * This file is a part of MI <br>
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * @author Roj234
 * Filename: StandardInvokers.java
 */
public interface StandardInvokers extends Instanced {
    boolean getBoolean();

    byte getByte();

    short getShort();

    char getChar();

    int getInt();

    long getLong();

    float getFloat();

    Object getObject();

    void instance();

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
