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
package roj.net.misc;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.function.Consumer;

/**
 * Pipe network IO threads
 * @author Roj233
 * @since 2021/12/24 23:27
 */
public final class PipeIOThread extends NIOSelectLoop<Pipe> {
    private static PipeIOThread inst;

    private PipeIOThread(Shutdownable owner) {
        super(owner,"Pipe IO", Runtime.getRuntime().availableProcessors(), 60000, 100);
    }

    @Override
    public void unregister(Pipe pipe) {
        if (pipe.upKey != null)
            pipe.upKey.cancel();
        if (pipe.downKey != null)
            pipe.downKey.cancel();
        pipe.upKey = pipe.downKey = null;
    }

    @Override
    protected void register1(Selector sel, Pipe pipe, Object att) throws IOException {
        if (pipe.upstream != null) {
            FileDescriptorChannel uc = new FileDescriptorChannel(pipe.upstream);
            pipe.upKey = uc.register(sel, SelectionKey.OP_READ, att);
        }
        if (pipe.downstream != null) {
            FileDescriptorChannel dc = new FileDescriptorChannel(pipe.downstream);
            pipe.downKey = dc.register(sel, SelectionKey.OP_READ, att);
        }
    }

    public static void syncRegister(Shutdownable svr, Pipe p, Consumer<Pipe> cb) throws Exception {
        if (inst == null) {
            synchronized (PipeIOThread.class) {
                if (inst == null) {
                    inst = new PipeIOThread(svr);
                }
            }
        }
        inst.register(p, cb);
    }
}
