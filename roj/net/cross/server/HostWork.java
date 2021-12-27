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

import roj.net.cross.server.AEServer.PipeGroup;
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
final class HostWork extends Stated {
    static final HostWork HOST_WORK = new HostWork();

    @Override
    Stated next(Worker W) throws IOException {
        WrappedSocket ch = W.ch;

        ByteBuffer rb = ch.buffer();
        rb.clear();

        int heart = TIMEOUT_HEART_SERVER;
        int except = 1;
        while (!W.server.shutdown && isInRoom(W)) {
            int read;
            if ((read = ch.read(except - rb.position())) == 0 && rb.position() < except) {
                W.pollPackets();
                LockSupport.parkNanos(20);
                if (heart-- < 0) {
                    syncPrint(this + ": 心跳超时");
                    write1(ch, (byte) PS_ERROR_TIMEOUT);
                    break;
                }
                continue;
            }

            if (read < 0) break;
            switch (rb.get(0) & 0xFF) {
                case P_HEARTBEAT:
                    W.lastHeart = System.currentTimeMillis();
                    break;
                case P_LOGOUT:
                    rb.clear();
                    syncPrint(W + ": 断开连接(协议)");
                    return Logout.LOGOUT;
                case PS_KICK_CLIENT:
                    if (rb.position() < 5) {
                        except = 5;
                        continue;
                    }
                    except = 1;

                    int clientId = rb.getInt(1);
                    Worker w = W.room.clients.remove(clientId);
                    if (w == null) syncPrint(W + ": PKC 无效的客户端");
                    break;
                case PS_CHANNEL_OPEN:
                    if (rb.position() < 37) {
                        except = 37;
                        continue;
                    }
                    except = 1;

                    clientId = rb.getInt(1);
                    w = W.room.clients.get(clientId);
                    if (w != null) {
                        rb.position(5);
                        byte[] rnd2 = new byte[32];
                        rb.get(rnd2).clear();
                        w.sync(rb.put((byte) P_CHANNEL_RESULT)
                                 .put(rnd2).putInt(0)
                                 .putLong(w.getPendingPipeId()));
                    } else syncPrint(W + ": PCO 无效的客户端");

                    break;
                case P_CHANNEL_OPEN_FAIL:
                    if (rb.position() < 6) {
                        except = 6;
                        continue;
                    }
                    if (rb.position() < (rb.get(5) & 0xFF) + 6) {
                        except = (rb.get(5) & 0xFF) + 6;
                        continue;
                    }
                    except = 1;

                    clientId = rb.getInt(1);
                    w = W.room.clients.get(clientId);
                    if (w != null) {
                        w.sync(rb.putInt(1, 0));
                    } else syncPrint(W + ": PCOF 无效的客户端");
                    break;
                case PS_CHANNEL_CLOSE:
                    if (rb.position() < 5) {
                        except = 5;
                        continue;
                    }
                    except = 1;

                    int pipeId = rb.getInt(1);
                    rb.limit(9);
                    rb.putInt(1, W.clientId)
                      .putInt(5, pipeId);
                    w = W.closePipe(pipeId);
                    if (w != null) w.sync(rb);
                    break;
                case P_CHANNEL_OP:
                    if (rb.position() < 6) {
                        except = 6;
                        continue;
                    }
                    except = 1;

                    pipeId = rb.getInt(1);
                    PipeGroup group = W.getPipe(pipeId);
                    if (group == null || group.pairRef == null) {
                        syncPrint(W + ": PCOP 无效的管道: " + pipeId);
                        break;
                    }
                    switch (rb.get(2) & 0xFF) {
                        case OP_SET_ACTIVE:
                            group.pairRef.setActive();
                            break;
                        case OP_SET_INACTIVE:
                            group.pairRef.setInactive();
                            break;
                    }
                    group.downOwner.sync(rb);
                    break;
                default:
                    unknownPacket(W, rb);
                    return Logout.LOGOUT;
            }
            write1(ch, (byte) P_HEARTBEAT);
            heart = TIMEOUT_HEART_SERVER;
            rb.clear();
        }

        return Logout.LOGOUT;
    }
}
