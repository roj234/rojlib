package roj.kscript.type;

import roj.kscript.vm.ResourceManager;

import javax.annotation.Nonnull;

/**
 * This file is a part of MI <br>
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * @author Roj234
 * Filename: KDouble.java
 */
public class KDouble extends KBase {
    public double value;

    protected KDouble(double number) {
        this.value = number;
    }

    @Override
    public Type getType() {
        return Type.DOUBLE;
    }

    public static KType valueOf(double d) {
        return (((int) d) == d) ? new KInt((int) d) : new KDouble(d);
    }

    public static KType valueOf(String d) {
        return valueOf(Double.parseDouble(d));
    }

    @Override
    public final boolean isInt() {
        return ((int) value) == value;
    }

    @Override
    public final boolean asBool() {
        return value == value && value != 0;
    }

    @Override
    public final double asDouble() {
        return value;
    }

    @Override
    public final int asInt() {
        return (int) value;
    }

    @Override
    public final void setDoubleValue(double doubleValue) {
        value = doubleValue;
    }

    @Override
    public final void setIntValue(int intValue) {
        value = intValue;
    }

    @Nonnull
    @Override
    public final String asString() {
        return String.valueOf(value);
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KDouble that = (KDouble) o;
        return Double.compare(that.value, value) == 0;
    }

    @Override
    public final int hashCode() {
        return Float.floatToIntBits((float) value);
    }

    @Override
    public final StringBuilder toString0(StringBuilder sb, int depth) {
        return sb.append(value);
    }

    @Override
    public final boolean equalsTo(KType b) {
        return b.canCastTo(Type.DOUBLE) && b.asDouble() == value;
    }

    @Override
    public final void copyFrom(KType type) {
        value = type.asDouble();
    }

    @Override
    public KType copy() {
        return new KDouble(value);
    }

    @Override
    public KType setFlag(int kind) {
        return kind == -1 ? this : ResourceManager.get().allocD(value, kind);
    }

    @Override
    public final boolean canCastTo(Type type) {
        switch (type) {
            case BOOL:
            case DOUBLE:
            case INT:
            case STRING:
                return true;
        }
        return false;
    }
}
