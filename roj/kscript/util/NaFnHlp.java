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
package roj.kscript.util;

import roj.kscript.Constants;
import roj.kscript.api.ArgList;
import roj.kscript.api.IObject;
import roj.kscript.func.KFuncNative;
import roj.kscript.type.KObject;
import roj.kscript.type.KType;
import roj.kscript.type.KUndefined;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/10/31 0:06
 */
public final class NaFnHlp {
    private final KObject object;
    private final NaFnHlp parent;

    private NaFnHlp(NaFnHlp parent) {
        this.object = new KObject(parent == null ? null : Constants.OBJECT);
        this.parent = parent;
    }

    public static NaFnHlp builder() {
        return new NaFnHlp(null);
    }

    public NaFnHlp returnVoid(String name, Consumer<ArgList> consumer) {
        return with(name, new KFuncNative() {
                    @Override
                    public KType invoke(@Nonnull IObject $this, ArgList param) {
                        consumer.accept(param);
                        return KUndefined.UNDEFINED;
                    }
        });
    }

    public NaFnHlp with(String name, KFuncNative func) {
        object.put(name, func);
        return this;
    }

    public NaFnHlp put(String name, KType some) {
        object.put(name, some);
        return this;
    }

    public NaFnHlp sub() {
        return new NaFnHlp(this);
    }

    public NaFnHlp endSub(String name) {
        parent.object.put(name, object);
        return parent;
    }

    public KObject build() {
        return object;
    }
}
