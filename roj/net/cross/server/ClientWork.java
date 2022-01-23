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

import roj.collect.IntMap;
import roj.net.WrappedSocket;
import roj.net.cross.Util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.LockSupport;

import static roj.net.cross.Util.*;

/**
 * @author Roj233
 * @since 2021/12/21 13:28
 */
final class ClientWork extends Stated {
    static final ClientWork CLIENT_WORK = new ClientWork();

    @Override
    Stated next(Client W) throws IOException {
        WrappedSocket ch = W.ch;

        ByteBuffer rb = ch.buffer();
        rb.clear();

        UPnPinger pinger = null;
        int heart = T_HEART_TIMEOUT;
        int except = 1;
        while (!AEServer.server.shutdown && isInRoom(W)) {
            int read;
            if ((read = ch.read(except - rb.position())) == 0 && rb.position() < except) {
                W.pollPackets();
                LockSupport.parkNanos(10000);
                if (heart-- < 0) {
                    syncPrint(W + ": 心跳超时");
                    write1(ch, (byte) PS_ERROR_TIMEOUT);
                    break;
                }
                continue;
            }

            if (read < 0) break;
            switch (rb.get(0) & 0xFF) {
                case P_HEARTBEAT:
                    W.lastHeart = System.currentTimeMillis();
                    if (DEBUG) syncPrint(W + ": 客户端心跳");
                    break;
                case P_LOGOUT:
                    rb.clear();
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
                case P_CHANNEL_CLOSE:
                    if (rb.position() < 5) {
                        except = 5;
                        continue;
                    }
                    except = 1;
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
                    except = 1;
                    ChatUtil.chat(W, ch, rb);
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
                    except = 1;
                    ChatUtil.chat(W, ch, rb);
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
                    except = 1;
                    rb.position(1).flip();
                    long sec = rb.getLong();
                    char port = rb.getChar();
                    byte[] ip = new byte[rb.get() & 0xFF];
                    rb.get(ip);

                    if (pinger == null) pinger = new UPnPinger();
                    int result = pinger.ping(W, port, ip, sec);
                    rb.put(1, (byte) result).limit(2);
                    W.sync(rb);
                    writeAndFlush(ch, rb, 500);

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
                    except = 1;
                    rb.putInt(W.clientId).flip();
                    W.room.master.sync(rb);
                    break;
                case P_CHANNEL_RESET:
                    if (rb.position() < 5) {
                        except = 5;
                        continue;
                    }
                    except = 1;
                    if (null == W.getPipe(rb.getInt(1))) {
                        if (DEBUG) {
                            syncPrint("不存在的管道 " + rb.getInt(1));
                        }
                        write1(ch, (byte) P_FAIL);
                        break;
                    }
                    rb.flip();
                    W.room.master.sync(rb);
                    if (Util.DEBUG) syncPrint(W + ": Await reset #" + rb.getInt(1));
                    if (W.room.resetLock.await(rb.getInt(1), T_HEART_TIMEOUT)) {
                        if (Util.DEBUG) syncPrint(W + ": Await reset done");
                    } else {
                        // timed out
                        syncPrint("警告, Reset回调超时, 正在清理门户...");
                        IntMap<Client> clients = W.room.clients;
                        synchronized (clients) {
                            clients.clear();
                        }
                        return Logout.LOGOUT;
                    }
                    break;
                default:
                    unknownPacket(W, rb);
                    return Logout.LOGOUT;
            }
            if (DEBUG) syncPrint(W + ": PktProcDone");
            write1(ch, (byte) P_HEARTBEAT);
            heart = T_HEART_TIMEOUT;
            rb.clear();
        }

        return Logout.LOGOUT;
    }
}
