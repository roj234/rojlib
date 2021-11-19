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
import roj.asm.nixim.NiximException;
import roj.asm.nixim.NiximSystem;
import roj.asm.util.Context;
import roj.util.Helpers;

import javax.annotation.Nonnull;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/18 9:51
 */
public final class NiximProxy extends NiximSystem implements ContextClassTransformer {
    public static final NiximProxy instance = new NiximProxy();
    public static boolean alreadyAtDeobfEnv;

    public static void read(@Nonnull final byte[] basicClass) {
        try {
            instance.load(basicClass);
        } catch (NiximException e) {
            Helpers.athrow(e);
        }
    }

    public static boolean removeByClass(String target) {
        return instance.remove(target);
    }

    private NiximProxy() {}

    @Override
    public void transform(String transformedName, Context context) {
        try {
            NiximData data = registry.remove(transformedName);
            if (data != null) {
                nixim(context, data);
            }
        } catch (NiximException e) {
            Helpers.athrow(e);
        }
    }
}