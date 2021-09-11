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

import java.io.Closeable;
import java.io.IOException;
import java.net.*;
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

    ServerSocket local;
    InetSocketAddress local2;
    InetSocketAddress server;
    MyHashSet<Worker> workers;
    boolean shutdownRequested;

    AEClient(String roomId, String roomToken, InetSocketAddress server, InetSocketAddress local, boolean ssl, int magic) {
        this.id = roomId;
        this.token = roomToken;
        this.server = server;
        this.ssl = ssl;
        this.workers = new MyHashSet<>();
        if(local != null)
            this.local2 = local;
    }

    public AEClient(String roomId, String roomToken, InetSocketAddress server, InetSocketAddress local, boolean ssl) throws IOException {
        this(roomId, roomToken, server, null, ssl, 0);
        ServerSocket socket = this.local = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(local, 32);
    }

    public void close() throws IOException {
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
        while (true) {
            Socket client;
            try {
                client = local.accept();
            } catch (IOException e) {
                break;
            }
            try {
                Worker w = new Worker(new InsecureSocket(client, NonblockingUtil.fd(client)));
                synchronized (workers) {
                    workers.add(w);
                }
                w.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    class Worker extends Thread {
        Worker(WrappedSocket client) {
            this.client = client;
            setDaemon(true);
        }

        final WrappedSocket client;
        long lastHeart;

        @Override
        public String toString() {
            return client.toString();
        }

        @Override
        public void run() {
            try {
                run1();
            } catch (IOException e) {
                e.printStackTrace();
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
                x:
                {
                    int wait = TIMEOUT_CONNECT;
                    while (!channel.handShake()) {
                        LockSupport.parkNanos(1000);
                        if(wait-- <= 0) {
                            syncPrint("T1 连接超时");
                            break x;
                        }
                    }

                    ByteList buf = channel.buffer();
                    buf.clear();
                    buf.addAll(new byte[] {
                            'A','E','C','L','I','E','N','T','H','A','L','L','O'
                    });
                    while (buf.writePos() < buf.pos()) {
                        channel.write(buf);
                        LockSupport.parkNanos(1000);
                        if(wait-- <= 0) {
                            syncPrint("T2 连接超时");
                            break x;
                        }
                    }
                    buf.clear();

                    while (channel.read(1) == 0) {
                        LockSupport.parkNanos(1000);
                        if(wait-- <= 0) {
                            syncPrint("T3 连接超时");
                            break x;
                        }
                    }
                    if(buf.getU(0) != PS_SERVER_HALLO) {
                        throw new SocketException("协议错误: " + buf);
                    }

                    wait = TIMEOUT_ESTABLISHED;

                    ByteReader r = new ByteReader(buf);
                    ByteWriter w = new ByteWriter(buf);

                    buf.clear();
                    w.writeByte((byte) PS_CONNECT).writeByte((byte) id.length()).writeByte((byte) token.length()).writeByte((byte) 0).writeAllUTF(id).writeAllUTF(token);
                    while (buf.writePos() < buf.pos()) {
                        channel.write(buf);
                        LockSupport.parkNanos(1000);
                        if(wait-- <= 0) {
                            syncPrint("T4 连接超时");
                            break x;
                        }
                    }
                    buf.clear();

                    wait = 5000;
                    int except_length = -1;
                    main:
                    while (!shutdownRequested) {
                        while ((read = channel.read(except_length == -1 ? 1 : except_length - buf.pos())) == 0 || buf.pos() < except_length) {
                            LockSupport.parkNanos(1000);
                            int r1 = client.read();
                            if(r1 > 0) {
                                buf.clear();
                                buf.add((byte) PS_DATA);
                                ByteList cb = client.buffer();
                                w.writeInt(cb.pos()).writeBytes(cb);
                                cb.clear();
                                wait = TIMEOUT_TRANSFER;
                                while (true) {
                                    channel.write(buf);
                                    if (buf.writePos() == buf.pos())
                                        break;
                                    LockSupport.parkNanos(1000);
                                    if(wait-- <= 0) {
                                        syncPrint(this + ": 数据发送超时! All " + buf.pos() + " OK " + buf.writePos());
                                        break;
                                    }
                                }
                                buf.clear();
                                break;
                            } else if(wait-- <= 0) {
                                buf.clear();
                                buf.add((byte) PS_HEARTBEAT);
                                wait = 1000;
                                while (channel.write(buf) == 0) {
                                    LockSupport.parkNanos(1000);
                                    if(wait-- <= 0) {
                                        syncPrint(this + ": 客户端没收到心跳!");
                                        break;
                                    }
                                }
                                channel.dataFlush();
                                buf.clear();
                                break;
                            } else if (r1 < 0) {
                                syncPrint(this + ": 下级断开连接");
                                buf.clear();
                                buf.add((byte) PS_DISCONNECT);
                                while (channel.write(buf) == 0);
                                channel.dataFlush();
                                break main;
                            }
                        }
                        if(buf.pos() < 1) {
                            if (read < -1) {
                                break;
                            }
                            continue;
                        }
                        switch (buf.get(0) & 0xFF) {
                            case PS_HEARTBEAT:
                                lastHeart = System.currentTimeMillis();
                                buf.clear();
                                break;
                            case PS_TIMEOUT:
                                syncPrint(this + ": 服务器报告长时间没心跳");
                                buf.clear();
                                break main;
                            case PS_LOGON:
                                if(buf.pos() < 5) {
                                    except_length = 5;
                                    break;
                                }
                                except_length = -1;
                                r.index = 1;
                                int slaveId = r.readInt();
                                syncPrint(this + ": 登录成功 #" + slaveId);
                                buf.clear();
                                break;
                            case PS_DISCONNECT:
                                channel.read();
                                syncPrint(this + ": 服务器断开连接(协议): " + buf.getString());
                                buf.clear();
                                break main;
                            case PS_STATE_SLAVE:
                                if(buf.pos() < 2) {
                                    except_length = 2;
                                    break;
                                }
                                except_length = -1;
                                if(buf.getU(1) != 0)
                                    syncPrint(this + ": 上次的转发状态: " + RSTATE_NAMES[buf.getU(1)]);
                                buf.clear();
                                break;
                            case PS_SERVER_DATA:
                                r.index = 1;
                                wait = TIMEOUT_ESTABLISHED;
                                if(buf.pos() < 5) {
                                    except_length = 5;
                                    break;
                                }
                                int value = r.readInt();
                                if(buf.pos() < value + 5) {
                                    except_length = value + 5;
                                    break;
                                }
                                //syncPrint(this + ": 接受转发数据 " + value);
                                except_length = -1;

                                buf.writePos(5);
                                while (true) {
                                    client.write(buf);
                                    if(buf.writePos() == buf.pos())
                                        break;
                                    LockSupport.parkNanos(1000);
                                    if(wait-- <= 0) {
                                        syncPrint(this + ": 本地传输超时 All " + buf.pos() + " OK " + buf.writePos());
                                        break;
                                    }
                                }
                                buf.clear();
                                break;
                            case PS_ERROR:
                                while (channel.read(1) == 0);
                                if(buf.pos() > 1) {
                                    r.index = 1;
                                    syncPrint(this + ": 错误" + ERROR_NAMES[r.readUByte()]);
                                } else {
                                    syncPrint(this + ": 错误, 且连接过早终止");
                                }
                                buf.clear();
                                break main;
                            default:
                                syncPrint(this + ": UNKNOWN PACKET: " + buf);
                                buf.clear();
                                break main;
                        }
                        wait = 1000;
                    }
                }
                if(read < 0) {
                    syncPrint("连接非正常断开: " + read);
                } else {
                    syncPrint("连接断开");
                }

                while (!channel.shutdown());
                channel.close();
                while (!client.shutdown());
                client.close();
            } catch (Throwable e) {
                ByteList buf = channel.buffer();
                if(buf.pos() > 0 && buf.get(0) == PS_ERROR) {
                    if(buf.pos() > 1) {
                        syncPrint(this + ": 错误" + ERROR_NAMES[buf.getU(1)]);
                    } else {
                        syncPrint(this + ": 错误, 且连接过早终止");
                    }
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
    }
}