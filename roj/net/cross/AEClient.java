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

import roj.config.word.AbstLexer;
import roj.io.NIOUtil;
import roj.net.MSSSocket;
import roj.net.WrappedSocket;
import roj.net.misc.*;
import roj.util.Helpers;

import java.io.EOFException;
import java.io.FileDescriptor;
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
 * @since 2021/8/18 0:09
 */
public class AEClient extends IAEClient implements Shutdownable, GuiChat.ChatDispatcher {
    public static final int MAX_CHANNEL_COUNT = 6;

    public char[] portMap;
    public int clientId;

    protected FakeServer[] servers;
    final FDCLoop<FakeServer> serverLoop;

    final    TransferQueue<Object> lock;
    volatile Object                msgbox;

    public AEClient(SocketAddress server, String id, String token) {
        super(server, id, token);
        this.lock = new LinkedTransferQueue<>();
        this.serverLoop = new FDCLoop<>(this, "AE 本地监听", 1, 30000, 1);
    }

    public final void awaitLogin() throws InterruptedException {
        if (free != null) return;
        lock.transfer(-1);
    }

    protected void notifyLogon() {}

    public final void notifyPortMapModified() throws IOException, InterruptedException {
        if (free == null)
            throw new IOException("Client closed");

        if (!lock.tryTransfer(-2, 500, TimeUnit.MILLISECONDS)) {
            throw new IOException("异步处理超时");
        }
    }

