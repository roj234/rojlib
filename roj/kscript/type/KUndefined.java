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

import javax.annotation.Nonnull;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/28 22:12
 */
public final class KUndefined extends KBase {
    public static final KUndefined UNDEFINED = new KUndefined();

    private KUndefined() {}

    @Override
    public Type getType() {
        return Type.UNDEFINED;
    }

    @Override
    public StringBuilder toString0(StringBuilder sb, int depth) {
        return sb.append("undefined");
    }

    @Override
    public boolean equalsTo(KType b) {
        return b == UNDEFINED || b == KNull.NULL;
    }

    @Override
    public boolean equals(Object obj) {
        return obj == UNDEFINED;
    }

    @Override
    public boolean asBool() {
        return false;
    }

    @Nonnull
    @Override
    public String asString() {
        return "undefined";
    }

    @Override
    public int hashCode() {
        return 235789;
    }

    @Override
    public boolean canCastTo(Type type) {
        return false;
    }
}
