package roj.kscript.type;

import javax.annotation.Nonnull;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: YAMLBoolean.java
 */
public final class KBoolean extends KBase {
    public static final KBoolean
            TRUE = new KBoolean(),
            FALSE = new KBoolean();

    public static KBoolean valueOf(boolean b) {
        return b ? TRUE : FALSE;
    }

    private KBoolean() {
        super(Type.BOOL);
    }

    @Override
    public int asInteger() {
        return this == TRUE ? 1 : 0;
    }

    @Override
    public double asDouble() {
        return this == TRUE ? 1 : 0;
    }

    @Nonnull
    @Override
    public String asString() {
        return this == TRUE ? "true" : "false";
    }

    @Override
    public boolean asBoolean() {
        return this == TRUE;
    }

    @Override
    public StringBuilder toString0(StringBuilder sb, int depth) {
        return sb.append(this == TRUE);
    }

    @Override
    public boolean equalsTo(KType b) {
        return b.canCastTo(Type.BOOL) && b.asBoolean() == (this == TRUE);
    }

    @Override
    public KType copy() {
        return this;
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
}
