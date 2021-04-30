package roj.config.data;

import javax.annotation.Nonnull;

/**
 * This file is a part of MI <br>
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * @author Roj234
 * Filename: YAMLNull.java
 */
public final class CNull extends ConfEntry {
    public static final CNull NULL = new CNull();

    private CNull() {
        super(Type.NULL);
    }

    @Nonnull
    public String asString() {
        return "";
    }

    @Override
    public double asDouble() {
        return 0;
    }

    @Override
    public boolean asBoolean() {
        return false;
    }

    @Override
    public int asNumber() {
        return 0;
    }

    @Nonnull
    @Override
    public CMapping asMap() {
        return new CMapping();
    }

    @Nonnull
    @Override
    public CList asList() {
        return new CList();
    }

    @Nonnull
    @Override
    public <T> CObject<T> asObject(Class<T> clazz) {
        return new CObject<>((T) null);
    }

    @Override
    public StringBuilder toYAML(StringBuilder sb, int depth) {
        return sb.append("null");
    }

    @Override
    public StringBuilder toJSON(StringBuilder sb, int depth) {
        return sb.append("null");
    }

    @Override
    protected boolean isSimilar(ConfEntry value) {
        return false;
    }

    @Override
    public boolean equals(Object obj) {
        return obj == NULL;
    }

    @Override
    public int hashCode() {
        return 0;
    }
}
