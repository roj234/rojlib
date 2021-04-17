package roj.config.data;

import javax.annotation.Nonnull;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: YAMLBoolean.java
 */
public final class CBoolean extends ConfEntry {
    public static final CBoolean TRUE = new CBoolean(), FALSE = new CBoolean();

    private CBoolean() {
        super(Type.BOOL);
    }

    public static ConfEntry valueOf(boolean b) {
        return b ? TRUE : FALSE;
    }

    public static ConfEntry valueOf(String val) {
        return valueOf(Boolean.parseBoolean(val));
    }

    @Override
    public int asNumber() {
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
    public StringBuilder toYAML(StringBuilder sb, int depth) {
        return sb.append(this == TRUE);
    }

    @Override
    public StringBuilder toJSON(StringBuilder sb, int depth) {
        return toYAML(sb, depth);
    }

}
