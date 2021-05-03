package roj.reflect;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/10/17 18:24
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
