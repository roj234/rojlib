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

import roj.io.NonblockingUtil;
import roj.net.NetworkUtil;
import roj.net.tcp.client.HttpClient;
import roj.net.tcp.util.InsecureSocket;
import roj.net.tcp.util.SecureSocket;
import roj.net.tcp.util.WrappedSocket;
import roj.util.ByteList;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.locks.LockSupport;

import static roj.net.cross.AEServer.*;

/**
 * Your description here
 *
 * @author Roj233
 * @version 0.1
 * @since 2021/8/18 0:09
 */
public class AEClient {
    boolean ssl;
    Socket remote;
    String id ="123", token = "test";
    long lastHeart;

    ServerSocket local;
    WrappedSocket client;

    public AEClient(InetSocketAddress local, boolean ssl) throws IOException {
        ServerSocket socket = this.local = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(local, 32);
        this.remote = new Socket();
        this.ssl = ssl;
    }

    public void close() throws IOException {
        local.close();
        if(remote != null)
            remote.close();
        if(client != null)
            client.close();
    }

    public void waitForLocal() throws IOException {
        Socket client = local.accept();
        this.client = new InsecureSocket(client, NonblockingUtil.fd(client));
        synchronized (this) {
            notify();
        }
    }

    public void connectAndWork(InetSocketAddress server) throws IOException {
        while (client == null) {
            syncPrint("等待本地客户端连接");
            synchronized (this) {
                try {
                    wait();
                } catch (InterruptedException ignored) {}
            }
        }

        this.remote.connect(server);
        WrappedSocket channel = ssl ? SecureSocket.get(remote, NonblockingUtil.fd(remote), HttpClient.CLIENT_ALLOCATOR, true) : new InsecureSocket(remote, NonblockingUtil.fd(remote));
        try {
            int read = 0;
            x:
            {
                int wait = TIMEOUT_CONNECT;
                while (!channel.handShake()) {
                    LockSupport.parkNanos(1000);
                    if(wait-- <= 0) {
                        System.out.println("T1 连接超时");
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
                        System.out.println("T2 连接超时");
                        break x;
                    }
                }
                buf.clear();

                while (channel.read() == 0) {
                    LockSupport.parkNanos(1000);
                    if(wait-- <= 0) {
                        System.out.println("T3 连接超时");
                        break x;
                    }
                }
                if(buf.getU(0) != PS_SERVER_HALLO) {
                    throw new SocketException("Unknown protocol " + buf);
                }

                wait = TIMEOUT_ESTABLISHED;

                ByteReader r = new ByteReader(buf);
                ByteWriter w = new ByteWriter(buf);
                WrappedSocket client = this.client;

                buf.clear();
                w.writeByte((byte) PS_CONNECT).writeByte((byte) id.length()).writeByte((byte) token.length()).writeAllUTF(id).writeAllUTF(token);
                while (buf.writePos() < buf.pos()) {
                    channel.write(buf);
                    LockSupport.parkNanos(1000);
                    if(wait-- <= 0) {
                        System.out.println("T4 连接超时");
                        break x;
                    }
                }
                buf.clear();

                wait = 1000;
                int except_length = -1;
                main:
                while (true) {
                    while ((read = channel.read()) == 0 || buf.pos() < except_length) {
                        LockSupport.parkNanos(1000);
                        int r1 = client.read();
                        if(r1 > 0) {
                            wait = 8;
                            do {
                                while ((read = channel.read()) > 0);
                                LockSupport.parkNanos(1000);
                                if (read < -1) break main;
                                if (read == -1) break;
                            } while (wait-- > 0);

                            buf.clear();
                            buf.add((byte) PS_DATA);
                            ByteList cb = client.buffer();
                            w.writeInt(cb.pos()).writeBytes(cb);
                            wait = TIMEOUT_TRANSFER;
                            while (true) {
                                channel.write(buf);
                                if (buf.writePos() == buf.pos())
                                    break;
                                LockSupport.parkNanos(1000);
                                if(wait-- <= 0) {
                                    System.out.println(this + ": 数据发送超时! Total " + buf.pos() + " Wrote " + buf.writePos());
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
                                    System.out.println(this + ": 客户端没收到心跳!");
                                    break;
                                }
                            }
                            channel.dataFlush();
                            buf.clear();
                            break;
                        } else if (r1 != -1) {
                            System.out.println("客户端断开连接... " + r1);
                            waitForLocal();
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
                            System.out.println(this + ": 服务器报告客户端长时间没心跳");
                            buf.clear();
                            break main;
                        case PS_LOGIN_STATE:
                            System.out.println(this + ": 登录成功，是否为主机: " + buf.getU(1));
                            buf.clear();
                            break;
                        case PS_STATE:
                        case PS_STATE_SLAVE:
                            System.out.println(this + ": 上次发送消息的转发状态: " + buf);
                            buf.clear();
                            break;
                        case PS_SERVER_DATA:
                        case PS_SERVER_SLAVE_DATA:
                            r.index = 1;
                            wait = TIMEOUT_ESTABLISHED;
                            if(buf.pos() < 5) {
                                except_length = 5;
                                break;
                            }
                            int value = r.readInt();
                            if(buf.pos() + 5 < value) {
                                except_length = value + 5;
                                break;
                            }
                            System.out.println(this + ": recv data " + value);
                            boolean slave = buf.getU(0) == PS_SERVER_SLAVE_DATA;
                            System.out.println(" from is slave: " + slave);
                            except_length = -1;

                            buf.writePos(5);
                            if(slave) {
                                int addrLen = buf.getU(buf.pos() - 1);
                                r.index = buf.pos() - 2 - addrLen;
                                byte[] addr = r.readBytes(addrLen);
                                int port = r.readUnsignedShort();
                                System.out.println("From: " + NetworkUtil.bytes2ip(addr) + ":" + port);
                                buf.pos(buf.pos() - addrLen - 2);
                            }
                            while (true) {
                                client.write(buf);
                                if(buf.writePos() == buf.pos())
                                    break;
                                LockSupport.parkNanos(1000);
                                if(wait-- <= 0) {
                                    System.out.println(this + ": 本地消息传输超时! Total " + buf.pos() + " Wrote " + buf.writePos());
                                    break;
                                }
                            }
                            break;
                        case PS_ERROR:
                            if(buf.pos() > 1) {
                                r.index = 1;
                                syncPrint(this + ": ERROR: " + r.readUByte());
                            } else {
                                syncPrint(this + ": UNKNOWN ERROR: " + buf);
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
                System.out.println("连接非正常断开: " + read);
            } else {
                System.out.println("连接断开");
            }

            while (!channel.shutdown());
            channel.close();
            while (!client.shutdown());
            client.close();
        } catch (Throwable e) {
            String msg = e.getMessage();
            if (!"Broken pipe".equals(msg) && !"Connection reset by peer".equals(msg)) {
                syncPrint("I/O Error: " + e);
                e.printStackTrace();
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