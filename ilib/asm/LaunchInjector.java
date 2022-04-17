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

import ilib.Config;
import ilib.api.ContextClassTransformer;
import roj.asm.SharedBuf;
import roj.asm.SharedBuf.Level;
import roj.asm.util.Context;
import roj.util.ByteList;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader.Acceptor;
import net.minecraft.launchwrapper.LaunchClassLoader.Reader;

import java.util.List;

/**
 * @author Roj233
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

        Level level = SharedBuf.alloc();
        level.setLevel(true);
        try {
            if (rd.buf1 == null) {
                for (int i = 0; i < transformers.size(); i++) {
                    IClassTransformer o = transformers.get(i);
                    rd.buf1 = o.transform(name, trName, rd.buf1);
                }
                rd.pos1 = rd.buf1 == null ? -1 : rd.buf1.length;
            } else {
                ByteList shared = new ByteList(rd.buf1);
                Context ctx = new Context(name, shared);

                for (int i = 0; i < transformers.size(); i++) {
                    Object o = ((List<?>) transformers).get(i);
                    if (o instanceof ContextClassTransformer) {
                        ((ContextClassTransformer) o).transform(trName, ctx);
                    } else {
                        shared.setArray(((IClassTransformer) o).transform(name, trName, ctx.get().toByteArray()));
                        ctx.set(shared);
                    }
                }

                ByteList list = ctx.get();
                rd.buf1 = list.list;
                rd.pos1 = list.wIndex();
            }
        } catch (Throwable e) {
            if ((Config.debug & 64) != 0) e.printStackTrace();
            throw e;
        } finally {
            level.setLevel(false);
        }
    }
}
