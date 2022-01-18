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
package roj.net.cross.server;

import roj.net.WrappedSocket;
import roj.net.cross.server.AEServer.PipeGroup;
import roj.net.cross.server.AEServer.Worker;
import roj.net.misc.Pipe;
import roj.net.misc.PipeIOThread;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import static roj.net.cross.Util.*;

/**
 * @author Roj233
 * @version 0.1
 * @since 2021/12/23 22:14
 */
final class PipeLogin extends Stated {
    public static final PipeLogin PIPE_LOGIN = new PipeLogin();
    public static final Stated PIPE_OK = new PipeLogin();

    @Override
    Stated next(Worker W) throws IOException {
        WrappedSocket ch = W.ch;

        ByteBuffer rb = ch.buffer();
        rb.clear();

        int heart = TIMEOUT;
        while (!W.server.shutdown) {
            int read;
            if ((read = ch.read(8 - rb.position())) == 0 && rb.position() < 8) {
                LockSupport.parkNanos(10000);
                if (heart-- < 0) {
                    syncPrint(W + ": 登录超时");
                    break;
                }
                continue;
            }

            if (read < 0) break;
            Integer user = rb.getInt(0);
            int pass = rb.getInt(4);

            PipeGroup group = W.server.pipes.get(user);
            if (group == null || pass == 0) syncPrint(W + ": 无效的管道 " + user + "@" + pass);
            else {
                Pipe pipe = group.pairRef;
                if (group.upPass == pass) {
                    pipe.setUpstream(ch.fd());
                    group.upPass = 0;
                    if (DEBUG) syncPrint(W + ": " + user + " up logon");
                } else if (group.downPass == pass) {
                    pipe.setClient(ch.fd());
                    group.downPass = 0;
                    if (DEBUG) syncPrint(W + ": " + user + " down logon");
                } else {
                    syncPrint(W + ": " + user + " 密码无效");
                    break;
                }
                if (group.upPass == group.downPass) {
                    W.server.pipes.remove(user);
                    group.downOwner.pendingPipeOpen();

                    AtomicInteger i = W.server.remain;
                    try {
                        PipeIOThread.syncRegister(W.server, pipe, p -> {
                            i.addAndGet(2);
                            PipeGroup group1 = (PipeGroup) p.att;
                            try {
                                group1.close(-1);
                            } catch (IOException ignored) {}
                        });
                    } catch (IOException e) {
                        throw e;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                return PIPE_OK;
            }
            break;
        }
        return null;
    }
}
