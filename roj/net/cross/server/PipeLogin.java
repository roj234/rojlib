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

        int heart = TIMEOUT_HEART_SERVER;
        while (!W.server.shutdown) {
            int read;
            if ((read = ch.read(8 - rb.position())) == 0 && rb.position() < 8) {
                LockSupport.parkNanos(20);
                if (heart-- < 0) {
                    syncPrint(W + ": 登录超时");
                    break;
                }
                continue;
            }

            if (read < 0) break;
            Integer user = rb.getInt(0);
            W.clientId = user;
            int pass = rb.getInt(4);

            PipeGroup group = W.server.pipes.get(user);
            if (group == null) syncPrint(W + ": 无效的管道 @" + pass);
            else {
                if (group.upConnFD == null && group.upPass == pass) {
                    group.upConnFD = ch.fd();
                    group.upPass = 0;
                } else if (group.downConnFD == null && group.downPass == pass) {
                    group.downConnFD = ch.fd();
                    group.downPass = 0;
                } else {
                    syncPrint(W + " 密码无效");
                    break;
                }
                if (group.upConnFD != null && group.downConnFD != null) {
                    W.server.pipes.remove(user);
                    group.life = 1;
                    Pipe sp = new Pipe(group.downConnFD, group.upConnFD);
                    sp.att = group;
                    group.pairRef = sp;
                    group.downOwner.pendingPipeOpen();

                    //syncPrint(" 管道 #" + user + " 已开启");
                    AtomicInteger i = W.server.remain;
                    try {
                        PipeIOThread.syncRegister(W.server, sp, pipe -> {
                            i.addAndGet(2);
                            try {
                                pipe.release();
                                syncPrint("管道 #" + user + " 已终止");
                            } catch (IOException e) {
                                System.err.println("无法释放管道 #" + user);
                                e.printStackTrace();
                            }
                            PipeGroup pg = (PipeGroup) pipe.att;

                            ByteBuffer packet = ByteBuffer.allocate(1 + 4 + 4);
                            packet.put((byte) P_CHANNEL_CLOSE).putInt(-1).putInt(pg.id).flip();

                            if (pg.downOwner.getPipe(pg.id) != null) {
                                pg.downOwner.closePipe(pg.id);
                                pg.downOwner.sync(packet);
                                packet.position(0);
                            }

                            Worker upOwner = pg.downOwner.room.master;
                            if (upOwner.getPipe(pg.id) != null) {
                                upOwner.closePipe(pg.id);
                                upOwner.sync(packet);
                            }

                            pg.pairRef = null;
                            pg.downConnFD = pg.upConnFD = null;
                        });
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