    public String sendMessage(int clientId, String message) throws InterruptedException {
        if (clientId == this.clientId) return "你不能对自己说";
        if (message.length() > 255) return "消息过长";
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 255) return "消息过长";
        if (!lock.tryTransfer(new Object[] {clientId, bytes}, 500, TimeUnit.MILLISECONDS)) {
            return "异步处理超时";
        }
        return null;
    }

    final Pipe requestSocketPair(int portMapId) throws IOException {
        if (free == null)
            throw new IOException("Client closed");

        try {
            if (!lock.tryTransfer(portMapId, 2000, TimeUnit.MILLISECONDS)) {
                throw new IOException("异步处理超时");
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
                throw new IOException("异步处理超时");
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

        WrappedSocket ch = new MSSSocket(c, NIOUtil.fd(c));

        block:
        try {
            LoginResult p = clientLogin(ch);
            if (p == null) return;
            this.portMap = p.ports;
            this.clientId = p.clientId;

            this.free = Helpers.cast(new List<?>[p.ports.length]);
            Arrays.fill(free, Collections.emptyList());
            this.servers = new FakeServer[p.ports.length];

            notifyLogon();

            ByteBuffer rb = ch.buffer();
            rb.clear();

            Object state = null;
            int heart = 1;
            int except = 1;
            conn:
            while (!shutdown) {
                int read;
                if ((read = ch.read(except - rb.position())) == 0 && rb.position() < except) {
                    LockSupport.parkNanos(10000);

                    Object _int;
                    if (rb.position() == 0 && msgbox == null && (_int = lock.peek()) != null) {
                        checkInterrupt:
                        if (_int instanceof Pipe) { // release
                            Pipe pipe = (Pipe) _int;
                            // 不用检测失效，会在heartbeat中检测
                            if (!pipe.isUpstreamEof()) {
                                SpAttach att = (SpAttach) pipe.att;
                                List<Pipe> pairs = free[att.portId];
                                if (pairs == Collections.EMPTY_LIST)
                                    pairs = free[att.portId] = new ArrayList<>(3);
                                pairs.add((Pipe) _int);
                            }
                            lock.poll();
                        } else if (_int instanceof Object[]) { // send message
                            Object[] msg = (Object[]) _int;
                            byte[] mb = (byte[]) msg[1];
                            if (mb.length > 255) {
                                msgbox = "消息过长";
                                lock.poll();
                                break checkInterrupt;
                            }
                            rb.put((byte) P_MSG).putInt((int) msg[0]).put((byte) mb.length).put(mb);
                            msgbox = CheckServerAlive;
                            state = CheckServerAlive;
                        } else {
                            if (socketsById.size() > MAX_CHANNEL_COUNT) {
                                msgbox = "频道过多(" + MAX_CHANNEL_COUNT + ")";
                                lock.poll();
                                break checkInterrupt;
                            }

                            int i = (int) _int;
                            switch (i) {
                                case -1: // wait login
                                    lock.poll();
                                    break;
                                case -2: // notify port map changed
                                    for (int j = 0; j < servers.length; j++) {
                                        FakeServer cn = servers[j];
                                        char port = portMap[j];
                                        if (cn != null) {
                                            if (port != cn.port) {
                                                try {
                                                    cn.local.close();
                                                } catch (IOException ignored) {}
                                                serverLoop.unregister(servers[j]);
                                                servers[j] = null;
                                            }
                                        }
                                        if (servers[j] == null && port > 0) {
                                            servers[j] = new FakeServer(port, j);
                                            serverLoop.register(servers[j], null);
                                        }
                                    }
                                    lock.poll();
                                    break;
                                default: // get #i pipe
                                    List<Pipe> pairs = free[i];
                                    Pipe target;
                                    do {
                                        if (pairs.isEmpty()) {
                                            // 新开一个
                                            byte[] cipher = new byte[32];
                                            state = cipher;
                                            rnd.nextBytes(cipher);
                                            rb.put((byte) PS_REQUEST_CHANNEL).put((byte) i).put(cipher);
                                            msgbox = CheckServerAlive;
                                            break checkInterrupt;
                                        }
                                        target = pairs.remove(pairs.size() - 1);
                                        // 不用检测失效，会在heartbeat中检测
                                    } while (target.isUpstreamEof());
                                    SpAttach att = (SpAttach) target.att;

                                    if (DEBUG) syncPrint("复用 #" + att);
                                    // 通知对面重新连接下
                                    rb.put((byte) P_CHANNEL_RESET).putInt(att.channelId);
                                    msgbox = target;
                                    // 等回复
                                    state = CheckServerAlive;
                                    break;
                            }
                        }

                        rb.flip();
                        if (rb.limit() > 0 && writeAndFlush(ch, rb, TIMEOUT) < 0) {
                            msgbox = "操作超时";
                            lock.poll();
                            break;
                        }
                        rb.clear();
                    }

                    if (heartbeat(ch, --heart)) continue;
                    break;
                }

                if (read < 0) throw new EOFException("未预料的连接关闭: " + read);

                if (state == CheckServerAlive) {
                    if (msgbox == CheckServerAlive)
                        msgbox = null;
                    lock.poll();
                    state = null;
                }

                switch (rb.get(0) & 0xFF) {
                    case P_FAIL:
                        syncPrint("上次的操作失败了");
                        break;
                    case P_HEARTBEAT:
                        break;
                    case P_LOGOUT:
                        syncPrint("连接断开");
                        break block;
                    case P_CHANNEL_CLOSE:
                        if(rb.position() < 9) {
                            except = 9;
                            continue;
                        }
                        Pipe pair = socketsById.remove(rb.getInt(5));
                        pair.close();
                        List<Pipe> pairs = free[((SpAttach) pair.att).portId];
                        if (!pairs.isEmpty()) pairs.remove(pair);
                        syncPrint((rb.getInt(1) < 0 ? "服务端" : "房主") + "关闭了频道 #" +
                                          rb.getInt(5));
                        break;
                    case P_CHANNEL_OPEN_FAIL:
                        // Condition 'state instanceof byte[]' is redundant and can be replaced with a null check
                        if (state == null) throw new IOException("未预料的数据包");
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
                        if (state == null) throw new IOException("未预料的数据包");
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
                        SpAttach att = new SpAttach();
                        pair.att = att;
                        socketsById.put(att.channelId = (int) (pipeId >>> 32), pair);
                        msgbox = pair;
                        att.clientId = clientId;
                        att.portId = ((Number)lock.remove()).byteValue();
                        syncPrint("申请了频道 #" + (pipeId >>> 32));
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
                        rb.flip().position(5);
                        byte[] tmp = new byte[rb.get() & 0xFF];
                        rb.get(tmp);

                        int target = rb.getInt(1);
                        syncPrint("客户端 #" + rb.getInt(1) + " 对你说 \"" + AbstLexer.addSlashes(
                                new String(tmp, StandardCharsets.UTF_8)) + "\"");
                        break;
                    default:
                        int bc = (rb.get(0) & 0xFF) - 0x20;
                        if(rb.position() == 1 && bc >= 0 && bc < ERROR_NAMES.length) {
                            syncPrint("错误 " + ERROR_NAMES[bc]);
                        } else {
                            syncPrint("未知数据包: " + NIOUtil.dumpDirty(rb));
                        }
                        rb.clear();
                        break block;
                }
                rb.clear();
                heart = T_HEART;
                except = 1;
            }

            try {
                write1(ch, (byte) P_LOGOUT);
            } catch (IOException ignored) {}
            int t = 1000;
            while (ch.read() == 0 && t-- > 0) {
                LockSupport.parkNanos(10);
            }
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
                    pair.close();
                } catch (IOException ignored) {}
            }
            socketsById.clear();
            if (servers != null)
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
        if(writeAndFlush(ch, rb, TIMEOUT) < 0) {
            throw new SocketTimeoutException("登录发送超时");
        }
        rb.clear();

        long heart = TIMEOUT;
        int except = 1;

        while (!shutdown) {
            int read;
            if ((read = ch.read( except - rb.position())) == 0 && rb.position() < except) {
                LockSupport.parkNanos(1000000);
                if (heart-- < 0) {
                    throw new SocketTimeoutException("等待登录回复超时");
                }
                continue;
            }

            if (read < 0) throw new EOFException("未预料的连接关闭: " + read);

            switch (rb.get(0) & 0xFF) {
                case PC_LOGON_C:
                    if (rb.position() < 8) {
                        except = 8;
                        continue;
                    }
                    int infoLen = rb.get(1) & 0xFF;
                    int motdLen = rb.get(2) & 0xFF;
                    int portLen = rb.get(3) & 0xFF;
                    if (rb.position() < (except = infoLen + motdLen + (portLen << 1) + 8)) {
                        continue;
                    }
                    rb.position(4);
                    int clientId = rb.getInt();

                    byte[] infoBytes = new byte[infoLen];
                    rb.get(infoBytes);
                    String info = new String(infoBytes, StandardCharsets.UTF_8);
                    syncPrint("服务器MOTD: " + info);

                    byte[] motdBytes = new byte[motdLen];
                    rb.get(motdBytes);
                    String motd = new String(motdBytes, StandardCharsets.UTF_8);
                    syncPrint("房间MOTD: " + motd);

                    syncPrint("客户端ID: " + clientId);

                    if (portLen > 32)
                        throw new IllegalArgumentException("系统限制: 端口映射数量 < 32");
                    char[] ports = new char[portLen];
                    for (int i = 0; i < portLen; i++) {
                        ports[i] = rb.getChar();
                    }
                    ch.poll();

                    LoginResult logon = new LoginResult();
                    logon.clientId = clientId;
                    logon.ports = ports;
                    return logon;
                case P_LOGOUT:
                    syncPrint("服务端断开连接");
                    return null;
                default:
                    int bc;
                    if(rb.position() == 1 && (bc = (0xFF & rb.get(0)) - 0x20) >= 0 && bc < ERROR_NAMES.length) {
                        syncPrint("错误 " + ERROR_NAMES[bc]);
                    } else {
                        syncPrint("无效数据包");
                    }
                    return null;
            }
        }
        return null;
    }

    static final class LoginResult {
        char[] ports;
        int    clientId;
    }

    final class FakeServer extends FDChannel implements Consumer<Pipe> {
        final ServerSocket local;
        final int portId;
        final char port;

        public FakeServer(char port, int portId) throws IOException {
            ServerSocket s = this.local = new ServerSocket();
            s.setReuseAddress(true);
            s.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 100);
            this.portId = portId;
            this.port = port;
        }

        @Override
        public FileDescriptor fd() throws IOException {
            return NIOUtil.fd(local);
        }

        @Override
        public void accept(Pipe pair) {
            if (pair.isUpstreamEof()) {
                System.out.println("管道结束 " + pair.att);
                return;
            }
            try {
                releaseSocketPair(pair);
            } catch (Throwable e) {
                System.out.println("管道回收失败 " + pair.att);
                e.printStackTrace();
            }
        }

        @Override
        public void tick(int elapsed) throws IOException {
            if (shutdown) close();
        }

        @Override
        public void close() throws IOException {
            local.close();
        }

        @Override
        public void selected(int readyOps) throws Exception {
            if (local.isClosed()) return;
            Socket c = local.accept();
            initSocketPref(c);
            Pipe pair = requestSocketPair(portId);
            pair.setClient(NIOUtil.fd(c));
            PipeIOThread.syncRegister(AEClient.this, pair, this);
        }
    }
}
