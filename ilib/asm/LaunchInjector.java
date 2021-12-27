/*
 * This file is a part of MoreItems
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
import roj.asm.SharedBuf;
import roj.asm.SharedBuf.Level;
import roj.asm.util.Context;
import roj.util.ByteList;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader.Acceptor;
import net.minecraft.launchwrapper.LaunchClassLoader.Reader;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * @author Roj233
 * @version 0.1
 * @since 2021/10/21 9:45
 */
class LaunchInjector implements Acceptor {
    static void patch() throws NoClassDefFoundError, NoSuchMethodError {
        Launch.classLoader.setAcceptorIL(new LaunchInjector());
    }

    @Override
    public void accept(List<IClassTransformer> transformers, String name, String trName, Object obj) {
        long cn = System.nanoTime();

        Reader rd = (Reader) obj;

        ByteList shared = (ByteList) rd.threadLocalForYou;
        if (shared == null)
            rd.threadLocalForYou = shared = new ByteList();

        shared.setValue(rd.buf1);
        Context ctx = new Context(name, shared);

        Level level = SharedBuf.alloc();
        level.setLevel(true);
        try {
            for (int i = 0; i < transformers.size(); i++) {
                try {
                    Object o = ((List<?>) transformers).get(i);
                    if (o instanceof ContextClassTransformer) {
                        ((ContextClassTransformer) o).transform(trName, ctx);
                    } else {
                        shared.setValue(((IClassTransformer) o).transform(name, trName, ctx.get(true).getByteArray()));
                        ctx.set(shared);
                    }
                } catch (Throwable e) {
                    try (FileOutputStream out = new FileOutputStream(trName.replace('/', '.'))) {
                        ctx.get(true).writeToStream(out);
                    } catch (IOException ignored) {}
                    throw e;
                }
            }

            ByteList list = ctx.get(true);
            rd.buf1 = list.list;
            rd.pos1 = list.wIndex();
        } finally {
            level.setLevel(false);
            Loader.classLoadElapse = System.nanoTime() - cn;
        }
    }
}
