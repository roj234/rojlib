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

import roj.collect.MyHashSet;
import roj.io.NonblockingUtil;
import roj.net.tcp.client.HttpClient;
import roj.net.tcp.util.InsecureSocket;
import roj.net.tcp.util.SecureSocket;
import roj.net.tcp.util.WrappedSocket;
import roj.util.ByteList;
import roj.util.ByteReader;
import roj.util.ByteWriter;
import roj.util.FastLocalThread;

import java.io.Closeable;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.LockSupport;

import static roj.net.cross.Util.*;

/**
 * AbyssalEye Dedicated Client
 *
 * @author Roj233
 * @version 0.3.1
 * @since 2021/8/18 0:09
 */
public class AEClient implements Runnable, Closeable {
    boolean ssl;
    String id, token;

    ServerSocket      local;
    InetSocketAddress localServer, server;
    final MyHashSet<Worker> workers;
    private final List<Worker> free;
    boolean           shutdownRequested;
    int maxFreeThreads;

    AEClient(String roomId, String roomToken, InetSocketAddress server, InetSocketAddress local, boolean ssl, int magic) {
        this.id = roomId;
        this.token = roomToken;
        this.server = server;
        this.ssl = ssl;
        this.workers = new MyHashSet<>();
        this.free = new ArrayList<>(this.maxFreeThreads = 10);
        if(local != null)
            this.localServer = local;
    }

    public AEClient(String roomId, String roomToken, InetSocketAddress server, InetSocketAddress local, boolean ssl) throws IOException {
        this(roomId, roomToken, server, null, ssl, 0);
        ServerSocket socket = this.local = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(local, 32);
    }

    public void close() throws IOException {
        if(local != null)
            local.close();
        Object[] arr;
        synchronized (workers) {
            arr = workers.toArray(new Object[workers.size()]);
        }
        shutdownRequested = true;
        for (Object o : arr) {
            Worker w = (Worker) o;
            w.interrupt();
            LockSupport.unpark(w);
        }
        for (Object o : arr) {
            Worker w = (Worker) o;
            try {
                w.join();
            } catch (InterruptedException ignored) {}
        }
    }

