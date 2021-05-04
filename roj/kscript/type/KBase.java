package roj.kscript.type;

import roj.config.word.AbstLexer;

/**
 * This file is a part of MI <br>
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * @author Roj234
 * Filename: KBase.java
 */
public abstract class KBase implements KType {
    public KBase(Type type) {
        this.type = (byte) type.ordinal();
    }

    private final byte type;

    public Type getType() {
        return Type.VALUES[this.type];
    }

    @Override
    public final String toString() {
        return toString0(new StringBuilder(), 0).toString();
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
}
