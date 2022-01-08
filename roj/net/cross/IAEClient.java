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
package roj.net.cross;

import roj.collect.IntMap;
import roj.io.NIOUtil;
import roj.net.MSSSocket;
import roj.net.WrappedSocket;
import roj.net.misc.Pipe;
import roj.net.misc.Pipe.CipherPipe;
import roj.net.misc.Shutdownable;
import roj.util.FastLocalThread;

import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.*;

import static roj.net.cross.Util.*;

/**
 * @author Roj233
 * @version 0.1
 * @since 2021/12/26 2:55
 */
abstract class IAEClient extends FastLocalThread implements Shutdownable {
    static final int MAX_CHANNEL_COUNT = 32;

    final SocketAddress server;
    final String id, token;

    final Random       rnd;
    final IntMap<Pipe> socketsById;
    List<Pipe>[] free;

    boolean shutdown;

    protected IAEClient(SocketAddress server, String id, String token) {
        this.server = server;
        this.id = id;
        this.token = token;
        this.rnd = new SecureRandom();
        this.socketsById = new IntMap<>();
        setDaemon(true);
        setName("AE - 控制连接");
    }

    @Override
    public final void shutdown() {
        shutdown = true;
        try {
            join();
        } catch (InterruptedException ignored) {}
    }

    @Override
    public final boolean wasShutdown() {
        return shutdown;
    }

    @Override
    public final void run() {
        try {
            shutdown = false;
            call();
            shutdown = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public final String toString() {
        return id;
    }

    protected abstract void call() throws IOException;

    final boolean heartbeat(WrappedSocket ch, int heart, boolean host) throws IOException {
        if(heart % T_CLIENT_HEARTBEAT_RETRY == 0) {
            if (write1(ch, (byte) P_HEARTBEAT) < 0) {
                syncPrint(this + ": 传输失败");
            }
            ByteBuffer rb = ch.buffer();
            if (rb.position() == 0 && !socketsById.isEmpty()) {
                for (Iterator<Pipe> itr = socketsById.values().iterator(); itr.hasNext(); ) {
                    Pipe pair = itr.next();
                    SpAttach att = (SpAttach) pair.att;
                    List<Pipe> pairs = free[att.portId];
                    if (pair.isUpstreamEof() || pair.isReleased()) {
                        itr.remove();

                        if (!pairs.isEmpty()) pairs.remove(pair);

                        rb.put((byte) P_CHANNEL_CLOSE)
                          .putInt(att.channelId);
                    } else if (host && pair.isDownstreamEof()) {
                        // client手动回收
                        if (pairs == Collections.EMPTY_LIST)
                            pairs = free[att.portId] = new ArrayList<>(3);
                        pairs.add(pair);

                        rb.put((byte) P_CHANNEL_INACTIVE)
                          .putInt(att.channelId);
                        syncPrint("管道 #" + att.channelId + " 系统回收");
                    } else if (pair.idleTime++ > 100000) {
                        itr.remove();

                        rb.put((byte) P_CHANNEL_CLOSE)
                          .putLong(att.channelId);
                        syncPrint("管道 #" + att.channelId + " 超时关闭");

                        pair.release();
                        if (!pairs.isEmpty()) pairs.remove(pair);
                    }
                }

                if (rb.position() > 0) {
                    rb.flip();
                    if (writeAndFlush(ch, rb, TIMEOUT_TRANSFER) < 0) {
                        syncPrint(this + ": 传输失败");
                        return false;
                    }
                }
            }
        } else if(heart < -T_CLIENT_HEARTBEAT_TIMEOUT) {
            syncPrint(this + ": 没收到服务端心跳");
            return false;
        }
        return true;
    }

    static void onError(WrappedSocket ch, Throwable e) {
        ByteBuffer buf = ch.buffer();
        int bc;
        if(buf.position() == 1 && (bc = (0xFF & buf.get(0)) - 0x20) >= 0 && bc < ERROR_NAMES.length) {
            syncPrint("服务错误 " + ERROR_NAMES[bc]);
        } else {
            syncPrint("异常 " + e.getMessage());
            if (e.getClass() != IOException.class)
                e.printStackTrace();
        }
    }

    final Pipe pipeLogin(long pipeId, byte[] ciphers) throws IOException {
        Socket c = new Socket();
        try {
            c.connect(server);
        } catch (ConnectException e) {
            throw new ConnectException("无法连接至服务器");
        }

        WrappedSocket ch = new MSSSocket(c, NIOUtil.fd(c));

        try {
            handshakeClient(ch, pipeId);
            CipherPipe SCP = new CipherPipe(ch.fd(), null, ciphers);
            SCP.setInactive();
            return SCP;
        } catch (IOException e) {
            ch.close();
            throw e;
        }
    }

    static final Object CheckServerAlive = new Object(), 无事发生 = new Object();
}