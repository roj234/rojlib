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
    protected KBase() {}

    @Override
    public final String toString() {
        return toString0(new StringBuilder(), 0).toString();
    }

    protected static String optionalSlashes(String key) {
        for (int i = 0; i < key.length(); i++) {
            if (AbstLexer.SPECIAL_CHARS.contains(key.charAt(i))) {
                return '"' + AbstLexer.addSlashes(key) + '"';
            }
        }
        return key;
    }
}
