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
import roj.concurrent.task.ITaskNaCl;
import roj.config.data.CList;
import roj.config.word.AbstLexer;
import roj.io.NIOUtil;
import roj.net.misc.MSSClientKey;
import roj.net.misc.PacketBuffer;
import roj.net.misc.Pipe;
import roj.net.misc.PipeIOThread;
import roj.net.tcp.MSSSocket;
import roj.net.tcp.PlainSocket;
import roj.net.tcp.WrappedSocket;
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
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

import static roj.net.cross.Util.*;

/**
 * AbyssalEye Host
 *
 * @author Roj233
 * @version 0.3.1
 * @since 2021/9/12 0:57
 */
public class AEHost extends IAEClient {
    static {
        MSSClientKey.loadMSSCert();
    }

    IntMap<Client> clients;
    char[] portMap;

    PacketBuffer kick;

    public AEHost(SocketAddress server, boolean ssl, String id, String token) {
        super(server, ssl, id, token);
        this.clients = new IntMap<>();
        this.portMap = new char[1];
        this.kick = new PacketBuffer();
    }

    public void kickSome(int... clientIds) {
        ByteBuffer tmp = ByteBuffer.allocate(clientIds.length * 5);
        for (int i : clientIds) {
            tmp.put((byte) PS_KICK_CLIENT).putInt(i);
        }
        tmp.flip();
        kick.offer(tmp);
    }

