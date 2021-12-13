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
import roj.config.data.CList;
import roj.config.data.CMapping;
import roj.io.NonblockingUtil;
import roj.net.cross.Pipe.CipherPipe;
import roj.net.tcp.MSSSocket;
import roj.net.tcp.PlainSocket;
import roj.net.tcp.WrappedSocket;
import roj.util.FastLocalThread;

import java.io.FileDescriptor;
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
    final boolean ssl;
    final String id, token;

    final Random       rnd;
    final IntMap<Pipe> socketsById;
    List<Pipe>[] free;

    boolean shutdown;

    protected IAEClient(SocketAddress server, boolean ssl, String id, String token) {
        this.server = server;
        this.ssl = ssl;
        this.id = id;
        this.token = token;
        this.rnd = new SecureRandom();
        this.socketsById = new IntMap<>();
        setDaemon(true);
        setName("AE - 控制性连接线程");
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
            call();
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
                syncPrint(this + ": 心跳发送失败");
            }
            if (ch.buffer().position() == 0 && !socketsById.isEmpty()) {
                for (Iterator<Pipe> itr = socketsById.values().iterator(); itr.hasNext(); ) {
                    Pipe pair = itr.next();
                    if (pair.isUpstreamEof()) {
                        itr.remove();
                        SpAttach att = (SpAttach) pair.att;
                        List<Pipe> pairs = free[att.portId];
                        if (!pairs.isEmpty()) pairs.remove(pair);
                    } else if (pair.isDownstreamEof()) {
                        ByteBuffer rb = ch.buffer();
                        SpAttach att = (SpAttach) pair.att;
                        List<Pipe> pairs = free[att.portId];
                        if (att.idleTimer++ == 0 && host) {
                            rb.put((byte) P_CHANNEL_OP)
                              .putInt(att.channelId)
                              .put((byte) OP_SET_INACTIVE).flip();
                            if (writeAndFlush(ch, rb, TIMEOUT_TRANSFER) < 0) {
                                syncPrint(this + ": 频道暂停超时");
                                return false;
                            }

                            if (pairs == Collections.EMPTY_LIST)
                                pairs = free[att.portId] = new ArrayList<>(3);
                            pairs.add(pair);
                        } else if (att.idleTimer > 10000) {
                            rb.put((byte) PS_CHANNEL_CLOSE)
                              .putLong(att.channelId).flip();
                            if (writeAndFlush(ch, rb, TIMEOUT_TRANSFER) < 0) {
                                syncPrint(this + ": 频道关闭超时");
                                return false;
                            }

                            pair.release();
                            if (!pairs.isEmpty()) pairs.remove(pair);
                            itr.remove();
                        }
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
            syncPrint(ch + ": 错误 " + ERROR_NAMES[bc]);
        } else {
            String msg = e.getMessage();
            if (!"Broken pipe".equals(msg) && !"Connection reset by peer".equals(msg)) {
                e.printStackTrace();
            }
        }
    }

    final Pipe pipeLogin(long pipeId, byte[] ciphers) throws IOException {
        Socket c = new Socket();
        try {
            c.connect(server);
        } catch (ConnectException e) {
            throw new ConnectException("无法连接至服务器");
        }

        WrappedSocket ch = ssl ?
                new MSSSocket(c, NonblockingUtil.fd(c)) :
                new PlainSocket(c, NonblockingUtil.fd(c));

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

    static final class Worker extends CipherPipe {
        // 统计信息, 可选
        int slaveId;
        final long connectTime;
        String remoteIp;

        public void serialize(CList lx) {
            CMapping self = new CMapping();
            self.put("id", att.toString());
            self.put("ip", remoteIp);
            self.put("time", connectTime);
            self.put("up", nBytesToH);
            self.put("down", nBytesToC);
            lx.add(self);
        }

        @Override
        public String toString() {
            return remoteIp + " #" + slaveId;
        }

        Worker(int slaveId, String remoteIp, FileDescriptor server, FileDescriptor local, byte[] password) {
            super(server, local, password);
            this.slaveId = slaveId;
            this.remoteIp = remoteIp;
            this.connectTime = System.currentTimeMillis();
        }
    }
}