    public void run() {
        while (true) {
            Socket client;
            try {
                client = local.accept();
            } catch (IOException e) {
                break;
            }
            try {
                initSocketPref(client);
                InsecureSocket client1 = new InsecureSocket(client, NonblockingUtil.fd(client));
                Worker w = null;
                if(free.size() > 0) {
                    synchronized (free) {
                        if(free.size() > 0) {
                            w = free.remove(free.size() - 1);
                        }
                    }
                }
                if(w == null) {
                    w = new Worker(client1);
                    synchronized (workers) {
                        workers.add(w);
                    }
                    w.start();
                } else {
                    //syncPrint(w + ": 重用");
                    w.client = client1;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    class Worker extends FastLocalThread {
        Worker(WrappedSocket client) {
            setDaemon(true);
            this.client = client;
            this.ob = new ByteList();
        }

        WrappedSocket client;
        long     lastHeart;
        ByteList ob;

        @Override
        public String toString() {
            return String.valueOf(client);
        }

        @Override
        public void run() {
            try {
                run1();
            } catch (IOException e) {
                e.printStackTrace();
            }
            synchronized (free) {
                free.remove(this);
            }
            synchronized (workers) {
                workers.remove(this);
            }
        }

        void run1() throws IOException {
            Socket remote = new Socket();
            try {
                remote.connect(server);
            } catch (ConnectException e) {
                syncPrint("无法连接至服务器");
                return;
            }
            WrappedSocket channel = ssl ? SecureSocket.get(remote, NonblockingUtil.fd(remote), HttpClient.CLIENT_ALLOCATOR, true) : new InsecureSocket(remote, NonblockingUtil.fd(remote));
            try {
                int read = 0;
                conn:
                {
                    int result = handshakeClient(channel);
                    if(result != 0) {
                        syncPrint("握手失败: " + result);
                        break conn;
                    }
                    ByteList buf = channel.buffer();
                    ByteReader r = new ByteReader(buf);
                    ByteWriter w = new ByteWriter(buf);

                    w.writeByte((byte) PS_CONNECT).writeByte((byte) ByteWriter.byteCountUTF8(id)).writeByte((byte) ByteWriter.byteCountUTF8(token)).writeByte((byte) 0).writeAllUTF(id).writeAllUTF(token);
                    if(writeAndFlush(channel, buf, TIMEOUT_ESTABLISHED) < 0) {
                        syncPrint(this + ": 连接数据包发送超时");
                        break conn;
                    }
                    buf.clear();

                    int heart = T_CLIENT_HEARTBEAT_INIT;
                    int except = -1;
                    while (!shutdownRequested) {
                        while ((read = channel.read(except == -1 ? 1 : except - buf.pos())) == 0 || buf.pos() < except) {
                            if(heart-- <= 0) {
                                if(heart == -1 || heart == -500) {
                                    if (writeEx(channel, (byte) PS_HEARTBEAT) < 0) {
                                        syncPrint(this + ": 心跳发送失败");
                                    }
                                } else if(heart < -T_CLIENT_HEARTBEAT_TIMEOUT) {
                                    syncPrint(this + ": 没收到服务端心跳");
                                    break conn;
                                }
                            }
                            if(shutdownRequested) break conn;
                            int r1 = client == null ? 0 : client.read();
                            if(r1 != 0) {
                                if(r1 > 0) {
                                    ByteList ob = this.ob;
                                    ob.clear();
                                    ByteList cb = client.buffer();
                                    w.list = ob;
                                    w.writeByte((byte) PS_DATA).writeInt(cb.pos()).writeBytes(cb)
                                     .list = buf;
                                    cb.clear();

                                    if (writeAndFlush(channel, ob, TIMEOUT_TRANSFER) < 0) {
                                        syncPrint(this + ": 数据发送失败! " + buf.writePos() + "/" + buf.pos());
                                        break;
                                    }
                                    ob.clear();
                                } else {
                                    //syncPrint(this + ": 下级断开连接");
                                    synchronized (free) {
                                        if(free.size() < maxFreeThreads) {
                                            free.add(this);
                                        } else {
                                            break conn;
                                        }
                                    }
                                    if(writeEx(channel, (byte) PS_RESET) < 0) {
                                        syncPrint(this + ": 重置发送失败");
                                    }
                                    client = null;
                                }
                            } else {
                                LockSupport.parkNanos(20);
                            }
                        }
                        if (read < 0 || shutdownRequested) {
                            break;
                        }
                        if(buf.pos() < 1) {
                            continue;
                        }
                        switch (buf.get(0) & 0xFF) {
                            case PS_HEARTBEAT:
                                lastHeart = System.currentTimeMillis();
                                buf.clear();
                                break;
                            case PS_LOGON:
                                if(buf.pos() < 5) {
                                    except = 5;
                                    break;
                                }
                                except = -1;
                                r.index = 1;
                                syncPrint(this + ": 登录成功 #" + r.readInt());
                                buf.clear();
                                break;
                            case PS_DISCONNECT:
                                syncPrint(this + ": 断开连接(协议)");
                                break conn;
                            case PS_STATE:
                                if(buf.pos() < 2) {
                                    except = 2;
                                    break;
                                }
                                except = -1;
                                syncPrint(this + ": 上次的转发状态: " + RSTATE_NAMES[buf.getU(1)]);
                                buf.clear();
                                break;
                            case PS_SERVER_DATA:
                                r.index = 1;
                                if(buf.pos() < 5) {
                                    except = 5;
                                    break;
                                }
                                int value = r.readInt();
                                if(buf.pos() < value + 5) {
                                    except = value + 5;
                                    break;
                                }
                                except = -1;

                                if(client == null) {
                                    syncPrint(this + ": 丢弃转发数据? " + value);
                                    buf.clear();
                                    break;
                                }
                                buf.writePos(5);
                                if(writeAndFlush(client, buf, TIMEOUT_TRANSFER) < 0) {
                                    syncPrint(this + ": 本地传输超时 " + buf.writePos() + "/" + buf.pos());
                                    break;
                                }
                                buf.clear();
                                break;
                            default:
                                int bc = buf.getU(0) - 0x20;
                                if(buf.pos() == 1 && bc >= 0 && bc < ERROR_NAMES.length) {
                                    syncPrint(this + ": 错误 " + ERROR_NAMES[bc]);
                                } else {
                                    syncPrint(this + ": 未知数据包: " + buf);
                                }
                                break conn;
                        }
                        heart = T_CLIENT_HEARTBEAT_RECV;
                    }
                }
                if(read < 0) {
                    syncPrint("连接非正常断开: " + read);
                } else {
                    syncPrint("连接断开");
                }

                try {
                    writeEx(channel, (byte) PS_DISCONNECT);
                } catch (IOException ignored) {}
                while (!channel.shutdown()) {
                    LockSupport.parkNanos(100);
                }
                channel.close();
                if(client != null) {
                    while (!client.shutdown()) {
                        LockSupport.parkNanos(100);
                    }
                    client.close();
                }
            } catch (Throwable e) {
                onError(channel, e);
            }
        }
    }

    void onError(WrappedSocket channel, Throwable e) {
        ByteList buf = channel.buffer();
        int bc;
        if(buf.pos() == 1 && (bc = buf.getU(0) - 0x20) >= 0 && bc < ERROR_NAMES.length) {
            syncPrint(this + ": 错误 " + ERROR_NAMES[bc]);
        } else {
            String msg = e.getMessage();
            if (!"Broken pipe".equals(msg) && !"Connection reset by peer".equals(msg)) {
                e.printStackTrace();
            }
        }

        try {
            /*
             * Only try once
             */
            channel.shutdown();
        } catch (IOException ignored) {}

        try {
            channel.close();
        } catch (IOException ignored) {}
    }
}