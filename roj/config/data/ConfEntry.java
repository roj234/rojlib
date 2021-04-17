package roj.config.data;

import roj.config.word.AbstLexer;
import roj.config.word.Word;
import roj.config.word.WordPresets;

import javax.annotation.Nonnull;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: YAMLEntry.java
 */
public abstract class ConfEntry {
    public ConfEntry(Type type) {
        this.type = type;
    }

    private final Type type;

    public static ConfEntry of(Word word) {
        switch (word.type()) {
            case WordPresets.DECIMAL_D:
            case WordPresets.DECIMAL_F:
                return CDouble.valueOf(word.val());
            case WordPresets.INTEGER:
                return CInteger.valueOf(word.val());
            case WordPresets.STRING:
                return CString.valueOf(word.val());
            case WordPresets.VARIABLE:
                switch (word.val()) {
                    case "true":
                        return CBoolean.valueOf(true);
                    case "false":
                        return CBoolean.valueOf(false);
                    default:
                        return CString.valueOf(word.val());
                }
        }
        throw new IllegalArgumentException(String.valueOf(word));
    }

    @Nonnull
    public final Type getType() {
        return this.type;
    }

    @Nonnull
    public String asString() {
        throw new ClassCastException(getType() + " cannot cast to " + Type.STRING);
    }

    public int asNumber() {
        throw new ClassCastException(getType() + " cannot cast to " + Type.NUMBER);
    }

    public double asDouble() {
        throw new ClassCastException(getType() + " cannot cast to " + Type.DOUBLE);
    }

    @Nonnull
    public CMapping asMap() {
        throw new ClassCastException(getType() + " cannot cast to " + Type.MAP);
    }

    @Nonnull
    public CList asList() {
        throw new ClassCastException(getType() + " cannot cast to " + Type.LIST);
    }

    public CListMap asListMap() {
        throw new ClassCastException(getType() + " cannot cast to " + Type.LIST_MAP);
    }

    public boolean asBoolean() {
        throw new ClassCastException(getType() + " cannot cast to " + Type.BOOL);
    }

    public <T> CObject<T> asObject(Class<T> clazz) {
        throw new ClassCastException(getType() + " cannot cast to " + Type.SERIALIZED_OBJECT);
    }

    public abstract StringBuilder toYAML(StringBuilder sb, int depth);

    public final String toYAML() {
        return toYAML(new StringBuilder(), 0).toString();
    }

    public abstract StringBuilder toJSON(StringBuilder sb, int depth);

    public final String toJSON() {
        return toJSON(new StringBuilder(), 0).toString();
    }

    @Override
    public final String toString() {
        return toShortJSON();
    }

    protected static String addSlash(String key) {
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (AbstLexer.SPECIAL_CHARS.contains(c)) {
                return "\"" + AbstLexer.addSlashes(key) + "\"";
            }
        }
        return key;
    }

    protected boolean isSimilar(ConfEntry value) {
        return value.type == this.type;
    }

    public String toShortJSON() {
        return toJSON(new StringBuilder(), -9999999).toString();
    }
}
