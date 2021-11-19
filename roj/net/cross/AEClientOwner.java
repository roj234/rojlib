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
import roj.io.IOUtil;
import roj.io.NonblockingUtil;
import roj.net.NetworkUtil;
import roj.net.tcp.util.InsecureSocket;
import roj.net.tcp.util.SecureSocket;
import roj.net.tcp.util.WrappedSocket;
import roj.util.ByteList;
import roj.util.ByteWriter;
import roj.util.DirectByteBufferAsList;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
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
        this.ob = ByteBuffer.allocateDirect(5);
    }

    final IntMap<Worker> channelById;
    final ByteBuffer ob;

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
                ByteBuffer rb = channel.buffer();
                ByteBuffer wb = rb.duplicate();

                wb.clear();
                wb.put((byte) PS_CONNECT).put((byte) ByteWriter.byteCountUTF8(id))
                  .put((byte) ByteWriter.byteCountUTF8(token)).put((byte) 1);
                ByteList u8b = new DirectByteBufferAsList(wb);
                ByteWriter.writeUTF(u8b, id, -1);
                ByteWriter.writeUTF(u8b, token, -1);
                wb.flip();
                if(writeAndFlush(channel, wb, TIMEOUT_TRANSFER) < 0) {
                    syncPrint(this + ": 连接数据包发送超时");
                    break conn;
                }

                int heart = 0;
                int except = -1;
                Worker wk;
                while (!shutdownRequested) {
                    while ((read = channel.read(except == -1 ? 1 : except - rb.position())) == 0 || rb.position() < except) {
                        if(--heart % T_CLIENT_HEARTBEAT_RETRY == 0) {
                            if (write1(channel, (byte) PS_HEARTBEAT) < 0) {
                                syncPrint(this + ": 心跳发送失败");
                            }
                        } else if(heart < -T_CLIENT_HEARTBEAT_TIMEOUT) {
                            syncPrint(this + ": 没收到服务端心跳");
                            break conn;
                        }
                        if(shutdownRequested || read < 0) break conn;

                        ByteBuffer ob = this.ob;
                        for (Iterator<Worker> itr = channelById.values().iterator(); itr.hasNext(); ) {
                            wk = itr.next();
                            // client读取锁
                            if (!wk.alive) {
                                ob.clear();
                                ob.put((byte) PS_KICK_SLAVE)
                                  .putInt(wk.slaveId).flip();
                                writeAndFlush(channel, ob, 100);
                                syncPrint("分机断开(cleanup) #" + wk.slaveId);
                                itr.remove();
                                continue;
                            }
                            if (!wk.isWaiting()) continue;
                            try {
                                ByteBuffer in = wk.client.buffer();
                                if (in.position() > 0) {
                                    System.out.println("IN " + in);
                                    ob.clear();
                                    ob.put((byte) PS_DATA)
                                      .putInt(in.flip().limit() + 4).flip();
                                    int t = writeAndFlush(channel, ob, TIMEOUT_TRANSFER);

                                    // todo : multi-port(at most 255) support











                                    t = writeAndFlush(channel, in, t);
                                    in.clear();

                                    ob.position(0).limit(4);
                                    ob.putInt(0, wk.slaveId);
                                    writeAndFlush(channel, ob, t);
                                }
                            } finally {
                                wk.swapOrThrow(ST_WRITE, ST_RUN);
                            }
                        }
                        LockSupport.parkNanos(20);
                    }
                    if (read < 0 || shutdownRequested) {
                        break;
                    }
                    if(rb.position() < 1) {
                        continue;
                    }
                    switch (rb.get(0) & 0xFF) {
                        case PS_HEARTBEAT:
                            //lastHeart = System.currentTimeMillis();
                            rb.clear();
                            break;
                        case PS_LOGON:
                            if(rb.position() < 5) {
                                except = 5;
                                break;
                            }
                            except = -1;
                            syncPrint(this + ": 登录成功");
                            rb.clear();
                            break;
                        case PS_DISCONNECT:
                            syncPrint(this + ": 断开连接(协议)");
                            break conn;
                        case PS_STATE:
                            if(rb.position() < 2) {
                                except = 2;
                                break;
                            }
                            except = -1;
                            syncPrint(this + ": 上次的转发状态: " +
                                              RSTATE_NAMES[rb.get(1) & 0xFF]);
                            rb.clear();
                            break;
                        case PS_RESET:
                            if(rb.position() < 5) {
                                except = 5;
                                break;
                            }
                            except = -1;
                            wk = channelById.get(rb.getInt(1));
                            if(wk != null) {
                                boolean changed = wk.swapOrThrow(ST_RUN, ST_IDLE);
                                if(!changed) {
                                    syncPrint("分机 #" + wk.slaveId + " 已经挂起");
                                }
                                /*int i = 0;
                                while (!wk.lock.compareAndSet(ST_WORKING, ST_REQUEST_HANGUP) && i++ < 5)
                                    LockSupport.parkNanos(10);
                                while (wk.lock.get() != ST_IDLE && i++ < 5)
                                    LockSupport.parkNanos(10);
                                if(wk.lock.get() != ST_IDLE) {
                                    // kill
                                    channelById.remove(rb.getInt(1));
                                    wb.position(0).limit(5);
                                    wb.put(0, (byte) PS_KICK_SLAVE);
                                    writeAndFlush(channel, wb, 200);
                                    syncPrint("错误" + wk.lock.get() + " " + wk.alive + " #" + wk.slaveId);
                                }*/

                                if (wk.client != null) {
                                    finishClientConnection(wk);
                                }
                            } else {
                                if(wk != null)
                                    syncPrint("分机已断开 #" + wk.slaveId);
                            }
                            rb.clear();
                            break;
                        case PS_SLAVE_DISCONNECT:
                            if(rb.position() < 5) {
                                except = 5;
                                break;
                            }
                            except = -1;
                            int id = rb.getInt(1);
                            wk = channelById.remove(id);
                            if(wk != null/* && wk.alive*/) {
                                syncPrint("分机断开 #" + wk.slaveId);
                                wk.swapOrThrow(ST_RUN | ST_IDLE, ST_STOP);
                                if (wk.client != null) {
                                    finishClientConnection(wk);
                                }
                            } else {
                                syncPrint("分机已断开(cl) #" + id);
                            }
                            rb.clear();
                            break;
                        case PS_SLAVE_CONNECT:
                            if(rb.position() < 8) { // PKT IDX IDX IDX IDX PRT PRT ADR [ADR]
                                except = 8;
                                break;
                            }
                            int addrLen = rb.get(7) & 0xFF;
                            if(rb.position() < addrLen + 8) {
                                except = addrLen + 8;
                                break;
                            }
                            except = -1;

                            int index = rb.getInt(1);
                            if(channelById.containsKey(index)) {
                                syncPrint("分机已连接 #" + index + " !");
                                rb.clear();
                                break;
                            }

                            Socket soc = new Socket();
                            soc.connect(localServer);
                            initSocketPref(soc);

                            if (!IOUtil.directBufferEquals(wb, rb)) {
                                wb = rb.duplicate();
                            }
                            wb.limit(rb.position()).position(8);
                            byte[] addr = new byte[addrLen];
                            wb.get(addr);

                            int port = rb.getChar(5);
                            wk = new Worker(index,
                                 NetworkUtil.bytes2ip(addr) + ':' + port,
                                 new InsecureSocket(soc, NonblockingUtil.fd(soc)));

                            workers.add(wk);
                            channelById.put(index, wk);
                            task.pushTask(wk);

                            syncPrint("分机连接 #" + index + ", " + wk.remoteIp);
                            rb.clear();
                            break;
                        case PS_SERVER_SLAVE_DATA:
                            if(rb.position() < 9) { // PKT FRM FRM FRM FRM LEN LEN LEN LEN [len]
                                except = 9;
                                break;
                            }
                            int len = rb.getInt(5);
                            if(rb.position() < len + 9) {
                                except = len + 9;
                                break;
                            }
                            except = -1;

                            int from = rb.getInt(1);
                            wk = channelById.get(from);
                            if(wk == null) {
                                syncPrint("未知分机 #" + from);
                                rb.clear();
                                break;
                            }
                            wk.swapOrThrow(ST_RUN | ST_IDLE, ST_WRITE);

                            Socket soc2 = new Socket();
                            soc2.connect(localServer);
                            initSocketPref(soc2);
                            wk.client = new InsecureSocket(soc2, NonblockingUtil.fd(soc2));

                            ByteBuffer ob = wk.ob.compact();
                            if (ob.remaining() < rb.position() - 9) {
                                ByteBuffer newOB = ByteBuffer.allocateDirect(ob.position() + rb.position() - 9);
                                ob.flip();
                                newOB.put(ob);
                                IOUtil.clean(ob);
                                wk.ob = newOB;
                            }

                            if (!IOUtil.directBufferEquals(wb, rb)) {
                                wb = rb.duplicate();
                            }
                            wb.position(9).limit(rb.position());
                            wk.ob.put(wb).flip();

                            wk.swapOrThrow(ST_WRITE, ST_RUN);
                            rb.clear();
                            break;
                        default:
                            int bc = (rb.get(0) & 0xFF) - 0x20;
                            if(rb.position() == 1 && bc >= 0 && bc < ERROR_NAMES.length) {
                                syncPrint(this + ": 错误 " + ERROR_NAMES[bc]);
                            } else {
                                syncPrint(this + ": 未知数据包: " + dumpBuffer(rb));
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
                write1(channel, (byte) PS_DISCONNECT);
            } catch (IOException ignored) {}
            while (!channel.shutdown()) {
                LockSupport.parkNanos(100);
            }
            channel.close();
        } catch (Throwable e) {
            onError(channel, e);
        }
    }

    private static void finishClientConnection(Worker wk) throws IOException {
        wk.client.dataFlush();
        int t = 1000;
        while (!wk.client.shutdown() && t-- > 0) {
            LockSupport.parkNanos(100);
        }
        wk.client.close();
        wk.client = null;
    }

    static final int ST_STOP  = -1;
    static final int ST_RUN   = 1;
    static final int ST_IDLE  = 2;
    static final int ST_WRITE = 4;

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
            this.ob = ByteBuffer.allocateDirect(0);
        }

        @Override
        void run1() throws IOException {
            System.out.println("Run");
            lock.set(ST_RUN);
            try {
                while (!shutdownRequested && alive) {
                    // read
                    int read = client.read();
                    if (read < 0) {
                        break;
                    } else if (read > 0) {
                        lastHeart = System.currentTimeMillis();
                        downstream += read;
                    }

                    // write
                    ByteBuffer ob = this.ob;
                    if (ob.hasRemaining()) {
                        int w = client.writeDirect(ob);
                        if (w < 0) {
                            break;
                        } else if (w > 0) {
                            upstream += w;
                            if (!ob.hasRemaining()) ob.clear();
                        }
                    }

                    // check IDLE request
                    System.out.println("CHECK IDLE");
                    if (lock.compareAndSet(ST_IDLE_PENDING, ST_IDLE)) {
                        System.out.println("CHECK IDLE OK");
                        LockSupport.park();
                        continue;
                    }

                    // swap to WRITE_PENDING state
                    System.out.println("CHECK WRITE_PENDING");
                    if (lock.compareAndSet(ST_RUN, ST_WRITE)) {
                        LockSupport.parkNanos(20);
                        if (!lock.compareAndSet(ST_WRITE, ST_RUN)) {
                            System.out.println("CHECK WRITE_PENDING OK " + lock.get());
                            LockSupport.park();
                        }
                    }
                }
            } finally {
                alive = false;
                lock.set(ST_STOP);
                finishClientConnection(this);
            }
        }

        static final int ST_IDLE_PENDING = 5;

        public boolean swapOrThrow(int from, int to) throws IOException {
            int time = 1000;
            switch (from) {
                case ST_RUN | ST_IDLE: // to stop / write
                    if (to == ST_WRITE) {
                        if (lock.get() == ST_IDLE)
                            return false;
                        while (!lock.compareAndSet(ST_RUN, ST_IDLE_PENDING)) {
                            if (lock.compareAndSet(ST_WRITE, 233))
                                return true;
                            if (time-- < 0) {
                                alive = false;
                                throw new IOException("Lock timeout" + lock.get());
                            }
                            LockSupport.parkNanos(20);
                        }
                        while (lock.get() != ST_IDLE) {
                            if (time-- < 0) {
                                alive = false;
                                throw new IOException("Lock timeout" + lock.get());
                            }
                            LockSupport.parkNanos(20);
                        }
                    } else {
                        assert to == ST_STOP;
                        if (lock.get() == ST_IDLE)
                            LockSupport.unpark(self);
                        alive = false;
                    }
                    break;
                case ST_WRITE: // to run
                    assert to == ST_RUN;
                    if (!lock.compareAndSet(233, ST_RUN))
                        throw new IOException("Illegal state " + lock.get());

                    LockSupport.unpark(self);
                    break;
                case ST_RUN: // to idle
                    assert to == ST_IDLE;
                    if (lock.get() == ST_IDLE)
                        return false;
                    if (lock.get() != ST_RUN)
                        throw new IOException("Illegal state " + lock.get());
                    while (!lock.compareAndSet(ST_RUN, ST_IDLE_PENDING)) {
                        if (lock.compareAndSet(ST_WRITE, 233))
                            return true;
                        if (time-- < 0) {
                            alive = false;
                            throw new IOException("Lock timeout" + lock.get());
                        }
                        LockSupport.parkNanos(20);
                    }
                    while (lock.get() != ST_IDLE) {
                        if (time-- < 0) {
                            alive = false;
                            throw new IOException("Lock timeout" + lock.get());
                        }
                        LockSupport.parkNanos(20);
                    }
                    break;
                default:
                    throw new IOException("Illegal state transformation");
            }
            return true;
        }

        public boolean isWaiting() {
            return lock.compareAndSet(ST_WRITE, 233);
        }
    }
}