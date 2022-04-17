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

package ilib.asm;

import ilib.api.ContextClassTransformer;
import roj.asm.AccessTransformer;
import roj.asm.util.Context;

import java.util.Collection;

import static roj.asm.AccessTransformer.getTransforms;

/**
 * @author Roj234
 * @since 2021/5/29 16:43
 */
public class ATProxy implements ContextClassTransformer {
    public ATProxy() {
        Loader.addTransformer(this);
    }

    @Override
    public byte[] transform(String name, String trName, byte[] basicClass) {
        Loader.wrapTransformers();
        return AccessTransformer.transform(trName, basicClass);
    }

    @Override
    public void transform(String trName, Context ctx) {
        Collection<String> list = getTransforms().get(trName);
        if (list == null) return;
        if (ctx.isFresh()) {
            transform(null, trName, ctx.get().list);
        } else {
            AccessTransformer.openSome(list, ctx.getData());
        }
    }
}