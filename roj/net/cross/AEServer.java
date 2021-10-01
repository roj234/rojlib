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
import roj.collect.IntSet;
import roj.concurrent.TaskExecutor;
import roj.concurrent.TaskHandler;
import roj.concurrent.TaskPool;
import roj.concurrent.task.ITask;
import roj.concurrent.task.ITaskNaCl;
import roj.config.data.CMapping;
import roj.io.NonblockingUtil;
import roj.net.tcp.TCPServer;
import roj.net.tcp.util.InsecureSocket;
import roj.net.tcp.util.SecureSocket;
import roj.net.tcp.util.WrappedSocket;
import roj.util.ByteList;
import roj.util.ByteReader;
import roj.util.ByteWriter;
import roj.util.FastLocalThread;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.LockSupport;

import static roj.net.cross.Util.*;

/**
 * AbyssalEye Server
 *
 * @author Roj233
 * @version 0.1
 * @since 2021/8/17 22:17
 */
public class AEServer extends TCPServer {
    ConcurrentHashMap<Worker, Object> workers = new ConcurrentHashMap<>();
    ConcurrentHashMap<String, Room> rooms = new ConcurrentHashMap<>();

    int maxConn;
    Watcher watcher = new Watcher();

    public AEServer(InetSocketAddress address, int maxConnection, String keyStoreFile, char[] keyPassword) throws
            IOException, GeneralSecurityException {
        super(address, maxConnection, keyStoreFile, keyPassword);
        this.maxConn = maxConnection;
    }

    public AEServer(InetSocketAddress address, int maxConnection) throws IOException {
        super(address, maxConnection);
        this.maxConn = maxConnection;
    }

    public boolean canCreateRoom = true, canJoinRoom = true;

    public void m_KickRoom(Room room) throws IOException {
        if(room.master == null) return;
        while (!room.compareAndSet(0, 2)) {
            LockSupport.parkNanos(10);
            if(room.master == null || watcher == null) return;
        }
        try {
            writeEx(room.master, (byte) PS_ERROR_SYSTEM_LIMIT);
            while (!room.master.shutdown()) {
                LockSupport.parkNanos(1000);
            }
            room.master.close();
            room.master = null;
            rooms.remove(room.id);
        } finally {
            room.set(0);
        }
    }

    @Override
    protected ITask getTaskFor(Socket client) throws IOException {
        if(workers.size() >= maxConn) {
            try {
                client.getOutputStream().write(PS_LINK_OVERFLOW);
                client.getOutputStream().flush();
            } finally {
                client.close();
            }
            return null;
        } else {
            FileDescriptor fd = NonblockingUtil.fd(client);
            initSocketPref(client);

            WrappedSocket cio = (
                    ssl != null ?
                            SecureSocket.get(client, fd, ssl, false) :
                            new InsecureSocket(client, fd)
            );
            Worker w = new Worker(this, cio);
            workers.put(w, 0);
            return w;
        }
    }

    @Override
    protected TaskHandler getTaskHandler() {
        return watcher;
    }

    protected int handleConnect(Worker address, boolean owner, String id, String token) {
        Room room = rooms.get(id);
        if(owner != (room == null)) return PS_ERROR_AUTH;
        if(room == null) {
            if(!canCreateRoom)
                return PS_ERROR_SYSTEM_LIMIT;
            rooms.put(id, new Room(id, address, token));
            return -1;
        }
        if(!canJoinRoom || room.locked) {
            return PS_ERROR_SYSTEM_LIMIT;
        }
        return room.tryConnect(this, address, token);
    }

    public void shutdown() {
        if(watcher == null) return;
        watcher.shutdown();
        try {
            socket.close();
        } catch (IOException ignored) {}
        LockSupport.parkNanos(1000);
        for (Room room : rooms.values()) {
            room.master = null;
        }
        rooms.clear();
        for (Worker w : workers.keySet()) {
            int state = w.state;
            w.state = SHUTDOWN;
            LockSupport.unpark(w);
            switch (state) {
                case CONNECTED:
                case ESTABLISHED:
                    try {
                        LockSupport.parkNanos(100);
                        if(w.channel == null) break;
                        writeEx(w.channel, (byte) PS_ERROR_SHUTDOWN);
                        for (int i = 0; i < 20; i++) {
                            if (w.channel.shutdown())
                                break;
                            LockSupport.parkNanos(20);
                        }
                        w.channel.close();
                    } catch (IOException ignored) {}
            }
            w.interrupt();
        }
        for (Worker w : workers.keySet()) {
            try {
                w.join(50);
            } catch (InterruptedException ignored) {}
        }
        workers.clear();
        watcher.waitUntilFinish();
        watcher = null;
        System.out.println("服务器已关闭");
    }

