package roj.config.data;

import javax.annotation.Nonnull;

/**
 * This file is a part of MI <br>
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * @author Roj234
 * Filename: YAMLNumber.java
 */
public final class CDouble extends ConfEntry {
    public double value;

    public CDouble(double number) {
        super(Type.DOUBLE);
        this.value = number;
    }

    public static CDouble valueOf(double number) {
        return new CDouble(number);
    }

    public static CDouble valueOf(String number) {
        return valueOf(Double.parseDouble(number));
    }

    @Override
    public double asDouble() {
        return value;
    }

    @Override
    public int asNumber() {
        return (int) value;
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
        CDouble that = (CDouble) o;
        return Double.compare(that.value, value) == 0;
    }

    @Override
    public int hashCode() {
        return Float.floatToIntBits((float) value);
    }

    @Override
    public StringBuilder toYAML(StringBuilder sb, int depth) {
        return sb.append(value);
    }

    @Override
    public StringBuilder toJSON(StringBuilder sb, int depth) {
        return toYAML(sb, depth);
    }

    @Override
    protected boolean isSimilar(ConfEntry value) {
        return value.getType().isNumber();
    }
}
