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
import roj.concurrent.PacketBuffer;
import roj.config.word.AbstLexer;
import roj.io.NIOUtil;
import roj.net.MSSSocket;
import roj.net.NetworkUtil;
import roj.net.WrappedSocket;
import roj.net.misc.Pipe;
import roj.net.misc.PipeIOThread;
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
import java.util.concurrent.locks.LockSupport;

import static roj.net.cross.Util.*;

/**
 * AbyssalEye Host
 *
 * @author Roj233
 * @version 0.4.0
 * @since 2021/9/12 0:57
 */
public class AEHost extends IAEClient implements GuiChat.ChatDispatcher {
    static final int MAX_CHANNEL_COUNT = 100;

    static final class ResetTimer extends Thread {
        static final int TIMER = 3000;
        static final class Entry {
            Pipe pipe;
            long time;
        }

        final ArrayList<Entry> entries = new ArrayList<>();
        volatile boolean park;

        ResetTimer() {
            setName("Channel reset timer");
            setDaemon(true);
        }

        @Override
        public void run() {
            while (!Thread.interrupted()) {
                ArrayList<Entry> es = this.entries;
                for (int i = 0; i < es.size(); i++) {
                    long now = System.currentTimeMillis();
                    Entry e = es.get(i);
                    // ms => ns
                    LockSupport.parkNanos(this, (now - e.time) * 1000000L);
                    if (e.pipe.idleTime > TIMER) {
                        try {
                            e.pipe.close();
                        } catch (Throwable ignored) {}
                    }
                    synchronized (es) {
                        es.remove(i--);
                    }
                }
                park = true;
                LockSupport.park();
            }
        }

        public void add(Pipe pipe) {
            if (entries.size() > 100) {
                pipe.idleTime = CHANNEL_IDLE_TIMEOUT - TIMER;
                return;
            }
            Entry e = new Entry();
            e.pipe = pipe;
            e.time = System.currentTimeMillis() + TIMER;
            synchronized (entries) {
                entries.add(e);
            }
            if (park) {
                park = false;
                LockSupport.unpark(this);
            }
        }
    }

    IntMap<Client> clients;
    char[] portMap;
    public String motd;

    PacketBuffer pb;
    ResetTimer   tm;

    public AEHost(SocketAddress server, String id, String token) {
        super(server, id, token);
        this.clients = new IntMap<>();
        this.portMap = new char[1];
        this.pb = new PacketBuffer();
        this.tm = new ResetTimer();
        this.tm.start();
    }

    public void kickSome(int... clientIds) {
        ByteBuffer tmp = ByteBuffer.allocate(clientIds.length * 5);
        for (int i : clientIds) {
            tmp.put((byte) PS_KICK_CLIENT).putInt(i);
        }
        tmp.flip();
        pb.offerAndAwait(tmp);
    }

