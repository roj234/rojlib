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

import roj.concurrent.task.ITaskNaCl;
import roj.io.NIOUtil;
import roj.net.tcp.MSSSocket;
import roj.net.tcp.PlainSocket;
import roj.net.tcp.WrappedSocket;
import roj.util.Helpers;

import java.io.EOFException;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TransferQueue;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

import static roj.net.cross.Util.*;

/**
 * AbyssalEye Client
 *
 * @author Roj233
 * @version 0.3.1
 * @since 2021/8/18 0:09
 */
public class AEClient extends IAEClient implements Shutdownable {
    static final int MAX_CHANNEL_COUNT = 32;

    public List<PortMapEntry> portMap;
    protected FakeServer[] servers;

    final    TransferQueue<Object> lock;
    volatile Object                msgbox;

    public AEClient(SocketAddress server, boolean ssl, String id, String token) {
        super(server, ssl, id, token);
        this.lock = new LinkedTransferQueue<>();
    }

    public final void awaitLogin() throws InterruptedException {
        if (free != null) return;
        lock.transfer(-1);
    }

    protected void notifyLogon() {}

    public final void notifyPortMapModified() throws IOException, InterruptedException {
        if (free == null)
            throw new IOException("Client closed");

        if (!lock.tryTransfer(-2, 100, TimeUnit.MILLISECONDS)) {
            throw new IOException("Int-wait timeout");
        }
    }

    final Pipe requestSocketPair(int portMapId) throws IOException {
        if (free == null)
            throw new IOException("Client closed");

        try {
            if (!lock.tryTransfer(portMapId, 3000, TimeUnit.MILLISECONDS)) {
                throw new IOException("Int-wait timeout");
            }
        } catch (InterruptedException ignored) {} // should not happen

        Object by = msgbox;
        msgbox = null;
        if (by instanceof Pipe)
            return (Pipe) by;
        throw new IOException(String.valueOf(by));
    }

    final void releaseSocketPair(Pipe thePair) throws IOException {
        if (free == null)
            throw new IOException("Client closed");

        thePair.setClient(null);
        try {
            if (!lock.tryTransfer(thePair, 1000, TimeUnit.MILLISECONDS)) {
                throw new IOException("Int-wait timeout");
            }
        } catch (InterruptedException ignored) {} // should not happen

        String by = (String) msgbox;
        msgbox = null;
        if (by != null) throw new IOException(by);
    }

