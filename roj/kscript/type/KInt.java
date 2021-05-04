package roj.kscript.type;

import roj.kscript.KConstants;
import roj.math.MathUtils;

import javax.annotation.Nonnull;

/**
 * This file is a part of MI <br>
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * @author Roj234
 * Filename: KInt.java
 */
public abstract class KInt extends KBase {
    public int value;

    KInt(int number) {
        super(Type.INT);
        this.value = number;
    }

    public static KInt valueOf(int nv) {
        return new OnStack(nv);
    }

    @Override
    public KInt asKInt() {
        return this;
    }

    @Override
    public KDouble asKDouble() {
        return new KDouble.Intl(value);
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

    public static final class OnStack extends KInt {
        public byte s;

        public OnStack(int number) {
            super(number);
            s = 2;
        }

        public static KInt valueOf(int nv) {
            return KConstants.retainStackIntHolder(nv);
        }

        @Override
        public KInt asKInt() {
            return this;
        }

        @Override
        public int spec() {
            return s & 0xFF;
        }

        @Override
        public KType markImmutable(boolean kind) {
            s = (byte) (kind ? 16 : 1);
            return this;
        }

        @Override
        public KType copy() {
            return valueOf(value);
        }
    }

    public static final class Intl extends KInt {
        Intl(int number) {
            super(number);
        }

        public static KInt valueOf(int d) {
            return new Intl(d);
        }

        public static KInt valueOf(String d) {
            return valueOf(MathUtils.parseInt(d));
        }

        @Override
        public int spec() {
            return 8;
        }

        @Override
        public KType copy() {
            return this;
        }
    }
}
