package roj.kscript.type;

import roj.kscript.vm.ResourceManager;
import roj.math.MathUtils;

import javax.annotation.Nonnull;

/**
 * This file is a part of MI <br>
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * @author Roj234
 * Filename: KInt.java
 */
public class KInt extends KBase {
    public int value;

    protected KInt(int number) {
        this.value = number;
    }

    @Override
    public Type getType() {
        return Type.INT;
    }

    public static KInt valueOf(int nv) {
        return new KInt(nv);
    }

    public static KInt valueOf(String d) {
        return valueOf(MathUtils.parseInt(d));
    }

    @Override
    public boolean isInt() {
        return true;
    }

    @Override
    public double asDouble() {
        return value;
    }

    @Override
    public int asInt() {
        return value;
    }

    @Override
    public void setIntValue(int intValue) {
        value = intValue;
    }

    @Override
    public void setDoubleValue(double doubleValue) {
        value = (int) doubleValue;
    }

    @Nonnull
    @Override
    public String asString() {
        return String.valueOf(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KInt that = (KInt) o;
        return that.value == value;
    }

    @Override
    public void copyFrom(KType type) {
        value = type.asInt();
    }

    @Override
    public boolean canCastTo(Type type) {
        switch (type) {
            case BOOL:
            case DOUBLE:
            case INT:
            case STRING:
                return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return value;
    }

    @Override
    public StringBuilder toString0(StringBuilder sb, int depth) {
        return sb.append(value);
    }

    @Override
    public boolean equalsTo(KType b) {
        return b.canCastTo(Type.INT) && b.asInt() == value;
    }

    @Override
    public boolean asBool() {
        return value != 0;
    }

    @Override
    public KType copy() {
        return new KInt(value);
    }

    @Override
    public KType setFlag(int kind) {
        return ResourceManager.get().allocI(value, kind);
    }
}