    public static final class Room extends AtomicInteger {
        String id, token;
        WrappedSocket master;
        int index;

        final IntMap<Worker> slaves = new IntMap<>();
        List<Worker> packets = new ArrayList<>();
        IntSet kicked = new IntSet(4);

        // 配置项
        public boolean locked;

        // 统计数据
        LongAdder up, down;
        long creation;

        public Room(String id, Worker owner, String token) {
            this.master = owner.channel;
            this.id = id;
            this.token = token;
            this.index = 1;
            this.creation = System.currentTimeMillis() / 1000;
            this.up = new LongAdder();
            this.down = new LongAdder();
            owner.room = this;
        }

        public int tryConnect(AEServer server, Worker address, String token) {
            if(!token.equals(this.token))
                return PS_ERROR_AUTH;
            synchronized (slaves) {
                slaves.put(address.roomIndex = index++, address);
            }
            address.room = this;
            return -1;
        }

        public void mainThread() {
            if(packets.size() > 0) {
                int trys = 10;
                while (!compareAndSet(0, -1) && trys-- > 0) {
                    LockSupport.parkNanos(10);
                }
                if(trys == 0) return;

                for (int i = 0; i < packets.size(); i++) {
                    WrappedSocket channel = packets.get(i).channel;
                    try {
                        writeAndFlush(master, channel.buffer(), 200);
                        LockSupport.unpark(packets.get(i));
                    } catch (Throwable e) {
                        e.printStackTrace();
                        System.out.println("Error at #" + i);
                    }
                }
                packets.clear();
                set(0);
            }
        }

        public void wakeupAndDiscard() {
            if(packets.size() > 0) {
                while (!compareAndSet(0, -1)) {
                    LockSupport.parkNanos(10);
                }

                for (int i = 0; i < packets.size(); i++) {
                    LockSupport.unpark(packets.get(i));
                }
                packets.clear();
                set(0);
            }
        }

        public void register(Worker worker) {
            while (!compareAndSet(0, 1)) {
                LockSupport.parkNanos(10);
                if(worker.state == SHUTDOWN || master == null) return;
            }
            packets.add(worker);
            set(0);
            LockSupport.park();
        }

        public void kick(int id) {
            while (!compareAndSet(0, 1)) {
                LockSupport.parkNanos(10);
                if(master == null) return;
            }
            kicked.add(id);
            set(0);
        }

        public void resetStats() {
            up.reset();
            down.reset();
            synchronized (slaves) {
                for(Worker w : slaves.values()) {
                    w.up = w.down = 0;
                }
            }
        }

        public CMapping serialize() {
            CMapping json = new CMapping();
            json.put("id", id);
            json.put("pass", token);
            json.put("time", creation);
            json.put("up", up.sum());
            json.put("down", down.sum());
            json.put("users", slaves.size());
            json.put("index", index);
            json.put("master", master == null ? "断线" : master.socket().getRemoteSocketAddress().toString());
            return json;
        }
    }

    static final class Worker extends FastLocalThread implements ITaskNaCl {
        AEServer server;
        WrappedSocket channel;

        Room room;
        int roomIndex;
        AtomicInteger busy;

        volatile int state;

        // 统计数据 (可以删除)
        long creation, lastHeart;
        long up, down;

        @Override
        public String toString() {
            return "[" + channel.socket().getRemoteSocketAddress() + "/" + STATE_NAMES[state] + "] #" + roomIndex;
        }

        public Worker(AEServer aeServer, WrappedSocket channel) {
            this.server = aeServer;
            this.channel = channel;
            this.creation = System.currentTimeMillis() / 1000;
            this.state = WAIT;
            this.busy = new AtomicInteger(3);
            setDaemon(true);
            setName("Worker " + channel.socket().getRemoteSocketAddress());
        }

