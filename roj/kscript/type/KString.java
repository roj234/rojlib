package roj.kscript.type;

import roj.config.word.AbstLexer;
import roj.text.TextUtil;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: YAMLString.java
 */
public final class KString extends KBase {
    private final String value;
    private byte valueType = -2;

    public KString(String string) {
        super(Type.STRING);
        this.value = string;
    }

    public static final KString EMPTY = new KString("");

    public static KString valueOf(String name) {
        return name.length() == 0 ? EMPTY : new KString(name);
    }

    @Nonnull
    @Override
    public String asString() {
        return value;
    }

    @Override
    public double asDouble() {
        if (checkType() >= 0) {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException ignored) {
            }
        }
        return super.asDouble();
    }

    @Override
    public int asInteger() {
        if (checkType() >= 0) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ignored) {
            }
        }
        return super.asInteger();
    }

    @Override
    public boolean isString() {
        return checkType() == -1;
    }

    @Override
    public boolean asBoolean() {
        return !value.equalsIgnoreCase("false") && !value.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KString that = (KString) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return value == null ? 0 : value.hashCode();
    }

    @Override
    public StringBuilder toString0(StringBuilder sb, int depth) {
        return sb.append('"').append(AbstLexer.addSlashes(value)).append('"');
    }

    @Override
    public boolean equalsTo(KType b) {
        return b.canCastTo(Type.STRING) && b.asString().equals(value);
    }

    @Override
    public boolean isInteger() {
        return checkType() == 0;
    }

    @Override
    public KType copy() {
        return this;
    }

    @Override
    public boolean canCastTo(Type type) {
        switch (type) {
            case BOOL:
            case STRING:
                return true;
            case DOUBLE:
            case NUMBER:
                return checkType() >= 0;
        }
        return false;
    }

    private int checkType() {
        if (valueType == -2) {
            valueType = (byte) TextUtil.isNumber(this.value);
            if (valueType == -1) {
                switch (value) {
                    case "NaN":
                    case "Infinity":
                        valueType = 1;
                }
            }
        }
        return valueType;
    }

}