    public void chat(int clientId, String message) {
        byte[] mb = message.getBytes(StandardCharsets.UTF_8);
        ByteBuffer tmp = ByteBuffer.allocate(6 + mb.length);
        tmp.put((byte) P_MSG).putInt(clientId).put((byte) mb.length).put(mb).flip();
        kick.offer(tmp);
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
            if (!hostLogin(ch, "阿伟死了么")) return;

            // 留空
            Arrays.fill(super.free = Helpers.cast(new List<?>[portMap.length]), Collections.emptyList());

            ByteBuffer rb = ch.buffer();
            rb.clear();

            int heart = 0;
            int except = 1;
            conn:
            while (!shutdown) {
                int read;
                if ((read = ch.read(except - rb.position())) == 0 && rb.position() < except) {
                    LockSupport.parkNanos(50);
                    intr:
                    if (rb.position() == 0) {
                        if (!kick.take(rb)) break intr;
                        rb.flip();
                        if (writeAndFlush(ch, rb, TIMEOUT_TRANSFER) < 0) {
                            syncPrint(this + ": PBW超时");
                            break;
                        }
                        rb.clear();
                    }
                    if (heartbeat(ch, --heart, true)) continue;
                    break;
                }

                if (read < 0) throw new EOFException("未预料的连接关闭: " + read);

                switch (rb.get(0) & 0xFF) {
                    case P_HEARTBEAT:
                        break;
                    case P_LOGOUT:
                        syncPrint(" 连接断开");
                        break conn;
                    case PS_CHANNEL_CLOSE:
                        if(rb.position() < 9) {
                            except = 9;
                            continue;
                        }
                        Pipe pair = socketsById.remove(rb.getInt(5));
                        pair.release();
                        syncPrint((rb.getInt(1) < 0 ? "服务端" : "客户端") + "关闭了频道 #" +
                                          Integer.toHexString(rb.getInt(5)));
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
                        syncPrint(" 客户端 #" + clientId + " 尝试开启频道");

                        if (socketsById.size() > MAX_CHANNEL_COUNT) {
                            rb.clear();
                            rb.put((byte) P_CHANNEL_OPEN_FAIL).putInt(clientId);
                            byte[] reasonBytes = "这边开启的频道过多".getBytes(StandardCharsets.UTF_8);
                            rb.put((byte) reasonBytes.length).put(reasonBytes).flip();

                            if (writeAndFlush(ch, rb, TIMEOUT_TRANSFER) < 0) {
                                syncPrint(" COF 超时");
                                break conn;
                            }
                        } else {
                            long pipeId = rb.getLong();

                            rb.clear();
                            rb.put((byte) PS_CHANNEL_OPEN).putInt(clientId)
                              .put(ciphers, 32, 32).flip();
                            if (writeAndFlush(ch, rb, TIMEOUT_TRANSFER) < 0) {
                                syncPrint(" CO 超时");
                                break conn;
                            }

                            SpAttach att = new SpAttach();
                            socketsById.put(att.channelId = (int) (pipeId >>> 32),
                                            pair = pipeLogin(pipeId, ciphers));
                            att.portId = (byte) portId;
                            pair.att = att;
                            new AsyncConnector(portMap[portId], pair).run();
                            clients.get(clientId).channels.add(pair);
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
                        Client cli = new Client();
                        cli.clientId = rb.getInt();
                        char port = rb.getChar();
                        byte[] ip = new byte[rb.get() & 0xFF];
                        rb.get(ip);
                        cli.address = new InetSocketAddress(InetAddress.getByAddress(ip), port);
                        cli.channels = new ArrayList<>();
                        clients.put(cli.clientId, cli);
                        syncPrint(" 客户端 #" + cli.clientId + " 上线了, 它来自 " + cli.address);
                        break;
                    case PH_CLIENT_LOGOUT:
                        if(rb.position() < 5) {
                            except = 5;
                            continue;
                        }
                        clientId = rb.getInt(1);
                        clients.remove(clientId);
                        syncPrint(" 客户端 #" + clientId + " 下线了.");
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
                        rb.flip().position(4);
                        byte[] tmp = new byte[rb.get() & 0xFF];
                        rb.get(tmp);

                        int target = rb.getInt(1);
                        syncPrint(" 客户端 #" + rb.getInt(1) + " 向你说 \"" + AbstLexer.addSlashes(
                                new String(tmp, StandardCharsets.UTF_8)) + "\"");
                        break;
                    default:
                        int bc = (rb.get(0) & 0xFF) - 0x20;
                        if(rb.position() == 1 && bc >= 0 && bc < ERROR_NAMES.length) {
                            syncPrint(" 错误 " + ERROR_NAMES[bc]);
                        } else {
                            syncPrint(" 未知数据包: " + dumpBuffer(rb));
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
            rb.clear();
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
                    pair.release();
                } catch (IOException ignored) {}
            }
            socketsById.clear();
            ch.close();
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
        if(writeAndFlush(ch, rb, TIMEOUT_TRANSFER) < 0) {
            throw new IOException("登录发送超时");
        }
        rb.clear();

        int heart = TIMEOUT_TRANSFER;
        int except = 1;

        while (!shutdown) {
            int read;
            if ((read = ch.read( except - rb.position())) == 0 && rb.position() < except) {
                LockSupport.parkNanos(50);
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
                    syncPrint(" 服务器欢迎消息: " + info);
                    ch.poll();

                    return true;
                case P_LOGOUT:
                    syncPrint(" 服务端断开连接");
                    return false;
                default:
                    int bc;
                    if(rb.position() == 1 && (bc = (0xFF & rb.get(0)) - 0x20) >= 0 && bc < ERROR_NAMES.length) {
                        syncPrint(" 错误 " + ERROR_NAMES[bc]);
                    } else {
                        syncPrint(" 无效数据包");
                    }
                    return false;
            }
        }
        return false;
    }

    static final class Client {
        int               clientId;
        List<Pipe>        channels;
        InetSocketAddress address;

        @Override
        public String toString() {
            return "Client{" + address + '#' + clientId + '}';
        }

        public void serialize(CList lx) {

        }
    }

    static final Consumer<Pipe> PIPE_INVALID_CB = pipe -> {
        System.out.println("管道失效回调: " + pipe);
        if (pipe.isUpstreamEof() || pipe.isReleased()) return;
        pipe.setClient(null);
    };

    final class AsyncConnector implements Runnable, ITaskNaCl {
        private final char port;
        private final Pipe pair;

        AsyncConnector(char port, Pipe pair) {
            this.port = port;
            this.pair = pair;
        }

        public void run() {
            FileDescriptor fd;
            Socket c = new Socket();
            try {
                c.setReuseAddress(true);
                initSocketPref(c);
                c.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 10000);
                fd = NIOUtil.fd(c);
            } catch (IOException e) {
                e.printStackTrace();
                try {
                    c.close();
                } catch (IOException ignored) {}
                return;
            }
            pair.setClient(fd);
            try {
                PipeIOThread.syncRegister(AEHost.this, pair, PIPE_INVALID_CB);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void calculate(Thread thread) {
            run();
        }

        @Override
        public boolean isDone() {
            return !pair.isEof();
        }
    }

    public void setPortMap(char... chars) {
        this.portMap = chars;
    }
}
