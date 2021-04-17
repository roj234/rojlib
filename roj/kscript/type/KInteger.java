package roj.kscript.type;

import javax.annotation.Nonnull;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: KInteger.java
 */
public class KInteger extends KBase {
    public int value;

    public KInteger() {
        super(Type.NUMBER);
    }

    public KInteger(int number) {
        super(Type.NUMBER);
        this.value = number;
    }

    public static KInteger valueOf(String v) {
        return valueOf(Integer.parseInt(v));
    }

    public static KInteger valueOf(int nativeValue) {
        /*switch (nativeValue) {
            case -1:
                return Immutable.M_ONE;
            case 0:
                return Immutable.ZERO;
            case 1:
                return Immutable.ONE;
            default:*/
        return new KInteger(nativeValue);
        //}
    }

    @Override
    public KInteger asKInteger() {
        return this;
    }

    @Override
    public KDouble asKDouble() {
        return new KDouble(value);
    }

    @Override
    public boolean isInteger() {
        return true;
    }

    @Override
    public double asDouble() {
        return value;
    }

    @Override
    public int asInteger() {
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
        KInteger that = (KInteger) o;
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
            case NUMBER:
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
        return b.canCastTo(Type.NUMBER) && b.asInteger() == value;
    }

    @Override
    public boolean asBoolean() {
        return value != 0;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    /*static class Immutable extends KInteger {
        static final Immutable
                M_ONE = new Immutable(-1),
                ZERO = new Immutable(0),
                ONE = new Immutable(1);

        public Immutable(int i) {
            super(i);
        }

        @Override
        public boolean isImmutable() {
            return true;
        }

        @Override
        public void setValue(int value) {
            throw new UnsupportedOperationException("setValue is unavailable in an Immutable Integer.");
        }
    }*/
}
