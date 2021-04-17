package roj.config.data;

import javax.annotation.Nonnull;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: YAMLNumber.java
 */
public final class CInteger extends ConfEntry {
    public int value;

    public CInteger(int number) {
        super(Type.NUMBER);
        this.value = number;
    }

    public static CInteger valueOf(int number) {
        return new CInteger(number);
    }

    public static CInteger valueOf(String number) {
        return valueOf(Integer.parseInt(number));
    }

    @Override
    public double asDouble() {
        return value;
    }

    @Override
    public int asNumber() {
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
        CInteger that = (CInteger) o;
        return that.value == value;
    }

    @Override
    public int hashCode() {
        return value;
    }

    @Override
    public StringBuilder toYAML(StringBuilder sb, int depth) {
        return sb.append(value);
    }

    @Override
    public StringBuilder toJSON(StringBuilder sb, int depth) {
        return sb.append(value);
    }

    @Override
    protected boolean isSimilar(ConfEntry value) {
        return value.getType().isNumber();
    }
}
