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

import javax.annotation.Nonnull;

/**
 * @author Roj234
 * @version 0.1
 * @since 2021/5/31 21:17
 */
public final class CBoolean extends CEntry {
    public static final CBoolean TRUE = new CBoolean(), FALSE = new CBoolean();

    public CBoolean() {}

    @Nonnull
    @Override
    public Type getType() {
        return Type.BOOL;
    }

    public static CEntry valueOf(boolean b) {
        return b ? TRUE : FALSE;
    }

    public static CEntry valueOf(String val) {
        return valueOf(Boolean.parseBoolean(val));
    }

    @Override
    public int asInteger() {
        return this == TRUE ? 1 : 0;
    }

    @Override
    public double asDouble() {
        return this == TRUE ? 1 : 0;
    }

    @Nonnull
    @Override
    public String asString() {
        return this == TRUE ? "true" : "false";
    }

    @Override
    public boolean asBool() {
        return this == TRUE;
    }

    @Override
    public boolean equalsTo(CEntry entry) {
        return entry.getType().fits(Type.BOOL) && entry.asBool() == asBool();
    }

    @Override
    public StringBuilder toYAML(StringBuilder sb, int depth) {
        return sb.append(this == TRUE);
    }

    @Override
    public StringBuilder toJSON(StringBuilder sb, int depth) {
        return sb.append(this == TRUE);
    }

    @Override
    public StringBuilder toINI(StringBuilder sb, int depth) {
        return sb.append(this == TRUE);
    }

    @Override
    public StringBuilder toTOML(StringBuilder sb, int depth) {
        return sb.append(this == TRUE);
    }

    @Override
    public Object toNudeObject() {
        return this == TRUE;
    }
}