    public String sendMessage(int clientId, String message) {
        if (clientId == 0) return "你不能对自己说";
        byte[] mb = message.getBytes(StandardCharsets.UTF_8);
        if (mb.length > 255) return "消息过长";
        ByteBuffer tmp = ByteBuffer.allocate(6 + mb.length);
        tmp.put((byte) P_MSG).putInt(clientId).put((byte) mb.length).put(mb).flip();
        pb.offerAndAwait(tmp);
        return null;
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
            if (!hostLogin(ch, motd == null ? "" : motd)) return;

            // 留空
            Arrays.fill(super.free = Helpers.cast(new List<?>[portMap.length]), Collections.emptyList());

            ByteBuffer rb = ch.buffer();
            rb.clear();

            int heart = 1;
            int except = 1;
            conn:
            while (!shutdown) {
                int read;
                if ((read = ch.read(except - rb.position())) == 0 && rb.position() < except) {
                    LockSupport.parkNanos(10000);
                    if (rb.position() == 0 && pb.take(rb)) {
                        rb.flip();
                        if (writeAndFlush(ch, rb, TIMEOUT) < 0) {
                            syncPrint(this + ": PBW超时");
                            break;
                        }
                        rb.clear();
                    }
                    if (heartbeat(ch, --heart)) continue;
                    break;
                }

                if (read < 0) throw new EOFException("未预料的连接关闭: " + read);

                switch (rb.get(0) & 0xFF) {
                    case P_FAIL:
                        syncPrint("上次的操作失败了");
                        break;
                    case P_HEARTBEAT:
                        break;
                    case P_LOGOUT:
                        syncPrint("连接断开");
                        break block;
                    case P_CHANNEL_RESET:
                        if(rb.position() < 5) {
                            except = 5;
                            continue;
                        }
                        Pipe pair = socketsById.get(rb.getInt(1));
                        if (pair != null) {
                            reset(pair);
                            tm.add(pair);
                        } else if (DEBUG) syncPrint("Invalid reset #" + rb.getInt(1));

                        rb.flip();
                        if (writeAndFlush(ch, rb, TIMEOUT) < 0) {
                            syncPrint(" CC 超时");
                            break conn;
                        }
                        break;
                    case P_CHANNEL_CLOSE:
                        if(rb.position() < 9) {
                            except = 9;
                            continue;
                        }
                        pair = socketsById.remove(rb.getInt(5));
                        if (pair != null) {
                            pair.close();
                            syncPrint((rb.getInt(1) < 0 ? "服务端" : "客户端") +
                                     "关闭了频道 #" + Integer.toHexString(rb.getInt(5)));
                        }
                        break;
                    case P_CHANNEL_RESULT:
                        if(rb.position() < 46) {
                            except = 46;
                            continue;
                        }
                        rb.flip().position(1);

                        int portId = rb.get() & 0xFF;

                        byte[] ciphers = new byte[64];
                        rnd.nextBytes(ciphers);
                        rb.get(ciphers, 0, 32);

                        int clientId = rb.getInt();

                        if (socketsById.size() >= MAX_CHANNEL_COUNT || !clients.containsKey(clientId)) {
                            rb.clear();
                            rb.put((byte) P_CHANNEL_OPEN_FAIL).putInt(clientId);
                            byte[] reasonBytes = (clients.containsKey(clientId) ? "这边开启的频道过多(" + MAX_CHANNEL_COUNT + ")" : "未知的客户端")
                                    .getBytes(StandardCharsets.UTF_8);
                            rb.put((byte) reasonBytes.length).put(reasonBytes).flip();
                            syncPrint("客户端 #" + clientId + " 开启频道被阻止");
                        } else {
                            long pipeId = rb.getLong();

                            rb.clear();
                            rb.put((byte) PS_CHANNEL_OPEN).putInt(clientId)
                              .put(ciphers, 32, 32).flip();

                            SpAttach att = new SpAttach();
                            att.clientId = clientId;
                            att.portId = (byte) portId;
                            socketsById.put(att.channelId = (int) (pipeId >>> 32),
                                            pair = pipeLogin(pipeId, ciphers));
                            pair.att = att;
                            reset(pair);
                        }

                        if (writeAndFlush(ch, rb, TIMEOUT) < 0) {
                            syncPrint(" COF 超时");
                            break conn;
                        }
                        break;
                    case PH_CLIENT_LOGIN:
                        if(rb.position() < 8) {
                            except = 8;
                            continue;
                        }
                        if(rb.position() < (rb.get(7) & 0xFF) + 8) {
                            except = (rb.get(7) & 0xFF) + 8;
                            continue;
                        }
                        rb.flip().position(1);
                        clientId = rb.getInt();
                        char port = rb.getChar();
                        byte[] ip = new byte[rb.get() & 0xFF];
                        rb.get(ip);
                        String address = NetworkUtil.bytes2ip(ip) + ':' + (int) port;
                        clients.put(clientId, new Client(address));
                        syncPrint("客户端 #" + clientId + " 上线了, 它来自 " + address);
                        break;
                    case PH_CLIENT_LOGOUT:
                        if(rb.position() < 5) {
                            except = 5;
                            continue;
                        }
                        clientId = rb.getInt(1);
                        if (clients.remove(clientId) != null) {
                            syncPrint("客户端 #" + clientId + " 下线了.");
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
                        rb.flip().position(5);
                        byte[] tmp = new byte[rb.get() & 0xFF];
                        rb.get(tmp);

                        int target = rb.getInt(1);
                        syncPrint("客户端 #" + rb.getInt(1) + " 向你说 \"" + AbstLexer.addSlashes(
                                new String(tmp, StandardCharsets.UTF_8)) + "\"");
                        break;
                    default:
                        int bc = (rb.get(0) & 0xFF) - 0x20;
                        if(rb.position() == 1 && bc >= 0 && bc < ERROR_NAMES.length) {
                            syncPrint("错误 " + ERROR_NAMES[bc]);
                        } else {
                            syncPrint("未知数据包: " + NIOUtil.dumpBuffer(rb));
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
            rb.clear();
            int t = 1000;
            while (ch.read() == 0 && t-- > 0) {
                LockSupport.parkNanos(10000);
            }
        } catch (Throwable e) {
            onError(ch, e);
        } finally {
            try {
                int i = 10;
                while (!ch.shutdown() && i-- > 0) {
                    LockSupport.parkNanos(10000);
                }
            } catch (IOException ignored) {}
            for (Pipe pair : socketsById.values()) {
                try {
                    pair.close();
                } catch (IOException ignored) {}
            }
            socketsById.clear();
            ch.close();
            tm.interrupt();
        }
    }

    private void reset(Pipe pipe) throws Exception {
        SpAttach att = (SpAttach) pipe.att;
        if (DEBUG) syncPrint(att + " reset.");
        Socket c = new Socket();
        try {
            c.setReuseAddress(true);
            initSocketPref(c);
            c.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), portMap[att.portId]), 1000);
            pipe.setClient(NIOUtil.fd(c));
            PipeIOThread.syncRegister(this, pipe, null);
        } catch (Throwable e) {
            try {
                c.close();
            } catch (IOException ignored) {}
            try {
                pipe.close();
            } catch (IOException ignored) {}
            syncPrint("管道错误 #" + att.channelId + " of " + att.clientId);
            e.printStackTrace();
        }
    }

