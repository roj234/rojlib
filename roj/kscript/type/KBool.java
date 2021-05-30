package roj.kscript.type;

import javax.annotation.Nonnull;

/**
 * This file is a part of MI <br>
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * @author Roj234
 * Filename: YAMLBoolean.java
 */
public final class KBool extends KBase {
    public static final KBool
            TRUE = new KBool(),
            FALSE = new KBool();

    @Override
    public Type getType() {
        return Type.BOOL;
    }

    public static KBool valueOf(boolean b) {
        return b ? TRUE : FALSE;
    }

    private KBool() {}

    @Override
    public int asInt() {
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
    public boolean asBool() {
        return this == TRUE;
    }

    @Override
    public StringBuilder toString0(StringBuilder sb, int depth) {
        return sb.append(this == TRUE);
    }

    @Override
    public boolean equalsTo(KType b) {
        return b.canCastTo(Type.BOOL) && b.asBool() == (this == TRUE);
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
