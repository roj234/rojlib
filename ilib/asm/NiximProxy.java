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
import roj.asm.TransformException;
import roj.asm.nixim.NiximSystem;
import roj.asm.util.Context;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.util.Helpers;
import sun.reflect.Reflection;

import javax.annotation.Nonnull;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public final class NiximProxy extends NiximSystem implements ContextClassTransformer {
    public static final NiximProxy instance = new NiximProxy();

    static void Nx(String file) {
        if (file.charAt(0) == '!') {
            CharList sb = IOUtil.getSharedCharBuf();
            sb.append("ilib/asm/nixim/").append(file, 1, file.length());
            if (!file.endsWith(".class")) sb.append(".class");
            file = sb.toString();
        }

        try {
            Class<?> caller = NiximProxy.class;
            try {
                caller = Reflection.getCallerClass();
            } catch (Throwable ignored) {}
            InputStream in = caller.getClassLoader().getResourceAsStream(file);
            if (in == null) throw new FileNotFoundException(file);

            Context ctx = new Context("", IOUtil.getSharedByteBuf().readStreamFully(in));
            Loader.logger.info("NiximRead " + ctx.getData().name);
            instance.loadCtx(ctx);
        } catch (TransformException | IOException e) {
            Helpers.athrow(e);
        }
    }

    public static void Nx(@Nonnull byte[] data) {
        try {
            instance.load(data);
        } catch (TransformException e) {
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
            CharList r = IOUtil.getSharedCharBuf()
                               .append(transformedName)
                               .replace('.', '/');

            NiximData data = registry.get(r);
            if (data != null) {
                synchronized (registry) {
                    data = registry.remove(r);
                }
                if (data != null) {
                    // 历史遗留问题+让代码保持简洁
                    nixim(context, data, NO_FIELD_MODIFIER_CHECK | NO_METHOD_MODIFIER_CHECK);
                }
            }
        } catch (TransformException e) {
            Helpers.athrow(e);
        }
    }
}