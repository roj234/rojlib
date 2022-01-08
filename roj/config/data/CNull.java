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

import roj.util.ByteList;

import javax.annotation.Nonnull;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/31 21:17
 */
public final class CNull extends CEntry {
    public static final CNull NULL = new CNull();

    private CNull() {}

    @Nonnull
    @Override
    public Type getType() {
        return Type.NULL;
    }

    @Nonnull
    public String asString() {
        return "";
    }

    @Override
    public double asDouble() {
        return 0;
    }

    @Override
    public boolean asBool() {
        return false;
    }

    @Override
    public int asInteger() {
        return 0;
    }

    @Override
    public long asLong() {
        return 0;
    }

    @Nonnull
    @Override
    public CMapping asMap() {
        return new CMapping();
    }

    @Nonnull
    @Override
    public CList asList() {
        return new CList();
    }

    @Nonnull
    @Override
    public <T> CObject<T> asObject(Class<T> clazz) {
        return new CObject<>((T) null);
    }

    @Override
    public StringBuilder toYAML(StringBuilder sb, int depth) {
        return sb.append("null");
    }

    @Override
    public StringBuilder toJSON(StringBuilder sb, int depth) {
        return sb.append("null");
    }

    @Override
    public StringBuilder toINI(StringBuilder sb, int depth) {
        return sb.append("null");
    }

    @Override
    public StringBuilder toTOML(StringBuilder sb, int depth, CharSequence chain) {
        return sb.append("null");
    }

    @Override
    public Object unwrap() {
        return null;
    }

    @Override
    public void toBinary(ByteList w) {
        w.put((byte) Type.NULL.ordinal());
    }

    @Override
    protected boolean isSimilar(CEntry value) {
        return false;
    }

    @Override
    public boolean equals(Object obj) {
        return obj == NULL;
    }

    @Override
    public int hashCode() {
        return 348764;
    }
}
