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
final class ClientWork extends Stated {
    static final ClientWork CLIENT_WORK = new ClientWork();

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
                case PS_REQUEST_CHANNEL:
                    if (rb.position() < 34) {
                        except = 34;
                        continue;
                    }
                    except = 1;

                    int[] tmp = new int[2];
                    String refused = W.generatePipe(tmp);
                    if (refused != null) {
                        syncPrint(W + ": 拒绝创建管道: " + refused);
                        openFail(rb, refused);
                        writeAndFlush(ch, rb, 500);
                        break;
                    }

                    rb.put(0, (byte) P_CHANNEL_RESULT)
                      .putInt(W.clientId)
                      .putInt(tmp[0]).putInt(tmp[1]).flip(); // target is up

                    W.room.master.sync(rb);
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
                    Worker w = W.closePipe(pipeId);
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
                        syncPrint(W + ": 1 无效 #" + pipeId);
                        write1(ch, (byte) P_FAIL);
                        break;
                    }
                    switch (rb.get() & 0xFF) {
                        case OP_SET_ACTIVE:
                            group.pairRef.setActive();
                            break;
                        case OP_SET_INACTIVE:
                            group.pairRef.setInactive();
                            break;
                    }
                    break;
                case P_MSG:
                    if (rb.position() < 6) {
                        except = 6;
                        continue;
                    }
                    if (rb.position() < (rb.get(5) & 0xFF) + 6) {
                        except = (rb.get(5) & 0xFF) + 6;
                        continue;
                    }
                    except = 1;

                    int target = rb.getInt(1);
                    Worker to = W.room.clients.get(target);
                    if (null == to) {
                        syncPrint(W + ": 2 无效 " + target);
                        write1(ch, (byte) P_FAIL);
                        break;
                    }
                    rb.putInt(1, W.clientId).flip();
                    to.sync(rb);
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
