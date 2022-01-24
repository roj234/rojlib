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
import roj.text.UTFCoder;
import roj.util.ByteList;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.concurrent.ExecutionException;

import static roj.net.cross.Util.*;

/**
 * @author Roj233
 * @since 2021/12/21 13:28
 */
class ClientWork extends Stated {
    static final ClientWork CLIENT_WORK = new ClientWork();

    @Override
    final void enter(Client W) {
        W.timer = 0;
        W.lastPacket = System.currentTimeMillis();
        W.st1 = 1;
        W.waiting = -1;
    }

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
                case PS_REQUEST_CHANNEL:
                    if (rb.position() < 34) {
                        except = 34;
                        continue;
                    }

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
                    W.closePipe(rb.getInt(1));
                    break;
                case P_MSG:
                    if (rb.position() < 6) {
                        except = 6;
                        continue;
                    }
                    if (rb.position() < (except = (rb.get(5) & 0xFF) + 6)) {
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
                        case SPEC_OP_BAN:
                            W.room.chatBanned.add(rb.getInt(2));
                            break;
                        case SPEC_OP_UNBAN:
                            W.room.chatBanned.remove(rb.getInt(2));
                            break;
                        case SPEC_OP_BAN_ALL:
                            W.room.muted = true;
                            break;
                        case SPEC_OP_UNBAN_ALL:
                            W.room.muted = false;
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
                    if (rb.position() < (except = (rb.get(11) & 0xFF) + 12)) {
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
                    rb.putInt(W.clientId).flip();
                    W.room.master.sync(rb);
                    break;
                case P_CHANNEL_RESET:
                    if (rb.position() < 5) {
                        except = 5;
                        continue;
                    }

                    int pipeId = rb.getInt(1);
                    if (null == W.getPipe(pipeId)) {
                        if (DEBUG) {
                            syncPrint("不存在的管道 " + pipeId);
                        }
                        write1(ch, (byte) P_FAIL);
                        break;
                    }

                    rb.flip();
                    W.timer = System.currentTimeMillis() + TIMEOUT;
                    W.waiting = pipeId;
                    W.room.addPending(pipeId);
                    W.room.master.sync(rb);
                    rb.clear();
                    // do not process more IO
                    W.key.interestOps(0);
                    return this;
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

    static int readOrAwait(WrappedSocket ch, int exc) throws IOException {
        ByteBuffer rb = ch.buffer();
        if (rb.position() < exc) {
            int read = ch.read(exc - rb.position());
            if (read < 0) return -1;
        }
        return rb.position() >= exc ? 1 : 0;
    }

    static void checkUPnPPing(Client W) throws IOException {
        UPnPPingTask task = W.task;
        if (task.isDone()) {
            WrappedSocket ch = W.ch;
            ByteBuffer rb = ch.buffer();
            try {
                rb.put(1, task.get()).limit(2);
            } catch (InterruptedException | ExecutionException e) {
                syncPrint("理论上，这是不可能发生的...");
            }
            W.sync(rb);
            writeAndFlush(ch, rb, 500);
            rb.clear();

            W.task = null;
        }
    }

    static UTFCoder uc;
    static void chat(Client W, WrappedSocket ch, ByteBuffer rb) throws IOException {
        int target = rb.getInt(1);
        if (target == W.clientId) return;

        Room room = W.room;
        if (target == -1) {
            boolean isLong = rb.get(0) == P_MSG_LONG;

            int pos = rb.position();
            rb.position(isLong ? 6 : 5);
            room.sendMessage(W, isLong ? rb.getChar(6) : (rb.get(5) & 0xFF), rb);
            rb.position(pos);
            return;
        }

        Client to = room.clients.get(target);
        if (null == to || !room.chatEnabled.contains(target) || room.chatBanned.contains(W.clientId)) {
            write1(ch, (byte) P_FAIL);
            return;
        }

        if (CHATSPY) {
            if (uc == null) {
                uc = new UTFCoder();
            }
            synchronized (uc) {
                boolean isLong = rb.get(0) == P_MSG_LONG;
                int len = isLong ? rb.getChar(6) : (rb.get(5) & 0xFF);
                ByteList buf = uc.byteBuf;
                buf.ensureCapacity(len);

                int pos = rb.position();
                rb.position(isLong ? 6 : 5);
                rb.get(buf.list).position(pos);

                buf.wIndex(len);
                syncPrint(W + ": msg => " + to + ": " + uc.decode());
            }
        }

        rb.putInt(1, W.clientId).flip();
        to.sync(rb);
    }

    @Override
    public void tick(Client W) throws IOException {
        if (!isInRoom(W)) {
            W.toState(Logout.LOGOUT);
            return;
        }

        if (W.task != null) {
            checkUPnPPing(W);
        }

        if (W.waiting >= 0) {
            if (System.currentTimeMillis() > W.timer) {
                // 超时了，怎么办
                syncPrint("ERROR reset超时！");
                write1(W.ch, (byte) PS_ERROR_IO);
                W.room.removePending(W.waiting);
                W.toState(Logout.LOGOUT);
            } else if (!W.room.isPending(W.waiting)) {
                W.key.interestOps(SelectionKey.OP_READ);
                W.waiting = -1;
                write1(W.ch, (byte) P_HEARTBEAT);
            }
        }
    }
}
