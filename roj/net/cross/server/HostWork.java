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
import roj.net.cross.server.AEServer.Worker;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.locks.LockSupport;

import static roj.net.cross.Util.*;

/**
 * @author Roj233
 * @since 2021/12/21 13:28
 */
final class HostWork extends Stated {
    static final HostWork HOST_WORK = new HostWork();

    @Override
    Stated next(Worker W) throws IOException {
        WrappedSocket ch = W.ch;

        ByteBuffer rb = ch.buffer();
        rb.clear();

        UPnPinger pinger = null;
        int heart = T_HEART_TIMEOUT;
        int except = 1;
        while (!W.server.shutdown && isInRoom(W)) {
            int read;
            if ((read = ch.read(except - rb.position())) == 0 && rb.position() < except) {
                W.pollPackets();
                LockSupport.parkNanos(10000);
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
                    return Logout.LOGOUT;
                case PS_KICK_CLIENT:
                    if (rb.position() < 5) {
                        except = 5;
                        continue;
                    }
                    except = 1;

                    int clientId = rb.getInt(1);
                    Worker w = W.room.clients.remove(clientId);
                    if (w == null)
                        if (Util.DEBUG) syncPrint(W + ": 踢出客户端: 无效客户端 #" + clientId);
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
                        rb.put((byte) P_CHANNEL_RESULT)
                          .put(rnd2).putLong(w.getPendingPipe())
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
                    except = 1;

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

                    int target = rb.getInt(1);
                    if (target == W.clientId) break;
                    Worker to = W.room.clients.get(target);
                    if (null == to) {
                        write1(ch, (byte) P_FAIL);
                        break;
                    }
                    if (Util.DEBUG) {
                        byte[] b = new byte[rb.get(5) & 0xFF];
                        int pos = rb.position();
                        rb.position(5);
                        rb.get(b).position(pos);
                        syncPrint(W + ": msg to #" + target + ": " + new String(b, StandardCharsets.UTF_8));
                    }
                    rb.putInt(1, W.clientId).flip();
                    to.sync(rb);
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
                    byte[] buf = new byte[rb.position()];
                    rb.flip();
                    rb.get(buf);
                    W.room.upnpAddress = buf;
                    // 同步到客户端
                    synchronized (W.room.clients) {
                        for (Worker wk : W.room.clients.values()) {
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
                    except = 1;
                    if (Util.DEBUG) syncPrint(W + ": Singal #" + rb.getInt(1));
                    W.room.resetLock.signal(rb.getInt(1));
                    break;
                default:
                    unknownPacket(W, rb);
                    return Logout.LOGOUT;
            }
            write1(ch, (byte) P_HEARTBEAT);
            heart = T_HEART_TIMEOUT;
            rb.clear();
        }

        return Logout.LOGOUT;
    }
}
