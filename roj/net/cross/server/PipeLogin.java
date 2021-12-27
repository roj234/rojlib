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

import roj.net.cross.Pipe;
import roj.net.cross.PipeIOThread;
import roj.net.cross.server.AEServer.PipeGroup;
import roj.net.cross.server.AEServer.Worker;
import roj.net.tcp.WrappedSocket;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.LockSupport;

import static roj.net.cross.Util.TIMEOUT_HEART_SERVER;
import static roj.net.cross.Util.syncPrint;

/**
 * @author Roj233
 * @version 0.1
 * @since 2021/12/23 22:14
 */
final class PipeLogin extends Stated {
    public static final PipeLogin PIPE_LOGIN = new PipeLogin();

    @Override
    Stated next(Worker W) throws IOException {
        WrappedSocket ch = W.ch;

        ByteBuffer rb = ch.buffer();
        rb.clear();

        int heart = TIMEOUT_HEART_SERVER;
        while (!W.server.shutdown) {
            int read;
            if ((read = ch.read(14 - rb.position())) == 0 && rb.position() < 14) {
                LockSupport.parkNanos(20);
                if (heart-- < 0) {
                    syncPrint(W + ": 登录超时");
                    break;
                }
                continue;
            }

            if (read < 0) break;
            Integer user = rb.getInt(6);
            int pass = rb.getInt(10);

            PipeGroup group = W.server.pipes.get(user);
            if (group == null) syncPrint(W + ": 无效的管道ID");
            else {
                if (group.upPass != null && group.upPass == pass) {
                    group.upConnFD = ch.fd();
                    W.ch = null;
                } else if (group.downPass != null && group.downPass == pass) {
                    group.downConnFD = ch.fd();
                    W.ch = null;
                } else {
                    syncPrint(W + ": 无效的管道密码");
                    break;
                }
                if (group.downPass == null && group.upPass == null) {
                    W.server.pipes.remove(user);
                    group.life = 1;
                    Pipe sp = new Pipe(group.downConnFD, group.upConnFD);
                    group.pairRef = sp;

                    PipeIOThread.syncRegister(W.server, sp, null);
                }
            }
            break;
        }
        return null;
    }
}