        @Override
        public void run() {
            try {
                conn:
                {
                    int heart = handshake();
                    if (heart != 0) {
                        syncPrint(this + ": 握手失败: " + heart);
                        break conn;
                    }
                    if(state == SHUTDOWN) break conn;

                    ByteList buf = channel.buffer();
                    buf.clear();
                    state = CONNECTED;

                    ByteReader r = new ByteReader(buf);
                    ByteWriter w = new ByteWriter(buf);

                    heart = T_SERVER_HEARTBEAT_INIT;
                    int except = -1;
                    while (state != SHUTDOWN) {
                        if (chkRoomState()) break;
                        int read;
                        while ((read = channel.read(except == -1 ? 1 : except - buf.pos())) == 0 || buf.pos() < except) {
                            if (chkRoomState()) break conn;
                            checkBusy(true);
                            LockSupport.parkNanos(20);
                            checkBusy(false);
                            if (heart-- <= 0) {
                                syncPrint(this + ": 心跳超时");
                                if(T_SERVER_HEARTBEAT) {
                                    writeEx(channel, (byte) PS_ERROR_TIMEOUT);
                                    break conn;
                                } else {
                                    heart = T_SERVER_HEARTBEAT_RECV;
                                }
                            }
                            if (state == SHUTDOWN) break conn;
                        }
                        if (read < 0 || state == SHUTDOWN) break;
                        switch (buf.get(0) & 0xFF) {
                            case PS_HEARTBEAT:
                                lastHeart = System.currentTimeMillis();
                                writeEx(channel, (byte) PS_HEARTBEAT);
                                buf.clear();
                                break;
                            case PS_CONNECT:
                                if (state == CONNECTED) {
                                    if (buf.pos() < 4) { // PKT VL VL XL
                                        except = 4;
                                        break;
                                    }
                                    r.index = 1;
                                    int value = r.readUByte() + r.readUByte();
                                    if (buf.pos() < value + 4) {
                                        except = value + 4;
                                        break;
                                    }
                                    r.index = 1;
                                    except = -1;

                                    int idLen = r.readUByte();
                                    int tokenLen = r.readUByte();

                                    int code = server.handleConnect(this, r.readBoolean(), r.readUTF0(idLen), r.readUTF0(tokenLen));
                                    if (code != -1) {
                                        syncPrint(this + ": 连接失败(协议): " + ERROR_NAMES[code - 0x20]);
                                        writeEx(channel, (byte) code);
                                        break conn;
                                    }
                                    buf.clear();
                                    buf.add((byte) PS_LOGON);
                                    writeAndFlush(channel, w.writeInt(roomIndex).list, 500);
                                    buf.clear();
                                    if (room.master != channel) {
                                        buf.add((byte) PS_SLAVE_CONNECT);
                                        byte[] addr = channel.socket().getInetAddress().getAddress();
                                        int port = channel.socket().getPort();
                                        w.writeInt(roomIndex).writeShort(port).writeByte((byte) addr.length).writeBytes(addr);
                                        room.register(this);
                                        buf.clear();
                                    }
                                    if(state == SHUTDOWN) break conn;
                                    state = ESTABLISHED;
                                } else {
                                    writeEx(channel, (byte) PS_ERROR_CONNECTED);
                                }
                                break;
                            case PS_DISCONNECT:
                                buf.clear();
                                syncPrint(this + ": 断开连接(协议)");
                                state = DISCONNECT;
                                break conn;
                            case PS_RESET:
                                if (state != ESTABLISHED) {
                                    writeEx(channel, (byte) PS_ERROR_NOT_CONNECT);
                                    break;
                                }
                                if (room.master != this.channel) {
                                    w.writeInt(this.roomIndex);
                                    room.register(this);
                                }
                                buf.clear();
                                break;
                            case PS_KICK_SLAVE:
                                if (buf.pos() < 5) {
                                    except = 5;
                                    break;
                                }
                                except = -1;
                                if (state != ESTABLISHED) {
                                    writeEx(channel, (byte) PS_ERROR_NOT_CONNECT);
                                    break;
                                }
                                if (room.master == this.channel) {
                                    r.index = 1;
                                    room.kick(r.readInt());
                                }
                                buf.clear();
                                break;
                            case PS_DATA:
                                if (state != ESTABLISHED) {
                                    writeEx(channel, (byte) PS_ERROR_NOT_CONNECT);
                                    break;
                                }

                                r.index = 1;
                                if (buf.pos() < 5) {
                                    except = 5;
                                    break;
                                }
                                int value = r.readInt();
                                if (buf.pos() < value + 5) {
                                    except = value + 5;
                                    break;
                                }
                                except = -1;

                                Worker wk = null;
                                AtomicInteger targetLock;
                                WrappedSocket target;
                                buf.writePos(0);
                                if (room.master == this.channel) {
                                    // from ClientOwner: PKT LEN LEN LEN [len - 4] TO TO TO TO
                                    r.index = buf.pos() - 4;
                                    synchronized (room.slaves) {
                                        wk = room.slaves.get(r.readInt());
                                    }
                                    if (wk == null || (target = wk.channel) == null) {
                                        r.index = buf.pos() - 4;
                                        syncPrint(this + ": 没有接受者 " + r.readInt());
                                        buf.clear();
                                        buf.add((byte) PS_STATE);
                                        buf.add((byte) PS_STATE_DISCARD);
                                        writeAndFlush(channel, buf, 200);
                                        buf.clear();
                                        break;
                                    }
                                    targetLock = wk.busy;

                                    int pos = buf.pos() - 4;
                                    buf.pos(0);
                                    w.writeByte((byte) PS_SERVER_DATA).writeInt(value - 4);
                                    buf.pos(pos);
                                    //syncPrint(this + " => '" + room.id + "'[" + id + "]");
                                    // 拿到写入锁
                                    if (!acquireWriteLock(targetLock)) break conn;
                                    // 等待线程停车
                                    while (targetLock.get() != 1) {
                                        LockSupport.parkNanos(10);
                                        if(state == SHUTDOWN ||
                                                room.master == null ||
                                                room.kicked.remove(roomIndex)) break conn;
                                    }
                                    room.down.add(value - 4);
                                    wk.down += value - 4;
                                } else {
                                    // from Client: PKT LEN LEN LEN LEN [len]
                                    int pos = buf.pos();
                                    byte[] x = buf.list;
                                    if(x.length < buf.pos() + 4) {
                                        x = new byte[buf.pos() + 4];
                                    }
                                    System.arraycopy(buf.list, 1, buf.list = x, 5, pos - 1);
                                    buf.pos(0);
                                    w.writeByte((byte) PS_SERVER_SLAVE_DATA).writeInt(roomIndex);
                                    buf.pos(pos + 4);

                                    target = room.master;
                                    targetLock = room;
                                    //syncPrint(this + " => '" + room.id + "'[0]");
                                    // 拿到写入锁
                                    if (!acquireWriteLock(targetLock)) break conn;
                                    room.up.add(value);
                                    up += value;
                                }
                                int v = writeAndFlush(target, buf, TIMEOUT_TRANSFER);
                                buf.clear();
                                if (v == -7) {
                                    buf.add((byte) PS_STATE);
                                    buf.add((byte) PS_STATE_TIMEOUT);
                                } else if (v < 0) {
                                    buf.add((byte) PS_STATE);
                                    buf.add((byte) PS_STATE_IO_ERROR);
                                }

                                if (buf.pos() > 0) writeAndFlush(channel, buf, 200);

                                if(room.master == channel) {
                                    LockSupport.unpark(wk);
                                } else {
                                    targetLock.set(0);
                                }
                                break;
                            default:
                                int bc = buf.getU(0) - 0x20;
                                if(buf.pos() == 1 && bc >= 0 && bc < ERROR_NAMES.length) {
                                    syncPrint(this + ": 错误 " + ERROR_NAMES[bc]);
                                } else {
                                    syncPrint(this + ": 未知数据包: " + buf);
                                    writeEx(channel, (byte) PS_ERROR_UNKNOWN_PACKET);
                                }
                                break conn;
                        }
                        heart = T_SERVER_HEARTBEAT_RECV;
                    }
                }

                // wait for last (writeEx) message to send
                try {
                    writeEx(channel, (byte) PS_DISCONNECT);
                } catch (IOException ignored) {}
                LockSupport.parkNanos(2000);
                syncPrint(this + ": 断开");
                while (!channel.shutdown()) {
                    LockSupport.parkNanos(100);
                }
                channel.close();
                state = FINALIZED;
            } catch (Throwable e) {
                state = ERROR;

                try {
                    writeEx(channel, (byte) PS_ERROR_IO);
                } catch (Throwable ignored) {}

                String msg = e.getMessage();

                if (!"Broken pipe".equals(msg) && !"Connection reset by peer".equals(msg)) {
                    syncPrint(this + ": Error: " + e);
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
            if(room != null) {
                if(room.master == channel) {
                    room.master = null;
                    server.rooms.remove(room.id);
                    room.wakeupAndDiscard();
                } else {
                    synchronized (room.slaves) {
                        room.slaves.remove(roomIndex);
                    }
                    ByteList buf = channel.buffer();
                    buf.clear();
                    new ByteWriter(buf).writeByte((byte) PS_SLAVE_DISCONNECT).writeInt(roomIndex);
                    room.register(this);
                }
            }
            channel = null;
            server.workers.remove(this);

            synchronized (this) {
                notify();
            }
        }

        private boolean acquireWriteLock(AtomicInteger targetLock) {
            while (!targetLock.compareAndSet(0, 2)) {
                if(room.master == channel) room.mainThread();
                LockSupport.parkNanos(10);
                if(state == SHUTDOWN ||
                        room.master == null ||
                        room.kicked.remove(roomIndex)) return false;
            }
            return true;
        }

        private void checkBusy(boolean state) {
            if(state) {
                if(!busy.compareAndSet(3, 0))
                    throw new IllegalStateException(String.valueOf(busy.get()));
            } else {
                int v;
                do {
                    v = busy.get();
                    if(v != 0 && v != 2)
                        throw new IllegalStateException(String.valueOf(busy.get()));
                    if (busy.compareAndSet(2, 1)) {
                        LockSupport.park();
                        if (!busy.compareAndSet(1, 3))
                            throw new IllegalStateException(String.valueOf(busy.get()));
                        break;
                    } else if (busy.compareAndSet(0, 3))
                        break;
                    LockSupport.parkNanos(10);
                } while (true);
            }
        }

        private int handshake() throws IOException {
            int wait = TIMEOUT_CONNECT;
            while (!channel.handShake()) {
                LockSupport.parkNanos(50);
                if(wait-- <= 0) {
                    return 1;
                }
            }

            int rem = CLIENT_HALLO.list.length + 1;
            while (rem > 0) {
                int r = channel.read(rem);
                if(r < 0)
                    return 2;
                rem -= r;
                LockSupport.parkNanos(100);
                if(state == SHUTDOWN)
                    return 3;
                if(wait-- <= 0) {
                    writeEx(channel, (byte) PS_ERROR_TIMEOUT);
                    return 4;
                }
            }
            if(!channel.buffer().startsWith(CLIENT_HALLO))
                return 5;
            if(channel.buffer().getU(CLIENT_HALLO.list.length) != PROTOCOL_VERSION)
                return 6;

            return writeEx(channel, (byte) PS_SERVER_HALLO);
        }

        private boolean chkRoomState() throws IOException {
            if (room != null) {
                if (room.master == null || room.kicked.remove(roomIndex)) {
                    writeEx(channel, (byte) PS_ERROR_MASTER_DIE);
                    return true;
                } else if (room.master == channel) room.mainThread();
            }
            return false;
        }

        public CMapping serialize() {
            CMapping json = new CMapping();
            json.put("id", roomIndex);
            json.put("ip", channel.socket().getRemoteSocketAddress().toString());
            json.put("state", STATE_NAMES[state]);
            json.put("time", creation);
            json.put("heart", lastHeart);
            json.put("up", up);
            json.put("down", down);
            return json;
        }

        @Override
        public void calculate(Thread thread) {
            run();
        }

        @Override
        public boolean isDone() {
            return channel == null;
        }
    }

    static final class Watcher extends TaskPool {
        public Watcher() {
            super(1, Integer.parseInt(System.getProperty("ae.server.threads", "10")), 1, 1, pool -> new TaskExecutor(pool, "Worker", 120000));
        }

        @Override
        public void pushTask(ITask task) {
            if(task == null) return;
            super.pushTask(task);
        }

        @Override
        protected void onReject(ITask task, int minPending) {
            Worker w = (Worker) task;
            w.start();
        }
    }
}