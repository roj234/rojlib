package roj.config.data;

import roj.config.word.AbstLexer;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * This file is a part of MI <br>
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * @author Roj234
 * Filename: YAMLString.java
 */
public final class CString extends ConfEntry {
    public String value;

    public CString(String string) {
        super(Type.STRING);
        this.value = string;
    }

    public static CString valueOf(String s) {
        return new CString(s);
    }

    @Nonnull
    @Override
    public String asString() {
        return value;
    }

    @Override
    public double asDouble() {
        return Double.parseDouble(value);
    }

    @Override
    public int asNumber() {
        return Integer.parseInt(value);
    }

    @Override
    public boolean asBoolean() {
        return Boolean.parseBoolean(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CString that = (CString) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return value == null ? 0 : value.hashCode();
    }

    @Override
    public StringBuilder toYAML(StringBuilder sb, int depth) {
        return sb.append('"').append(AbstLexer.addSlashes(value)).append('"');
    }

    @Override
    public StringBuilder toJSON(StringBuilder sb, int depth) {
        return toYAML(sb, depth);
    }

}
