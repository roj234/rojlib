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
import roj.net.NetworkUtil;
import roj.net.tcp.util.InsecureSocket;
import roj.net.tcp.util.SecureSocket;
import roj.net.tcp.util.WrappedSocket;
import roj.util.ByteList;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Iterator;
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
        this.ob = new ByteList();
    }

    IntMap<Worker> channelById;
    ByteList ob;

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

    void cb_onClientJoin(Worker w) {
        w.state = 1;
    }

    void cb_onClientExit(Worker w) {
        w.state = 2;
    }

    void cb_onClientHang(Worker w) {
        w.state = 3;
    }

    void cb_onClientRejoin(Worker w) {
        w.state = 4;
    }

    private void run1() throws IOException {
        Socket remote = new Socket();
        remote.connect(server);
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

                w.writeByte((byte) PS_CONNECT).writeByte((byte) ByteWriter.byteCountUTF8(id)).writeByte((byte) ByteWriter.byteCountUTF8(token)).writeByte((byte) 1).writeAllUTF(id).writeAllUTF(token);
                if(writeAndFlush(channel, buf, TIMEOUT_TRANSFER) < 0) {
                    syncPrint(this + ": 连接数据包发送超时");
                    break conn;
                }
                buf.clear();

                int heart = 0;
                int except = -1;
                Worker wk;
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
                        if(shutdownRequested || read < 0) break conn;

                        ByteList ob = this.ob;
                        for (Iterator<Worker> itr = channelById.values().iterator(); itr.hasNext(); ) {
                            wk = itr.next();
                            // client读取锁
                            if (!wk.lock.compareAndSet(ST_AVAILABLE, ST_CHANNEL_OP)) continue;
                            try {
                                ByteList in = wk.client.buffer();
                                if (in.pos() > 0) {
                                    ob.clear();
                                    ob.add((byte) PS_DATA);
                                    w.list = ob;
                                    w.writeInt(in.pos() + 4).writeBytes(in).writeInt(wk.slaveId).list = buf;
                                    in.clear();
                                    writeAndFlush(channel, ob, TIMEOUT_TRANSFER);
                                }
                                if (!wk.alive) {
                                    ob.clear();
                                    ob.add((byte) PS_KICK_SLAVE);
                                    w.list = ob;
                                    w.writeInt(wk.slaveId).list = buf;
                                    writeAndFlush(channel, ob, 100);
                                    syncPrint("分机断开(cleanup) #" + wk.slaveId);
                                    cb_onClientExit(wk);
                                    itr.remove();
                                }
                            } finally {
                                wk.lock.set(ST_AVAILABLE);
                            }
                        }
                        LockSupport.parkNanos(20);
                    }
                    if (read < 0 || shutdownRequested) {
                        break;
                    }
                    if(buf.pos() < 1) {
                        continue;
                    }
                    switch (buf.get(0) & 0xFF) {
                        case PS_HEARTBEAT:
                            //lastHeart = System.currentTimeMillis();
                            buf.clear();
                            break;
                        case PS_LOGON:
                            if(buf.pos() < 5) {
                                except = 5;
                                break;
                            }
                            except = -1;
                            syncPrint(this + ": 登录成功");
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
                        case PS_RESET:
                            if(buf.pos() < 5) {
                                except = 5;
                                break;
                            }
                            except = -1;
                            r.index = 1;
                            wk = channelById.get(r.readInt());
                            if(wk != null && wk.alive) {
                                if(wk.lock.get() == ST_HANGUP) {
                                    syncPrint("分机 #" + wk.slaveId + " 已经挂起");
                                }
                                int i = 0;
                                while (!wk.lock.compareAndSet(ST_AVAILABLE, ST_REQUEST_HANGUP) && i++ < 5)
                                    LockSupport.parkNanos(10);
                                while (wk.lock.get() != ST_HANGUP && i++ < 5)
                                    LockSupport.parkNanos(10);
                                if(wk.lock.get() != ST_HANGUP) {
                                    // kill
                                    r.index = 1;
                                    channelById.remove(r.readInt());
                                    buf.set(0, (byte) PS_KICK_SLAVE);
                                    writeAndFlush(channel, buf, 200);
                                    syncPrint("错误" + wk.lock.get() + " " + wk.alive + " #" + wk.slaveId);
                                }

                                wk.client.dataFlush();
                                while (!wk.client.shutdown()) {
                                    LockSupport.parkNanos(10);
                                }
                                wk.client.close();
                                cb_onClientHang(wk);
                            } else {
                                if(wk != null)
                                    syncPrint("分机已断开 #" + wk.slaveId);
                            }
                            buf.clear();
                            break;
                        case PS_SLAVE_DISCONNECT:
                            if(buf.pos() < 5) {
                                except = 5;
                                break;
                            }
                            except = -1;
                            r.index = 1;
                            int id = r.readInt();
                            wk = channelById.remove(id);
                            if(wk != null && wk.alive) {
                                syncPrint("分机断开 #" + wk.slaveId);
                                wk.alive = false;
                                wk.client.dataFlush();
                                while (!wk.client.shutdown()) {
                                    LockSupport.parkNanos(100);
                                }
                                wk.client.close();
                                if(wk.lock.get() == ST_HANGUP)
                                    LockSupport.unpark(wk);
                                cb_onClientExit(wk);
                            } else {
                                syncPrint("分机已断开(cl) #" + id);
                            }
                            buf.clear();
                            break;
                        case PS_SLAVE_CONNECT:
                            if(buf.pos() < 8) { // PKT IDX IDX IDX IDX PRT PRT ADR [ADR]
                                except = 8;
                                break;
                            }
                            r.index = 1;
                            int index = r.readInt();
                            int port = r.readUnsignedShort();
                            int addrLen = r.readUByte();
                            if(buf.pos() < addrLen + 8) {
                                except = addrLen + 8;
                                break;
                            }
                            except = -1;
                            if(channelById.containsKey(index)) {
                                syncPrint("分机已连接 #" + index + " ???");
                                buf.clear();
                                break;
                            }
                            Socket soc = new Socket();
                            soc.connect(localServer);
                            initSocketPref(soc);
                            wk = new Worker(index, NetworkUtil.bytes2ip(r.readBytes(addrLen)) + ':' + port, new InsecureSocket(soc, NonblockingUtil.fd(soc)));
                            workers.add(wk);
                            cb_onClientJoin(wk);
                            channelById.put(index, wk);
                            task.pushTask(wk);
                            syncPrint("分机连接 #" + index + ", " + wk.remoteIp);
                            buf.clear();
                            break;
                        case PS_SERVER_SLAVE_DATA:
                            if(buf.pos() < 9) { // PKT FRM FRM FRM FRM LEN LEN LEN LEN [len]
                                except = 9;
                                break;
                            }
                            r.index = 1;
                            int from = r.readInt();
                            int value = r.readInt();
                            if(buf.pos() < value + 9) {
                                except = value + 9;
                                break;
                            }
                            except = -1;

                            Worker worker = channelById.get(from);
                            if(worker == null) {
                                syncPrint("未知分机 #" + from);
                                buf.clear();
                                break;
                            }
                            int i = 0;
                            while (i++ < 20) {
                                int v = worker.lock.get();
                                if (v == ST_HANGUP) {
                                    worker.lock.set(ST_CHANNEL_OP);
                                    Socket soc2 = new Socket();
                                    soc2.connect(localServer);
                                    initSocketPref(soc2);
                                    worker.client = new InsecureSocket(soc2, NonblockingUtil.fd(soc2));
                                    LockSupport.unpark(worker);
                                    cb_onClientRejoin(worker);
                                    i = -1;
                                    break;
                                } else if (worker.lock.compareAndSet(ST_AVAILABLE, ST_CHANNEL_OP)) {
                                    i = -1;
                                    break;
                                }
                                LockSupport.parkNanos(10);
                            }
                            if (i != -1) {
                                System.out.println("内部错误! 忽略发来的数据" + worker.lock.get());
                                worker.alive = false;
                                buf.clear();
                                break;
                            }
                            worker.ob.addAll(buf, 9, buf.pos() - 9);
                            worker.lock.set(ST_AVAILABLE);
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
        } catch (Throwable e) {
            onError(channel, e);
        }
    }

    static final int ST_AVAILABLE      = 0;
    static final int ST_REQUEST_HANGUP = 1;
    static final int ST_CHANNEL_OP     = 2;
    static final int ST_HANGUP         = 3;

    final class Worker extends AEClient.Worker {
        AtomicInteger lock;
        volatile boolean alive;

        // 统计信息, 可选
        byte state;
        long connectTime;
        String remoteIp;
        long upstream, downstream;

        public void serialize(CList lx) {
            CMapping self = new CMapping();
            self.put("id", slaveId);
            self.put("ip", remoteIp);
            self.put("time", connectTime);
            self.put("up", upstream);
            self.put("down", downstream);
            self.put("state", state);
            lx.add(self);
        }

        @Override
        public String toString() {
            return remoteIp + " #" + slaveId;
        }

        Worker(int slaveId, String remoteIp, WrappedSocket client) {
            super(client);
            this.slaveId = slaveId;
            this.remoteIp = remoteIp;
            this.lock = new AtomicInteger();
            this.alive = true;
            this.connectTime = System.currentTimeMillis();
        }

        @Override
        void run1() throws IOException {
            while (!shutdownRequested && alive) {
                int read = client.read();
                if(read < -1) {
                    alive = false;
                    break;
                } else if (read > 0) {
                    lastHeart = System.currentTimeMillis();
                    downstream += read;
                }
                switch (lock.get()) {
                    case ST_AVAILABLE:
                        if(!lock.compareAndSet(ST_AVAILABLE, ST_CHANNEL_OP)) break;
                        ByteList ob = this.ob;
                        try {
                            if (ob.writePos() < ob.pos()) {
                                int w = client.write(ob);
                                if (w < 0) {
                                    alive = false;
                                    break;
                                }
                                upstream += w;
                            }
                            if (ob.writePos() == ob.pos()) ob.clear();
                        } finally {
                            lock.set(ST_AVAILABLE);
                        }
                        break;
                    case ST_REQUEST_HANGUP:
                        if(lock.compareAndSet(ST_REQUEST_HANGUP, ST_HANGUP))
                            LockSupport.park();
                        break;
                }
                LockSupport.parkNanos(20);
            }
            while (!client.shutdown()) {
                LockSupport.parkNanos(100);
            }
            client.close();
        }
    }
}