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

import roj.net.cross.server.AEServer.Worker;
import roj.net.tcp.WrappedSocket;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.LockSupport;

import static roj.net.cross.Util.*;

/**
 * @author Roj233
 * @version 0.1
 * @since 2021/12/21 13:28
 */
final class ClientLogin extends Stated {
    static final ClientLogin CLIENT_LOGIN = new ClientLogin();

    @Override
    Stated next(Worker W) throws IOException {
        WrappedSocket ch = W.ch;

        ByteBuffer rb = ch.buffer();
        rb.clear();

        int heart = TIMEOUT_HEART_SERVER;
        int except = 1;
        while (!W.server.shutdown) {
            int read;
            if ((read = ch.read(except - rb.position())) == 0 || rb.position() < except) {
                LockSupport.parkNanos(50);
                if (heart-- < 0) {
                    syncPrint(W + ": 登录超时");
                    write1(ch, (byte) PS_ERROR_TIMEOUT);
                    break;
                }
                continue;
            }

            if (read < 0) break;
            switch (rb.get(0) & 0xFF) {
                case PS_LOGIN_C:
                    if (rb.position() < 3) {
                        except = 3;
                        continue;
                    }
                    int nameLen = rb.get(1) & 0xFF;
                    int passLen = rb.get(2) & 0xFF;
                    if (rb.position() < nameLen + passLen + 3) {
                        except = nameLen + passLen + 3;
                        continue;
                    }
                    rb.position(3);

                    int code = W.server.createRoom(W,
                                                   false,
                                                   getUTF(rb, nameLen),
                                                   getUTF(rb, passLen));
                    if (code != -1) {
                        syncPrint(W + ": 连接失败(协议): " + ERROR_NAMES[code - 0x20]);
                        write1(ch, (byte) code);
                        return Logout.LOGOUT;
                    }

                    rb.clear();
                    rb.put((byte) PC_LOGON_C)
                      .put((byte) W.server.info.length)
                      .put((byte) W.room.motd.length)
                      .put((byte) (W.room.portMap.length / 2))
                      .putInt(W.clientId).put(W.server.info)
                      .put(W.room.motd).put(W.room.portMap).flip();
                    writeAndFlush(ch, rb, 500);

                    byte[] addr = ch.socket().getInetAddress().getAddress();
                    rb.clear();
                    rb.put((byte) PH_CLIENT_LOGIN)
                      .putInt(W.clientId)
                      .putShort((short) ch.socket().getPort())
                      .put((byte) addr.length)
                      .put(addr).flip();

                    W.room.master.sync(rb);
                    return ClientWork.CLIENT_WORK;
                case P_LOGOUT:
                    rb.clear();
                    syncPrint(W + ": 断开连接(协议)");
                    return Logout.LOGOUT;
                default:
                    unknownPacket(W, rb);
                    return Logout.LOGOUT;
            }
        }

        return Logout.LOGOUT;
    }
}