    protected void call() throws IOException {
        Socket c = new Socket();
        try {
            c.connect(server);
        } catch (ConnectException e) {
            throw new ConnectException("无法连接至服务器");
        }

        WrappedSocket ch = ssl ?
                new MSSSocket(c, NIOUtil.fd(c)) :
                new PlainSocket(c, NIOUtil.fd(c));

        try {
            LoginResult p = clientLogin(ch);
            if (p == null) return;
            this.portMap = p.ports;

            this.free = Helpers.cast(new ArrayList<?>[p.ports.size()]);
            Arrays.fill(free, Collections.emptyList());
            this.servers = new FakeServer[p.ports.size()];

            notifyLogon();

            ByteBuffer rb = ch.buffer();
            rb.clear();

            Object state = null;
            int heart = 0;
            int except = 1;
            conn:
            while (!shutdown) {
                int read;
                if ((read = ch.read(except - rb.position())) == 0 || rb.position() < except) {
                    LockSupport.parkNanos(50);

                    checkInterrupt:
                    if (rb.position() == 0 && msgbox == null) {
                        Object _int = lock.peek();
                        if (_int == null) break checkInterrupt;
                        if (_int instanceof Pipe) {
                            SpAttach att = (SpAttach) ((Pipe) _int).att;
                            rb.put((byte) P_CHANNEL_OP)
                              .putInt(att.channelId)
                              .put((byte) OP_SET_INACTIVE).flip();

                            List<Pipe> pairs = free[att.portId];
                            if (pairs == Collections.EMPTY_LIST)
                                pairs = free[att.portId] = new ArrayList<>(3);
                            pairs.add((Pipe) _int);

                            msgbox = 无事发生;
                            state = CheckServerAlive;
                        } else {
                            if (socketsById.size() > MAX_CHANNEL_COUNT) {
                                msgbox = "频道过多";
                                lock.poll();
                                break checkInterrupt;
                            }

                            int i = (int) _int;
                            if (i == -1) {
                                lock.poll();
                            } else if (i == -2) {
                                for (FakeServer cn : servers) {
                                    if (cn != null) {
                                        try {
                                            cn.local.close();
                                        } catch (IOException ignored) {}
                                    }
                                }
                                Arrays.fill(servers, null);
                                for (i = 0; i < portMap.size(); i++) {
                                    PortMapEntry entry = portMap.get(i);
                                    if (entry.enabled) {
                                        servers[i] = new FakeServer(entry.port, i, MAX_CHANNEL_COUNT);
                                    }
                                }
                                lock.poll();
                            } else {
                                List<Pipe> pairs = free[i];
                                if (pairs.isEmpty()) {
                                    byte[] cipher = new byte[32];
                                    rnd.nextBytes(cipher);
                                    rb.put((byte) PS_REQUEST_CHANNEL)
                                      .put((byte) i)
                                      .put(cipher).flip();
                                    state = cipher;

                                    msgbox = 无事发生;
                                } else {
                                    Pipe target = pairs.get(pairs.size() - 1);
                                    if (!target.isEof() || target.isReleased()) {
                                        syncPrint("不应在此时出现 NOT_EOF/RELEASED #" + target.att);
                                    }

                                    rb.put((byte) P_CHANNEL_OP)
                                      .putInt(((SpAttach) target.att).channelId)
                                      .put((byte) OP_SET_ACTIVE).flip();

                                    msgbox = target;
                                    lock.poll();
                                    state = CheckServerAlive;
                                }
                            }
                        }

                        if (writeAndFlush(ch, rb, TIMEOUT_TRANSFER) < 0) {
                            msgbox = "操作超时";
                            lock.poll();
                            break;
                        }
                    }

                    if (heartbeat(ch, --heart, false)) continue;
                    break;
                }
                if (read < 0) throw new IOException( "连接非正常断开: " + read);

                if (state == CheckServerAlive) {
                    if (msgbox == 无事发生)
                        msgbox = null;
                    lock.poll();
                    state = null;
                }

                switch (rb.get(0) & 0xFF) {
                    case P_HEARTBEAT:
                        break;
                    case P_LOGOUT:
                        syncPrint(this + ": 连接断开");
                        break conn;
                    case PS_CHANNEL_CLOSE:
                        if(rb.position() < 9) {
                            except = 9;
                            continue;
                        }
                        Pipe pair = socketsById.remove(rb.getInt(5));
                        pair.release();
                        List<Pipe> pairs = free[((SpAttach) pair.att).portId];
                        if (!pairs.isEmpty()) pairs.remove(pair);
                        syncPrint((rb.getInt(1) < 0 ? "服务端" : "房主") + "关闭了频道 #" +
                                          Integer.toHexString(rb.getInt(5)));
                        break;
                    case P_CHANNEL_OPEN_FAIL:
                        if (!(state instanceof byte[])) throw new IOException("未预料的数据包");
                        if(rb.position() < 6) {
                            except = 6;
                            continue;
                        }
                        if(rb.position() < (rb.get(5) & 0xFF) + 6) {
                            except = (rb.get(5) & 0xFF) + 6;
                            continue;
                        }
                        byte[] reasonBytes = new byte[rb.get(5) & 0xFF];
                        rb.flip().position(6);
                        rb.get(reasonBytes);
                        String reason = new String(reasonBytes, StandardCharsets.UTF_8);
                        msgbox = (rb.getInt(1) < 0 ? "服务端" : "房主") + "拒绝开启频道: " + reason;
                        lock.poll();
                        break;
                    case P_CHANNEL_RESULT:
                        if (!(state instanceof byte[])) throw new IOException("未预料的数据包");
                        if(rb.position() < 41) {
                            except = 41;
                            continue;
                        }
                        rb.flip().position(1);
                        byte[] ciphers = new byte[64];
                        System.arraycopy(state, 0, ciphers, 0, 32);
                        rb.get(ciphers, 32, 32);
                        long pipeId = rb.getLong();
                        pair = pipeLogin(pipeId, ciphers);
                        socketsById.put((int) (pipeId >> 32), pair);
                        msgbox = pair;
                        lock.poll();
                        break;
                    default:
                        int bc = (rb.get(0) & 0xFF) - 0x20;
                        if(rb.position() == 1 && bc >= 0 && bc < ERROR_NAMES.length) {
                            syncPrint(this + ": 错误 " + ERROR_NAMES[bc]);
                        } else {
                            syncPrint(this + ": 未知数据包: " + dumpBuffer(rb));
                        }
                        rb.clear();
                        break conn;
                }
                rb.clear();
                heart = T_CLIENT_HEARTBEAT_TIME;
                except = 1;
            }

            try {
                write1(ch, (byte) P_LOGOUT);
            } catch (IOException ignored) {}
        } catch (Throwable e) {
            onError(ch, e);
        } finally {
            try {
                int i = 10;
                while (!ch.shutdown() && i-- > 0) {
                    LockSupport.parkNanos(50);
                }
            } catch (IOException ignored) {}
            for (Pipe pair : socketsById.values()) {
                try {
                    pair.release();
                } catch (IOException ignored) {}
            }
            socketsById.clear();
            for (FakeServer cn : servers) {
                if (cn != null) {
                    try {
                        cn.local.close();
                    } catch (IOException ignored) {}
                }
            }
            ch.close();
        }
    }

