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
package roj.kscript.type;

import roj.config.word.AbstLexer;
import roj.math.MathUtils;
import roj.text.TextUtil;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/28 23:38
 */
public final class KString extends KBase {
    private final String value;
    private byte vt = -2;

    public KString(String string) {
        this.value = string;
    }

    @Override
    public Type getType() {
        return Type.STRING;
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
            } catch (NumberFormatException ignored) {}
        }
        return super.asDouble();
    }

    @Override
    public int asInt() {
        if (checkType() >= 0) {
            try {
                return MathUtils.parseInt(value);
            } catch (NumberFormatException ignored) {}
        }
        return super.asInt();
    }

    @Override
    public boolean isString() {
        return checkType() == -1;
    }

    @Override
    public boolean asBool() {
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
    public boolean isInt() {
        return checkType() == 0;
    }

    @Override
    public boolean canCastTo(Type type) {
        switch (type) {
            case BOOL:
            case STRING:
                return true;
            case DOUBLE:
            case INT:
                return checkType() >= 0;
        }
        return false;
    }

    private int checkType() {
        if (vt == -2) {
            vt = (byte) TextUtil.isNumber(this.value);
            if (vt == -1) {
                switch (value) {
                    case "NaN":
                    case "Infinity":
                        vt = 1;
                }
            }
        }
        return vt;
    }

}
