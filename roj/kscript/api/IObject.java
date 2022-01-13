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
package roj.kscript.api;

import roj.kscript.func.KFunction;
import roj.kscript.type.KType;
import roj.kscript.type.KUndefined;
import roj.kscript.type.Type;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/10/27 13:11
 */
public interface IObject extends KType {
    void put(String key, KType entry);

    @Nullable
    default KType getOrNull(String key) {
        return getOr(key, null);
    }

    @Nonnull
    default KType get(String key) {
        return getOr(key, KUndefined.UNDEFINED);
    }

    default boolean chmod(String key, boolean configurable, boolean enumerable, KFunction getter, KFunction setter) {
        return false;
    }

    default boolean delete(String key) {
        return false;
    }

    default boolean containsKey(@Nullable String key) {
        return getOr(key, null) != null;
    }

    default boolean containsKey(@Nullable String key, @Nonnull Type type) {
        return get(key).canCastTo(type);
    }

    boolean isInstanceOf(IObject obj);

    IObject getProto();

    KType getOr(String key, KType def);

    int size();

    default Object getInternal() {
        throw new UnsupportedOperationException();
    }

    @Nonnull
    @Override
    default String asString() {
        return "[object " + getClass().getSimpleName() + ']';
    }

    @Nonnull
    @Override
    default IObject asObject() {
        return this;
    }
}