    private boolean hostLogin(WrappedSocket ch, String motd) throws IOException {
        handshakeClient(ch, null);
        ByteBuffer rb = ch.buffer();

        rb.clear();
        byte[] idBytes = id.getBytes(StandardCharsets.UTF_8);
        byte[] tokenBytes = token.getBytes(StandardCharsets.UTF_8);
        byte[] motdBytes = motd.getBytes(StandardCharsets.UTF_8);
        rb.put((byte) PS_LOGIN_H)
          .put((byte) idBytes.length)
          .put((byte) tokenBytes.length)
          .put((byte) motdBytes.length)
          .put((byte) portMap.length)
          .put(idBytes).put(tokenBytes)
          .put(motdBytes);
        for (char c : portMap) {
            rb.putChar(c);
        }
        rb.flip();
        if(writeAndFlush(ch, rb, TIMEOUT) < 0) {
            throw new IOException("登录发送超时");
        }
        rb.clear();

        int heart = TIMEOUT;
        int except = 1;

        while (!shutdown) {
            int read;
            if ((read = ch.read( except - rb.position())) == 0 && rb.position() < except) {
                LockSupport.parkNanos(1000_000);
                if (heart-- < 0) {
                    throw new IOException("等待登录回复超时");
                }
                continue;
            }

            if (read < 0) throw new IOException("未预料的连接关闭");

            switch (rb.get(0) & 0xFF) {
                case PC_LOGON_H:
                    if (rb.position() < 2) {
                        except = 2;
                        continue;
                    }
                    int infoLen = rb.get(1) & 0xFF;
                    if (rb.position() < infoLen + 2) {
                        except = infoLen + 2;
                        continue;
                    }
                    rb.position(2);
                    byte[] infoBytes = new byte[infoLen];
                    rb.get(infoBytes);
                    String info = new String(infoBytes, StandardCharsets.UTF_8);
                    syncPrint("MOTD: " + info);

                    return true;
                case P_LOGOUT:
                    syncPrint("服务端断开连接");
                    return false;
                default:
                    int bc;
                    if(rb.position() == 1 && (bc = (0xFF & rb.get(0)) - 0x20) >= 0 && bc < ERROR_NAMES.length) {
                        syncPrint("错误 " + ERROR_NAMES[bc]);
                    } else {
                        syncPrint("无效数据包");
                    }
                    return false;
            }
        }
        return false;
    }

    public void setPortMap(char... chars) {
        this.portMap = chars;
    }

    public static final class Client {
        public final String addr;
        public final long connect = System.currentTimeMillis();

        Client(String addr) {this.addr = addr;}
    }
}
