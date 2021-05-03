package roj.kscript.type;

import javax.annotation.Nonnull;

/**
 * This file is a part of MI <br>
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * @author Roj234
 * Filename: KInt.java
 */
public final class KInt extends KBase {
    public int value;

    public KInt() {
        super(Type.INT);
    }

    public KInt(int number) {
        super(Type.INT);
        this.value = number;
    }

    public static KInt valueOf(String v) {
        return valueOf(Integer.parseInt(v));
    }

    public static KInt valueOf(int nv) {
        return new KInt(nv);
    }

    @Override
    public KInt asKInt() {
        return this;
    }

    @Override
    public KDouble asKDouble() {
        return new KDouble(value);
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
}
