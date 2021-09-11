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
import roj.collect.SimpleList;
import roj.concurrent.TaskExecutor;
import roj.concurrent.TaskHandler;
import roj.concurrent.task.ITask;
import roj.io.NonblockingUtil;
import roj.net.tcp.TCPServer;
import roj.net.tcp.client.HttpClient;
import roj.net.tcp.util.InsecureSocket;
import roj.net.tcp.util.SecureSocket;
import roj.net.tcp.util.WrappedSocket;
import roj.util.ByteList;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
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

    AtomicInteger freeThreads = new AtomicInteger();
    TaskExecutor[] runner;

    int maxConn;
    Watcher watcher;

    public AEServer(InetSocketAddress address, int maxConnection, String keyStoreFile, char[] keyPassword) throws
            IOException, GeneralSecurityException {
        super(address, maxConnection, keyStoreFile, keyPassword);
        this.maxConn = maxConnection;
    }

    public AEServer(InetSocketAddress address, int maxConnection) throws IOException {
        super(address, maxConnection);
        this.maxConn = maxConnection;
    }

    @Override
    public void run() {
        watcher = new Watcher(Thread.currentThread(), this);
        super.run();
    }

    public boolean canCreateRoom = true, canJoinRoom = true;

    public void m_KickRoom(Room room) throws IOException {
        while (!room.master.shutdown());
        room.master.close();
        room.master = null;
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
        } else {
            FileDescriptor fd = NonblockingUtil.fd(client);

            WrappedSocket cio = (
                    ssl != null ?
                            SecureSocket.get(client, fd, ssl, false) :
                            new InsecureSocket(client, fd)
            );
            Worker w = new Worker(this, cio);
            workers.put(w, 0);
            //enqueue(w);
        }
        return null;
    }

    @Override
    protected TaskHandler getTaskHandler() {
        return watcher;
    }

    protected int handleConnect(Worker address, String id, String token, boolean owner) {
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

    WrappedSocket getClientChannel(Socket socket) throws IOException {
        return ssl != null ? SecureSocket.get(socket, NonblockingUtil.fd(socket), HttpClient.CLIENT_ALLOCATOR, true) : new InsecureSocket(socket, NonblockingUtil.fd(socket));
    }

    static ByteList CLIENTHALLO = new ByteList(new byte[] {
        'A','E','C','L','I','E','N','T','H','A','L','L','O'
    });

    public void shutdown() {
        if(watcher == null) return;
        try {
            socket.close();
            watcher.join();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        LockSupport.parkNanos(1000);
        for (Map.Entry<Worker, Object> entry : workers.entrySet()) {
            Worker w = entry.getKey();
            ByteList tmp = new ByteList(5);
            tmp.add((byte) PS_ERROR);
            tmp.add((byte) PS_ERROR_SHUTDOWN);
            switch (w.state) {
                case CONNECTED:
                case ESTABLISHED:
                    try {
                        w.state = SHUTDOWN;
                        try {
                            synchronized (w) {
                                w.wait(10);
                            }
                        } catch (InterruptedException ignored) {}
                        w.channel.write(tmp);
                        tmp.writePos(0);
                        for (int i = 0; i < 50; i++) {
                            if (w.channel.shutdown())
                                break;
                            LockSupport.parkNanos(100);
                        }
                        w.channel.close();
                    } catch (IOException ignored) {}
            }
            w.state = SHUTDOWN;
        }
        workers.clear();
        watcher = null;
        syncPrint("Server closed");
    }

    public static final class Room extends AtomicInteger {
        String id, token;
        WrappedSocket master;
        int index;
        final IntMap<WrappedSocket> slaves = new IntMap<>();
        List<Worker> packets = new ArrayList<>();
        public boolean locked;

        public Room(String id, Worker owner, String token) {
            this.master = owner.channel;
            this.id = id;
            this.token = token;
            this.index = 1;
            owner.room = this;
        }

        public int tryConnect(AEServer server, Worker address, String token) {
            if(!token.equals(this.token))
                return PS_ERROR_AUTH;
            synchronized (slaves) {
                slaves.put(address.roomIndex = index++, address.channel);
            }
            address.room = this;
            return -1;
        }

        public void mainThread() {
            if(packets.size() > 0) {
                while (!compareAndSet(0, 1)) {
                    LockSupport.parkNanos(100);
                }
                ByteList bl;
                x:
                for (int i = 0; i < packets.size(); i++) {
                    bl = packets.get(i).channel.buffer();
                    while (bl.writePos() < bl.pos()) {
                        try {
                            master.write(bl);
                        } catch (IOException e) {
                            e.printStackTrace();
                            break x;
                        }
                        LockSupport.parkNanos(100);
                    }
                    LockSupport.unpark(packets.get(i));
                }
                packets.clear();
                set(0);
            }
        }

        public void register(Worker worker) {
            while (!compareAndSet(0, 1)) {
                LockSupport.parkNanos(100);
            }
            packets.add(worker);
            set(0);
            LockSupport.park();
        }
    }

    static class Worker extends Thread {
        AEServer server;
        WrappedSocket channel;

        Room room;
        int roomIndex;

        volatile int state;
        long creation;
        ByteList buffer;

        @Override
        public String toString() {
            return "[" + channel.socket().getRemoteSocketAddress() + "/" + STATE_NAMES[state] + "] #" + roomIndex;
        }

        public Worker(AEServer aeServer, WrappedSocket channel) {
            this.server = aeServer;
            this.channel = channel;
            this.creation = System.currentTimeMillis();
            this.state = WAIT;
            this.buffer = new ByteList();
            setDaemon(true);
            setName("Worker " + channel.socket().getRemoteSocketAddress());
            start();
        }

        @Override
        public void run() {
            try {
                Socket socket = this.channel.socket();
                socket.setSoTimeout(TIMEOUT_CONNECT);

                InetSocketAddress remote = (InetSocketAddress) socket.getRemoteSocketAddress();

                x:
                {
                    int wait = TIMEOUT_CONNECT;
                    while (!channel.handShake()) {
                        LockSupport.parkNanos(1000);
                        if(wait-- <= 0) {
                            timeout(channel);
                            break x;
                        }
                    }

                    while (channel.read() == 0) {
                        LockSupport.parkNanos(1000);
                        if(wait-- <= 0) {
                            timeout(channel);
                            break x;
                        }
                    }

                    while (!channel.buffer().startsWith(CLIENTHALLO)) {
                        if(channel.read() < 0)
                            break x;
                        LockSupport.parkNanos(1000);
                        if(state == SHUTDOWN)
                            return;
                        if(wait-- <= 0) {
                            timeout(channel);
                            break x;
                        }
                    }
                    ByteList buf = channel.buffer();
                    buf.clear();

                    buf.add((byte) PS_SERVER_HALLO);
                    while (channel.write(buf) == 0) {
                        LockSupport.parkNanos(1000);
                        if(wait-- <= 0) {
                            timeout(channel);
                            break x;
                        }
                    }
                    buf.clear();

                    state = CONNECTED;

                    wait = TIMEOUT_ESTABLISHED;
                    ByteReader r = new ByteReader(buf);
                    ByteWriter w = new ByteWriter(buf);

                    int read;
                    int except_length = -1;
                    main:
                    while (true) {
                        if(room != null) {
                            if(room.master == null) {
                                w.writeByte((byte) PS_ERROR).writeByte((byte) PS_ERROR_MASTER_DIE);
                                this.channel.write(buf);
                                this.channel.dataFlush();
                                LockSupport.parkNanos(10 * 1000);
                                break;
                            } else if(room.master == channel)
                                room.mainThread();
                        }
                        while ((read = channel.read(except_length == -1 ? 1 : except_length - buf.pos())) == 0 || buf.pos() < except_length) {
                            if(read < 0)
                                break main;
                            if(read == 0) {
                                if(room != null && room.master == channel)
                                    room.mainThread();
                                LockSupport.parkNanos(1000);
                                if(wait-- <= 0) {
                                    timeout(channel);
                                    break main;
                                }
                            }
                            if(state == SHUTDOWN)
                                return;
                        }
                        if(read < 0)
                            break;
                        if(state == SHUTDOWN)
                            return;
                        switch (buf.get(0) & 0xFF) {
                            case PS_HEARTBEAT:
                                buf.writePos(0);
                                while (channel.write(buf) == 0) {
                                    LockSupport.parkNanos(1000);
                                    if(state == SHUTDOWN)
                                        return;
                                    if(wait-- <= 0) {
                                        timeout(channel);
                                        break x;
                                    }
                                }
                                wait = TIMEOUT_ESTABLISHED;
                                buf.clear();
                                break;
                            case PS_TIMEOUT:
                                state = ERROR;
                                break main;
                            case PS_CONNECT:
                                if(state == CONNECTED) {
                                    if(buf.pos() < 4) { // PKT VL VL XL
                                        except_length = 4;
                                        break;
                                    }
                                    r.index = 1;
                                    int value = r.readUByte() + r.readUByte();
                                    if(buf.pos() < value + 4) {
                                        except_length = value + 4;
                                        break;
                                    }
                                    r.index = 1;
                                    except_length = -1;
                                    int idLen = r.readUByte();
                                    int tokenLen = r.readUByte();
                                    boolean owner = r.readBoolean();
                                    String id = r.readUTF0(idLen);
                                    String token = r.readUTF0(tokenLen);
                                    buf.clear();
                                    int vx = server.handleConnect(this, id, token, owner);
                                    if (vx != -1) {
                                        syncPrint(this + ": 连接失败(协议)");
                                        buf.add((byte) PS_ERROR);
                                        buf.add((byte) vx);
                                        this.channel.write(buf);
                                        this.channel.dataFlush();
                                        LockSupport.parkNanos(10 * 1000);
                                        break main;
                                    }
                                    buf.clear();
                                    buf.add((byte) PS_LOGON);
                                    w.writeInt(roomIndex);
                                    while (buf.writePos() < buf.pos()) {
                                        if(this.channel.write(buf) < 0)
                                            break;
                                    }
                                    this.channel.dataFlush();
                                    buf.clear();
                                    if(room.master != channel) {
                                        buf.add((byte) PS_SLAVE_CONNECT);
                                        byte[] addr = channel.socket().getInetAddress().getAddress();
                                        int port = channel.socket().getPort();
                                        w.writeInt(roomIndex).writeShort(port).writeByte((byte) addr.length).writeBytes(addr);
                                        room.register(this);
                                        buf.clear();
                                    }
                                    state = ESTABLISHED;
                                    wait = TIMEOUT_ESTABLISHED;
                                } else {
                                    buf.clear();
                                    buf.add((byte) PS_ERROR);
                                    buf.add((byte) PS_ERROR_CONNECTED);
                                    this.channel.write(buf);
                                    this.channel.dataFlush();
                                    buf.clear();
                                }
                                break;
                            case PS_DISCONNECT:
                                buf.clear();
                                syncPrint(this + ": 断开连接(协议)");
                                state = DISCONNECT;
                                break main;
                            case PS_DATA:
                                if(state != ESTABLISHED) {
                                    buf.clear();
                                    buf.add((byte) PS_ERROR);
                                    buf.add((byte) PS_ERROR_NOT_CONNECT);
                                    this.channel.write(buf);
                                    this.channel.dataFlush();
                                } else {
                                    r.index = 1;
                                    wait = TIMEOUT_ESTABLISHED;
                                    if (buf.pos() < 5) {
                                        except_length = 5;
                                        continue;
                                    }
                                    int value = r.readInt();
                                    if (buf.pos() < value + 5) {
                                        except_length = value + 5;
                                        continue;
                                    }
                                    except_length = -1;

                                    WrappedSocket slave;
                                    ByteList outBuf = this.buffer;
                                    outBuf.clear();
                                    if (room.master == this.channel) {
                                        // from ClientOwner: PKT LEN LEN LEN [len - 4] TO TO TO TO
                                        r.index = buf.pos() - 4;
                                        int id = r.readInt();
                                        synchronized (room.slaves) {
                                            slave = room.slaves.get(id);
                                        }
                                        if (slave == null) {
                                            syncPrint(this + ": 没有接受者: " + buf);
                                            buf.clear();
                                            buf.add((byte) PS_STATE);
                                            buf.add((byte) PS_STATE_DISCARD);
                                            this.channel.write(buf);
                                            this.channel.dataFlush();
                                            buf.clear();
                                            break;
                                        }

                                        // 转移数据到outBuf
                                        w.list = outBuf;
                                        w.writeByte((byte) PS_SERVER_DATA).writeInt(value).list = buf;
                                        outBuf.addAll(buf.list, 4, buf.pos() - 5);
                                        buf.clear();

                                        buf.add((byte) PS_STATE);
                                        syncPrint(this + " => '" + room.id + "'.slave[" + id + "]");
                                    } else {
                                        // from Client: PKT LEN LEN LEN LEN [len]
                                        w.list = outBuf;
                                        w.writeByte((byte) PS_SERVER_SLAVE_DATA).writeInt(roomIndex)
                                                .list = buf;
                                        outBuf.addAll(buf, 1, buf.pos() - 1);

                                        buf.clear();
                                        buf.add((byte) PS_STATE_SLAVE);

                                        slave = room.master;
                                        syncPrint(this + " => '" + room.id + "'.master");
                                    }
                                    wait = TIMEOUT_TRANSFER;
                                    while (outBuf.writePos() < outBuf.pos()) {
                                        int wr;
                                        try {
                                            wr = slave.write(outBuf);
                                        } catch (Throwable e) {
                                            buf.add((byte) PS_STATE_IO_ERROR);
                                            break;
                                        }
                                        if (wr < -1) {
                                            buf.add((byte) PS_STATE_IO_ERROR);
                                            break;
                                        } else if (wr == 0) {
                                            LockSupport.parkNanos(100);
                                        }
                                        if (state == SHUTDOWN) return;
                                        if (wait-- <= 0) {
                                            buf.add((byte) PS_STATE_TIMEOUT);
                                            break;
                                        }
                                    }

                                    if(buf.pos() == 1)
                                        buf.add((byte) PS_STATE_OK);
                                    this.channel.write(buf);
                                    this.channel.dataFlush();
                                    buf.clear();
                                    wait = TIMEOUT_ESTABLISHED;
                                }
                                break;
                            case PS_ERROR:
                                try {
                                    syncPrint(this + ": ERROR: " + r.readUByte());
                                } catch (ArrayIndexOutOfBoundsException ignored) {
                                    syncPrint(this + ": REPORTED AN EMPTY ERROR ");
                                }
                                break main;
                            default:
                                syncPrint(this + ": UNKNOWN PACKET: " + buf);
                                buf.clear();
                                buf.add((byte) PS_ERROR);
                                buf.add((byte) PS_ERROR_UNKNOWN_PACKET);
                                this.channel.write(buf);
                                this.channel.dataFlush();
                                break main;
                        }
                    }
                }

                syncPrint(this + ": 断开");
                while (!channel.shutdown());
                channel.close();
                state = FINALIZED;
            } catch (Throwable e) {
                state = ERROR;

                try {
                    ByteList error = this.channel.buffer();
                    error.clear();
                    error.add((byte) PS_ERROR);
                    error.add((byte) PS_ERROR_IO);
                    this.channel.write(error);
                    this.channel.dataFlush();
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

        private static void timeout(WrappedSocket channel) {
            ByteList buf = channel.buffer();
            buf.clear();
            buf.add((byte) PS_TIMEOUT);
            try {
                channel.write(buf);
                channel.dataFlush();
            } catch (Throwable ignored) {}
        }
    }

    static class Watcher extends Thread implements TaskHandler {
        Thread owner;
        AEServer server;
        final List<ITask> tasks = new ArrayList<>();
        final List<ITask> tasks2 = new SimpleList<>();

        public Watcher(Thread thread, AEServer server) {
            this.owner = thread;
            this.server = server;
            setDaemon(true);
            setName("Timeout watcher");
            start();
        }

        @Override
        public void run() {
            while (owner.isAlive()) {
                for (Iterator<Map.Entry<Worker, Object>> it = server.workers.entrySet().iterator(); it.hasNext(); ) {
                    Map.Entry<Worker, Object> entry = it.next();
                    Worker w = entry.getKey();
                    switch (w.state) {
                        case WAIT:
                        case ERROR:
                        case FINALIZED:
                            it.remove();
                    }
                }
                tasks2.clear();
                synchronized (tasks) {
                    if(!tasks.isEmpty()) {
                        tasks2.addAll(tasks);
                    }
                    tasks.clear();
                }
                for (int i = 0; i < tasks2.size(); i++) {
                    try {
                        tasks2.get(i).calculate(owner);
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                }
                tasks2.clear();
                LockSupport.parkNanos(1000 * 1000);
            }
        }

        @Override
        public void pushTask(ITask task) {
            if(task != null) {
                synchronized (tasks) {
                    tasks.add(task);
                }
            }
        }

        @Override
        public void clearTasks() {
            synchronized (tasks) {
                tasks.clear();
            }
        }
    }
}
