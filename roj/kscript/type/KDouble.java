package roj.kscript.type;

import javax.annotation.Nonnull;

/**
 * This file is a part of MI <br>
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * @author Roj234
 * Filename: YAMLNumber.java
 */
public final class KDouble extends KBase {
    public double value;

    public KDouble(double number) {
        super(Type.DOUBLE);
        this.value = number;
    }

    public static KDouble valueOfF(double d) {
        return new KDouble(d);
    }

    public static KType valueOf(double d) {
        return (((int) d) == d) ? new KInt((int) d) : new KDouble(d);
    }

    public static KType valueOf(String d) {
        return valueOf(Double.parseDouble(d));
    }

    @Override
    public KDouble asKDouble() {
        return this;
    }

    @Override
    public KInt asKInt() {
        return isInt() ? new KInt((int) value) : super.asKInt();
    }

    @Override
    public boolean isInt() {
        return ((int) value) == value;
    }

    @Override
    public boolean asBool() {
        return value == value && value != 0;
    }

    @Override
    public double asDouble() {
        return value;
    }

    @Override
    public int asInt() {
        return (int) value;
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
        KDouble that = (KDouble) o;
        return Double.compare(that.value, value) == 0;
    }

    @Override
    public int hashCode() {
        return Float.floatToIntBits((float) value);
    }

    @Override
    public StringBuilder toString0(StringBuilder sb, int depth) {
        return sb.append(value);
    }

    @Override
    public boolean equalsTo(KType b) {
        return b.canCastTo(Type.DOUBLE) && b.asDouble() == value;
    }

    @Override
    public KType copy() {
        return valueOf(value);
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

}
