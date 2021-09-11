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
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import static roj.net.cross.Util.*;

/**
 * AbyssalEye Client Server
 *
 * @author Roj233
 * @version 0.3.1
 * @since 2021/9/12 0:57
 */
public final class AEClientOwner extends AEClient {
    public AEClientOwner(String roomId, String roomToken, InetSocketAddress server, InetSocketAddress local, boolean ssl) {
        super(roomId, roomToken, server, local, ssl, 0);
        channelById = new IntMap<>();
        this.outBuf = new ByteList();
    }

    IntMap<Worker> channelById;
    ByteList outBuf;

    @Override
    public void run() {
        try {
            run1();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String toString() {
        return id;
    }

    public void run1() throws IOException {
        Socket remote = new Socket();
        remote.connect(server);
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
                w.writeByte((byte) PS_CONNECT).writeByte((byte) id.length()).writeByte((byte) token.length()).writeByte((byte) 1).writeAllUTF(id).writeAllUTF(token);
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
                ArrayList<Worker> dead = new ArrayList<>();
                main:
                while (!shutdownRequested) {
                    while ((read = channel.read(except_length == -1 ? 1 : except_length - buf.pos())) == 0 || buf.pos() < except_length) {
                        LockSupport.parkNanos(1000);
                        ByteList ob = this.outBuf;
                        for (Worker wk : channelById.values()) {
                            while(!wk.lock.compareAndSet(0, -1))
                                LockSupport.parkNanos(1000);
                            ByteList buf1 = wk.client.buffer();
                            if(buf1.pos() > 0) {
                                ob.clear();
                                ob.add((byte) PS_DATA);
                                w.list = ob;
                                w.writeInt(buf1.pos() + 4).writeBytes(buf1).writeInt(wk.order)
                                        .list = buf;
                                buf1.clear();
                                while (ob.writePos() < ob.pos()) {
                                    int o = channel.write(ob);
                                    if(o < 0)
                                        break;
                                    else if (o == 0) {
                                        LockSupport.parkNanos(1000);
                                    }
                                }
                            }
                            if(!wk.alive)
                                dead.add(wk);
                            wk.lock.set(0);
                        }
                        for (int i = 0; i < dead.size(); i++) {
                            channelById.remove(dead.get(i).order);
                            syncPrint("分机断开(cleanup) #" + dead.get(i).order);
                        }
                        dead.clear();

                        if(wait-- <= 0) {
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
                        }
                    }
                    if(buf.pos() < 1) {
                        if (read < 0) {
                            break;
                        }
                        continue;
                    }
                    switch (buf.get(0) & 0xFF) {
                        case PS_HEARTBEAT:
                            //lastHeart = System.currentTimeMillis();
                            buf.clear();
                            break;
                        case PS_TIMEOUT:
                            syncPrint(this + ": 服务器报告客户端长时间没心跳");
                            buf.clear();
                            break main;
                        case PS_LOGON:
                            if(buf.pos() < 5) {
                                except_length = 5;
                                break;
                            }
                            except_length = -1;
                            syncPrint(this + ": 登录成功");
                            buf.clear();
                            break;
                        case PS_STATE:
                            if(buf.pos() < 2) {
                                except_length = 2;
                                break;
                            }
                            except_length = -1;
                            if(buf.getU(1) != 0)
                                syncPrint(this + ": 上次的转发状态: " + RSTATE_NAMES[buf.getU(1)]);
                            buf.clear();
                            break;
                        case PS_SLAVE_DISCONNECT:
                            if(buf.pos() < 5) {
                                except_length = 5;
                                break;
                            }
                            except_length = -1;
                            r.index = 1;
                            int id = r.readInt();
                            Worker wxxx = channelById.remove(id);
                            if(wxxx != null) {
                                syncPrint("分机断开 #" + wxxx.order);
                                wxxx.client.close();
                            } else {
                                syncPrint("分机已断开(cl) #" + id);
                            }
                            buf.clear();
                            break;
                        case PS_SLAVE_CONNECT:
                            if(buf.pos() < 8) { // PKT IDX IDX IDX IDX PRT PRT ADR [ADR]
                                except_length = 8;
                                break;
                            }
                            r.index = 1;
                            int index = r.readInt();
                            int port = r.readUnsignedShort();
                            int addrLen = r.readUByte();
                            if(buf.pos() < addrLen + 8) {
                                except_length = addrLen + 8;
                                break;
                            }
                            except_length = -1;
                            if(channelById.containsKey(index)) {
                                syncPrint("分机已连接 #" + index + " ???");
                                buf.clear();
                                break;
                            }
                            Socket soc = new Socket();
                            soc.connect(local2);
                            Worker wxx = new Worker(index, NetworkUtil.bytes2ip(r.readBytes(addrLen)) + ':' + port, new InsecureSocket(soc, NonblockingUtil.fd(soc)));
                            workers.add(wxx);
                            channelById.put(index, wxx);
                            wxx.start();
                            syncPrint("分机连接 #" + index + ", " + wxx.remoteIp);
                            buf.clear();
                            break;
                        case PS_SERVER_SLAVE_DATA:
                            if(buf.pos() < 9) { // PKT FRM FRM FRM FRM LEN LEN LEN LEN [len]
                                except_length = 9;
                                break;
                            }
                            r.index = 1;
                            int from = r.readInt();
                            int value = r.readInt();
                            if(buf.pos() < value + 9) {
                                except_length = value + 9;
                                break;
                            }

                            except_length = -1;

                            //syncPrint(this + ": 接收 " + value);
                            Worker worker = channelById.get(from);
                            if(worker == null) {
                                syncPrint("未知分机 #" + from);
                                buf.clear();
                                break;
                            }
                            while(!worker.lock.compareAndSet(0, -1))
                                LockSupport.parkNanos(1000);
                            worker.pending.addAll(buf, 9, buf.pos() - 9);
                            worker.lock.set(0);
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

    private class Worker extends AEClient.Worker {
        int order;
        String remoteIp;
        ByteList pending;
        AtomicInteger lock;
        volatile boolean alive;

        @Override
        public String toString() {
            return "Worker{" + "id=" + order + ", ip='" + remoteIp + '\'' + '}';
        }

        Worker(int order, String remoteIp, WrappedSocket client) {
            super(client);
            this.order = order;
            this.remoteIp = remoteIp;
            this.lock = new AtomicInteger();
            this.pending = new ByteList();
            this.alive = true;
        }

        @Override
        void run1() throws IOException {
            while (!shutdownRequested) {
                int read = client.read();
                if(read < -1) {
                    alive = false;
                    break;
                } else if (read > 0) {
                    lastHeart = System.currentTimeMillis();
                    while (client.read() > 0);
                } else {
                    LockSupport.parkNanos(1000);
                }
                while (!lock.compareAndSet(0, -1)) {
                    LockSupport.parkNanos(1000);
                }
                if(pending.pos() > 0) {
                    while (pending.writePos() < pending.pos()) {
                        int w = client.write(pending);
                        if(w < 0)
                            break;
                        else if (w == 0)
                            LockSupport.parkNanos(1000);
                    }
                    pending.clear();
                }
                lock.set(0);
            }
            while (!client.shutdown());
            client.close();
        }
    }
}