    private LoginResult clientLogin(WrappedSocket ch) throws IOException {
        handshakeClient(ch, null);
        ByteBuffer rb = ch.buffer();

        rb.clear();
        byte[] idBytes = id.getBytes(StandardCharsets.UTF_8);
        byte[] tokenBytes = token.getBytes(StandardCharsets.UTF_8);
        rb.put((byte) PS_LOGIN_C)
          .put((byte) idBytes.length)
          .put((byte) tokenBytes.length)
          .put(idBytes)
          .put(tokenBytes)
          .flip();
        if(writeAndFlush(ch, rb, TIMEOUT_TRANSFER) < 0) {
            throw new SocketTimeoutException("登录发送超时");
        }

        int heart = TIMEOUT_HEART_SERVER;
        int except = 1;

        while (!shutdown) {
            int read;
            if ((read = ch.read( except - rb.position())) == 0 || rb.position() < except) {
                LockSupport.parkNanos(50);
                if (heart-- < 0) {
                    throw new SocketTimeoutException("等待登录回复超时");
                }
                continue;
            }

            if (read < 0) throw new EOFException("未预料的连接关闭");
            switch (rb.get(0) & 0xFF) {
                case PC_LOGON_C:
                    if (rb.position() < 8) {
                        except = 8;
                        continue;
                    }
                    int infoLen = rb.get(1) & 0xFF;
                    int motdLen = rb.get(2) & 0xFF;
                    int portLen = rb.get(3) & 0xFF;
                    if (rb.position() < infoLen + motdLen + (portLen << 1) + 8) {
                        except = infoLen + motdLen + portLen + 8;
                        continue;
                    }
                    rb.position(4);
                    int clientId = rb.getInt();

                    byte[] infoBytes = new byte[infoLen];
                    rb.get(infoBytes);
                    String info = new String(infoBytes, StandardCharsets.UTF_8);
                    syncPrint(this + " 服务器欢迎消息: " + info);

                    byte[] motdBytes = new byte[motdLen];
                    rb.get(motdBytes);
                    String motd = new String(motdBytes, StandardCharsets.UTF_8);
                    syncPrint(this + " 房间欢迎消息: " + motd);

                    syncPrint(this + " 客户端ID: " + clientId);

                    List<PortMapEntry> ports = new ArrayList<>();
                    while (rb.hasRemaining()) {
                        PortMapEntry port = new PortMapEntry();
                        port.port = rb.getChar();
                        ports.add(port);
                    }
                    if (ports.size() > 32)
                        throw new IllegalArgumentException("系统限制: 端口映射数量 < 32");

                    LoginResult logon = new LoginResult();
                    logon.clientId = clientId;
                    logon.ports = ports;
                    return logon;
                case P_LOGOUT:
                    syncPrint(this + ": 服务端断开连接");
                    return null;
                default:
                    int bc;
                    if(rb.position() == 1 && (bc = (0xFF & rb.get(0)) - 0x20) >= 0 && bc < ERROR_NAMES.length) {
                        syncPrint(this + ": 错误 " + ERROR_NAMES[bc]);
                    } else {
                        syncPrint(this + ": 无效数据包");
                    }
                    return null;
            }
        }
        return null;
    }

    static final class LoginResult {
        List<PortMapEntry> ports;
        int                clientId;
    }

    final class FakeServer implements Runnable, ITaskNaCl, Consumer<Pipe> {
        private final ServerSocket local;
        private final int portId;

        public FakeServer(char port, int portId, int backlog) throws IOException {
            ServerSocket s = this.local = new ServerSocket();
            s.setReuseAddress(true);
            s.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), backlog);
            this.portId = portId;
        }

        public void run() {
            while (!shutdown) {
                Socket c;
                try {
                    c = local.accept();
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }

                try {
                    initSocketPref(c);
                    Pipe pair = requestSocketPair(portId);
                    pair.setClient(NIOUtil.fd(c));
                    PipeIOThread.syncRegister(AEClient.this, pair, this);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            try {
                local.close();
            } catch (IOException ignored) {}
        }

        @Override
        public void calculate(Thread thread) {
            run();
        }

        @Override
        public boolean isDone() {
            return local.isClosed();
        }

        @Override
        public void accept(Pipe pair) {
            if (pair.isUpstreamEof() || pair.isReleased()) return;
            try {
                releaseSocketPair(pair);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static final class PortMapEntry {
        public char   port;
        public boolean enabled;
    }
}
