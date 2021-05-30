package roj.kscript.type;

import javax.annotation.Nonnull;

/**
 * This file is a part of MI <br>
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * @author Roj234
 * Filename: KUndefined.java
 */
public final class KUndefined extends KBase {
    public static final KUndefined UNDEFINED = new KUndefined();

    private KUndefined() {}

    @Override
    public Type getType() {
        return Type.UNDEFINED;
    }

    @Override
    public StringBuilder toString0(StringBuilder sb, int depth) {
        return sb.append("undefined");
    }

    @Override
    public boolean equalsTo(KType b) {
        return b == UNDEFINED || b == KNull.NULL;
    }

    @Override
    public boolean equals(Object obj) {
        return obj == UNDEFINED;
    }

    @Override
    public boolean asBool() {
        return false;
    }

    @Nonnull
    @Override
    public String asString() {
        return "undefined";
    }

    @Override
    public int hashCode() {
        return 235789;
    }

    @Override
    public boolean canCastTo(Type type) {
        return false;
    }
}
