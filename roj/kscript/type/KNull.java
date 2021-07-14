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

import roj.kscript.api.IObject;
import roj.kscript.util.opm.ObjectPropMap;

import javax.annotation.Nonnull;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/30 17:19
 */
public final class KNull extends KObject {
    public static final KNull NULL = new KNull();

    private KNull() {
        super(new ObjectPropMap() {
            @Override
            public Entry<String, KType> getOrCreateEntry(String id) {
                throw new NullPointerException("null cannot cast to object");
            }

            @Override
            public Entry<String, KType> getEntry(String id) {
                throw new NullPointerException("null cannot cast to object");
            }
        }, null);
    }

    @Override
    public Type getType() {
        return Type.NULL;
    }

    @Override
    public StringBuilder toString0(StringBuilder sb, int depth) {
        return sb.append("<null>");
    }

    @Override
    public KType copy() {
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        return obj == NULL;
    }

    @Override
    public int hashCode() {
        return 0;
    }

    @Override
    public boolean isInstanceOf(IObject obj) {
        return false;
    }

    @Override
    public void put(@Nonnull String key, KType entry) {
        throw new NullPointerException("null cannot cast to object");
    }

    @Override
    public KType getOr(String key, KType def) {
        throw new NullPointerException("null cannot cast to object");
    }

    @Override
    public IObject getProto() {
        throw new NullPointerException("null cannot cast to object");
    }

    @Override
    public boolean asBool() {
        return false;
    }
}
