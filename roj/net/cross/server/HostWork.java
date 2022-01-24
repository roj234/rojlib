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
import roj.net.cross.Util;

import java.io.IOException;
import java.nio.ByteBuffer;

import static roj.net.cross.Util.*;

/**
 * @author Roj233
 * @since 2021/12/21 13:28
 */
final class HostWork extends ClientWork {
    static final HostWork HOST_WORK = new HostWork();

    @Override
    Stated next(Client W) throws IOException {
        if (!isInRoom(W)) return Logout.LOGOUT;

        WrappedSocket ch = W.ch;
        ByteBuffer rb = ch.buffer();

        if (System.currentTimeMillis() - W.lastPacket > T_HEART_TIMEOUT) {
            syncPrint(W + ": 心跳超时");
            write1(ch, (byte) PS_ERROR_TIMEOUT);
            return Logout.LOGOUT;
        }

        int except = W.st1;
        do {
            switch (readOrAwait(ch, except)) {
                case -1:
                    return null;
                case 0:
                    W.st1 = except;
                    return this;
            }

            W.lastPacket = System.currentTimeMillis();
            switch (rb.get(0) & 0xFF) {
                case P_HEARTBEAT:
                    break;
                case P_LOGOUT:
                    rb.clear();
                    return Logout.REQUESTED;
                case PS_KICK_CLIENT:
                    if (rb.position() < 5) {
                        except = 5;
                        continue;
                    }

                    int clientId = rb.getInt(1);
                    Client w = W.room.clients.remove(clientId);
                    if (w == null)
                        if (Util.DEBUG) syncPrint(W + ": 踢出客户端: 无效客户端 #" + clientId);
                    break;
                case PS_CHANNEL_OPEN:
                    if (rb.position() < 37) {
                        except = 37;
                        continue;
                    }

                    clientId = rb.getInt(1);
                    w = W.room.clients.get(clientId);
                    if (w != null) {
                        rb.position(5);
                        byte[] rnd2 = new byte[32];
                        rb.get(rnd2).clear();
                        PipeGroup pending = w.pending;
                        rb.put((byte) P_CHANNEL_RESULT)
                          .put(rnd2).putLong(((long)pending.id << 32) | (pending.downPass & 0xFFFF_FFFFL))
                          .flip();
                        w.sync(rb);
                    } else
                        if (Util.DEBUG) syncPrint(W + ": 管道开启: 无效客户端 #" + clientId);

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

                    clientId = rb.getInt(1);
                    w = W.room.clients.get(clientId);
                    if (w != null) {
                        rb.flip();
                        w.sync(rb.putInt(1, 0));
                    } else
                        if (Util.DEBUG) syncPrint(W + ": 管道失败: 无效客户端 #" + clientId);
                    break;
                case P_CHANNEL_CLOSE:
                    if (rb.position() < 5) {
                        except = 5;
                        continue;
                    }
                    W.closePipe(rb.getInt(1));
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
                    chat(W, ch, rb);
                    break;
                case P_MSG_LONG:
                    if (rb.position() < 7) {
                        except = 7;
                        continue;
                    }
                    char len = rb.getChar(6);
                    if (len > 9999) {
                        syncPrint(W + ": 发送的消息过长(9999 bytes)");
                        write1(ch, (byte) PS_ERROR_SYSTEM_LIMIT);
                        return Logout.LOGOUT;
                    }
                    if (rb.position() < len + 7) {
                        except = len + 7;
                        continue;
                    }
                    chat(W, ch, rb);
                    break;
                case P_MSG_OP:
                    if (rb.position() < 6) {
                        except = 6;
                        continue;
                    }
                    switch (rb.get(1) & 0xFF) {
                        case SPEC_OP_ENABLE_CHAT:
                            W.room.chatEnabled.add(W.clientId);
                            break;
                        case SPEC_OP_DISABLE_CHAT:
                            W.room.chatEnabled.remove(W.clientId);
                            break;
                        case SPEC_OP_GET_ONLINES:
                            // todo generate online packet
                            break;
                        case SPEC_OP_GET_RECENT_MESSAGE:
                            break;
                        default:
                            write1(ch, (byte) P_FAIL);
                            break;
                    }
                    break;
                case P_UPNP_PING:
                    if (rb.position() < 12) {
                        except = 12;
                        continue;
                    }
                    if (rb.position() < (rb.get(11) & 0xFF) + 12) {
                        except = (rb.get(11) & 0xFF) + 12;
                        continue;
                    }
                    rb.position(1).flip();
                    long sec = rb.getLong();
                    char port = rb.getChar();
                    byte[] ip = new byte[rb.get() & 0xFF];
                    rb.get(ip);

                    UPnPPingTask task;
                    if (W.task != null || (task = W.ping(ip, port, sec)) == null) {
                        rb.put(1, (byte) -3).limit(2);
                        writeAndFlush(ch, rb, 500);
                    } else {
                        W.task = task;
                    }
                    break;
                case P_UPNP_PONG:
                    if (rb.position() < 4) {
                        except = 4;
                        continue;
                    }
                    if (rb.position() < (rb.get(3) & 0xFF) + 4) {
                        except = (rb.get(3) & 0xFF) + 4;
                        continue;
                    }
                    byte[] buf = new byte[rb.position()];
                    rb.flip();
                    rb.get(buf);
                    W.room.upnpAddress = buf;
                    // 同步到客户端
                    synchronized (W.room.clients) {
                        for (Client wk : W.room.clients.values()) {
                            rb.position(0);
                            if (wk != W) wk.sync(rb);
                        }
                    }
                    break;
                case P_CHANNEL_RESET:
                    if (rb.position() < 5) {
                        except = 5;
                        continue;
                    }
                    if (Util.DEBUG) syncPrint(W + ": Signal #" + rb.getInt(1));
                    W.room.removePending(rb.getInt(1));
                    break;
                default:
                    unknownPacket(W, rb);
                    return Logout.LOGOUT;
            }
        } while (rb.position() < except);
        if (DEBUG) syncPrint(W + ": PP Done");
        write1(ch, (byte) P_HEARTBEAT);
        rb.clear();

        return this;
    }
}
