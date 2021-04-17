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
import roj.concurrent.task.ITaskNaCl;
import roj.io.NonblockingUtil;
import roj.net.ssl.EngineAllocator;
import roj.net.ssl.SslConfig;
import roj.net.ssl.SslEngineFactory;
import roj.net.tcp.util.InsecureSocket;
import roj.net.tcp.util.SecureSocket;
import roj.net.tcp.util.WrappedSocket;
import roj.util.ByteList;
import roj.util.ByteReader;
import roj.util.ByteWriter;
import roj.util.FastLocalThread;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.GeneralSecurityException;
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
    static final EngineAllocator AE_SSL;

    static {
        EngineAllocator alloc = null;
        try {
            InputStream stream = AEClient.class.getResourceAsStream("/META-INF/client.ks");
            if (stream != null)
            alloc = SslEngineFactory.getSslFactory(new SslConfig() {
                @Override
                public boolean isServerSide() {
                    return false;
                }

                @Override
                public InputStream getPkPath() {
                    return null;
                }

                @Override
                public InputStream getCaPath() {
                    return stream;
                }

                @Override
                public char[] getPasswd() {
                    return "123456".toCharArray();
                }
            });
        } catch (IOException | GeneralSecurityException e) {
            e.printStackTrace();
        }
        AE_SSL = alloc;
    }

    boolean ssl;
    String id, token;

    ServerSocket      local;
    InetSocketAddress localServer, server;

    final MyHashSet<Worker> workers;
    final TaskRunner task;
    int freeThreads, maxWorkers, error;

    boolean shutdownRequested;

    AEClient(String roomId, String roomToken, InetSocketAddress server, InetSocketAddress local, boolean ssl, int magic) {
        this.id = roomId;
        this.token = roomToken;
        this.server = server;
        this.ssl = ssl;
        this.workers = new MyHashSet<>();
        this.maxWorkers = 32;
        this.task = new TaskRunner();
        if(local != null)
            this.localServer = local;
    }

    public AEClient(String roomId, String roomToken, InetSocketAddress server, InetSocketAddress local, boolean ssl) throws IOException {
        this(roomId, roomToken, server, null, ssl, 0);
        ServerSocket socket = this.local = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(local, maxWorkers);
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
        }
        for (Object o : arr) {
            Worker w = (Worker) o;
            try {
                w.join();
            } catch (InterruptedException ignored) {}
        }
    }

    public void run() {
        long t = System.currentTimeMillis();
        while (true) {
            Socket client;
            try {
                client = local.accept();
            } catch (IOException e) {
                break;
            }
            if (error > (System.currentTimeMillis() - t) / 10) {
                break;
            }
            t = System.currentTimeMillis();
            error = 0;
            try {
                if (workers.size() >= maxWorkers) {
                    client.close();
                    syncPrint("连接数超限! " + workers.size() + "/" + maxWorkers);
                }
                initSocketPref(client);
                InsecureSocket client1 = new InsecureSocket(client, NonblockingUtil.fd(client));
                x:
                synchronized (workers) {
                    if (freeThreads > 0) {
                        for (Worker wx : workers) {
                            if (wx.client == null) {
                                wx.client = client1;
                                LockSupport.unpark(wx.self);
                                freeThreads--;
                                break x;
                            }
                        }
                    }
                    task.pushTask(new Worker(client1));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            local.close();
        } catch (IOException ignored) {}
    }

    class Worker extends FastLocalThread implements ITaskNaCl {
        Worker(WrappedSocket client) {
            setDaemon(true);
            this.client = client;
            this.ob = new ByteList();
        }

        Thread self;
        WrappedSocket client;
        ByteList ob;

        // 子机ID
        int slaveId;
        long lastConnect;

        // 统计信息
        long lastHeart;

        @Override
        public String toString() {
            return client + " #" + slaveId;
        }

        @Override
        public void run() {
            self = this;
            try {
                run1();
            } catch (IOException e) {
                e.printStackTrace();
            }
            synchronized (workers) {
                workers.remove(this);
            }
            slaveId = -1;
        }

        void run1() throws IOException {
            self.setName("SW " + this);
            Socket remote = new Socket();
            try {
                remote.connect(server);
            } catch (ConnectException e) {
                syncPrint("无法连接至服务器");
                error++;
                return;
            }
            WrappedSocket channel = ssl ? SecureSocket.get(remote, NonblockingUtil.fd(remote), AE_SSL, true) : new InsecureSocket(remote, NonblockingUtil.fd(remote));
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
                    if(writeAndFlush(channel, buf, TIMEOUT_TRANSFER) < 0) {
                        syncPrint(this + ": 连接数据包发送超时");
                        break conn;
                    }
                    buf.clear();

                    int heart = 0;
                    int except = -1;
                    while (!shutdownRequested) {
                        while ((read = channel.read(except == -1 ? 1 : except - buf.pos())) == 0 || buf.pos() < except) {
                            if(--heart <= 0) {
                                if(heart % T_CLIENT_HEARTBEAT_RETRY == 0) {
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
                            if (r1 != 0) {
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
                                    if (freeThreads < maxWorkers) {
                                        synchronized (workers) {
                                            freeThreads++;
                                        }
                                        syncPrint(this + ": free idle 30");
                                        client = null;
                                        // 连接保留30s
                                        lastConnect = System.currentTimeMillis();
                                        if(writeEx(channel, (byte) PS_RESET) < 0) {
                                            syncPrint(this + ": 重置发送失败");
                                            break conn;
                                        }
                                    } else {
                                        syncPrint(this + ": 断开(释放)");
                                        break conn;
                                    }
                                }
                            } else {
                                LockSupport.parkNanos(20);
                                if (client == null && System.currentTimeMillis() - lastConnect > 30000) {
                                    syncPrint(this + ": 断开(释放,超时)");
                                    synchronized (workers) {
                                        freeThreads--;
                                    }
                                    break conn;
                                }
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
                                slaveId = r.readInt();
                                syncPrint(this + ": 登录成功 #" + slaveId);
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
                                error++;
                                int bc = buf.getU(0) - 0x20;
                                if(buf.pos() == 1 && bc >= 0 && bc < ERROR_NAMES.length) {
                                    syncPrint(this + ": 错误 " + ERROR_NAMES[bc]);
                                } else {
                                    syncPrint(this + ": 未知数据包: " + buf);
                                }
                                buf.clear();
                                break conn;
                        }
                        heart = T_CLIENT_HEARTBEAT_TIME;
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

        @Override
        public void calculate(Thread thread) throws Exception {
            // noinspection all
            interrupted();
            self = thread;
            try {
                run1();
            } finally {
                self.setName("SW Idle");
                synchronized (workers) {
                    workers.remove(this);
                }
            }
            slaveId = -1;
        }

        @Override
        public boolean isDone() {
            return slaveId == -1;
        }
    }

    void onError(WrappedSocket channel, Throwable e) {
        ByteList buf = channel.buffer();
        int bc;
        if(buf.pos() == 1 && (bc = buf.getU(0) - 0x20) >= 0 && bc < ERROR_NAMES.length) {
            syncPrint(channel + ": 错误 " + ERROR_NAMES[bc]);
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