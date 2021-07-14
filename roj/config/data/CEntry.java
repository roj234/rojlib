/*
 * This file is a part of MI
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 Roj234
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package roj.config.data;

import roj.config.word.AbstLexer;

import javax.annotation.Nonnull;

/**
 * Config Entry
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/31 21:17
 */
public abstract class CEntry {
    protected CEntry() {}

    @Nonnull
    public abstract Type getType();

    ////// easy caster

    @Nonnull
    public String asString() {
        throw new ClassCastException(getType() + " unable cast to 'string'");
    }

    public int asInteger() {
        throw new ClassCastException(getType() + " unable cast to 'int'");
    }

    public double asDouble() {
        throw new ClassCastException(getType() + " unable cast to 'double'");
    }

    public long asLong() {
        throw new ClassCastException(getType() + " unable cast to 'long'");
    }

    @Nonnull
    public CMapping asMap() {
        throw new ClassCastException(getType() + " unable cast to 'map'");
    }

    @Nonnull
    public CList asList() {
        throw new ClassCastException(getType() + " unable cast to 'list'");
    }

    public boolean asBoolean() {
        throw new ClassCastException(getType() + " unable cast to 'boolean'");
    }

    public <T> CObject<T> asObject(Class<T> clazz) {
        throw new ClassCastException(getType() + " unable cast to 'java_object'");
    }

    ////// toString methods

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

    public final String toShortJSON() {
        return toJSON(new StringBuilder(), -9999999).toString();
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

    protected boolean isSimilar(CEntry value) {
        return value.getType() == this.getType();
    }
}
