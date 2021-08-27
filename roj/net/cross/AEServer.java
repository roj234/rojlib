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

import roj.collect.SimpleList;
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
import java.util.concurrent.locks.LockSupport;

/**
 * Your description here
 *
 * @author Roj233
 * @version 0.1
 * @since 2021/8/17 22:17
 */
public class AEServer extends TCPServer {
    public static final String[] STATE_NAMES = {"WAIT", "CONNECTED", "ESTABLISHED", "ERROR", "DISCONNECT", "FINALIZED"};
    public static final int WAIT        = 0;
    public static final int CONNECTED   = 1;
    public static final int ESTABLISHED = 2;
    public static final int ERROR       = 3;
    public static final int DISCONNECT  = 4;
    public static final int FINALIZED   = 5;
    public static final int SHUTDOWN    = 6;

    public static final int TIMEOUT_CONNECT     = 30000;
    public static final int TIMEOUT_ESTABLISHED = 30000;
    public static final int TIMEOUT_TRANSFER    = 30000;

    public static final int PS_HEARTBEAT = 1;
    public static final int PS_CONNECT = 2;
    public static final int PS_DISCONNECT = 3;
    public static final int PS_DATA = 4;
    public static final int PS_SERVER_DATA = 5;
    public static final int PS_ERROR = 6;
    public static final int PS_TIMEOUT = 7;
    public static final int PS_STATE = 8;
    public static final int PS_STATE_SLAVE = 9;
    public static final int PS_SERVER_SLAVE_DATA = 10;
    public static final int PS_LOGIN_STATE = 11;
    public static final int PS_SERVER_HALLO = 233;

    public static final int PS_ERROR_IO = 0;
    public static final int PS_ERROR_AUTH = 1;
    public static final int PS_ERROR_CONNECTED = 2;
    public static final int PS_ERROR_NOT_CONNECT = 3;
    public static final int PS_ERROR_UNKNOWN_PACKET = 4;
    public static final int PS_ERROR_SHUTDOWN = 5;

    public static final int PS_STATE_OK = 0;
    public static final int PS_STATE_TIMEOUT = 1;
    public static final int PS_STATE_IO_ERROR = 2;
    public static final int PS_STATE_DISCARD = 3;

    public static synchronized void syncPrint(String msg) {
        System.out.println(msg);
    }

    ConcurrentHashMap<Worker, Object> workers = new ConcurrentHashMap<>();
    ConcurrentHashMap<String, Room> rooms = new ConcurrentHashMap<>();
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

    @Override
    protected ITask getTaskFor(Socket client) throws IOException {
        if(workers.size() >= maxConn) {
            try {
                client.getOutputStream().write(255);
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
            if(workers.isEmpty()) {
                Room r = new Room();
                r.master = cio;
                rooms.put("Test", r);
            }
            workers.put(new Worker(this, cio), 0);
        }
        return null;
    }

    @Override
    protected TaskHandler getTaskHandler() {
        return watcher;
    }

    protected boolean handleConnect(Worker address, String id, String token) {
        Room room = rooms.get(id);
        if(room == null) {
            rooms.put(id, room = new Room());
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
        watcher.owner.interrupt();
        LockSupport.parkNanos(1000);
        watcher.owner.interrupt();
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
                            w.wait(10);
                        } catch (InterruptedException ignored) {}
                        w.channel.write(tmp);
                        tmp.writePos(0);
                        for (int i = 0; i < 50; i++) {
                            if (w.channel.shutdown())
                                break;
                            LockSupport.parkNanos(1000);
                        }
                        w.channel.close();
                    } catch (IOException ignored) {}
            }
        }
        workers.clear();
        watcher = null;
    }

    static class Room {
        Socket room;
        String token;
        WrappedSocket master;
        final List<WrappedSocket> slaves = new ArrayList<>();

        public boolean tryConnect(AEServer server, Worker address, String token) {
            /*if(!token.equals(this.token))
                return false;*/
            if(master == null) {
                master = address.channel;
                System.out.println(address + ": 主机");
            } else {
                synchronized (slaves) {
                    slaves.add(address.channel);
                }
                System.out.println(address + ": 玩家");
            }
            address.room = this;
            return true;
        }
    }

    static class Worker extends Thread {
        AEServer server;
        WrappedSocket channel;
        Room room;
        volatile int state;
        long       creation;
        ByteList[] buffers;
        List<WrappedSocket> slavesCopy;

        @Override
        public String toString() {
            return "[" + channel.socket().getRemoteSocketAddress() + "/" + STATE_NAMES[state] + "] => " + room;
        }

        public Worker(AEServer aeServer, WrappedSocket channel) {
            this.server = aeServer;
            this.channel = channel;
            this.creation = System.currentTimeMillis();
            this.state = WAIT;
            this.buffers = new ByteList[0];
            this.slavesCopy = new SimpleList<>();
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

                    int read;
                    while ((read = channel.read()) == 0) {
                        LockSupport.parkNanos(1000);
                        if(wait-- <= 0) {
                            timeout(channel);
                            break x;
                        }
                    }

                    //state = WAIT;

                    while (!channel.buffer().startsWith(CLIENTHALLO)) {
                        read += channel.read();
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

                    int except_length = -1;
                    main:
                    while (true) {
                        while (channel.read() == 0 || buf.pos() < except_length) {
                            LockSupport.parkNanos(1000);
                            if(state == SHUTDOWN)
                                return;
                            if(wait-- <= 0) {
                                timeout(channel);
                                break x;
                            }
                        }
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
                                    if(buf.pos() < 3) {
                                        except_length = 3;
                                        break;
                                    }
                                    int value = r.readUByte() + r.readUByte();
                                    if(buf.pos() + 3 < value) {
                                        except_length = value + 3;
                                        break;
                                    }
                                    except_length = -1;
                                    r.index = 1;
                                    int idLen = r.readUByte();
                                    String id = r.readUTF0(idLen);
                                    int tokenLen = r.readUByte();
                                    String token = r.readUTF0(tokenLen);
                                    buf.clear();
                                    if (!server.handleConnect(this, id, token)) {
                                        syncPrint(this + ": failed to connect");
                                        buf.add((byte) PS_ERROR);
                                        buf.add((byte) PS_ERROR_AUTH);
                                        this.channel.write(buf);
                                        this.channel.dataFlush();
                                        break main;
                                    }
                                    buf.clear();
                                    buf.add((byte) PS_LOGIN_STATE);
                                    buf.add((byte) (room.master == channel ? 1 : 0));
                                    this.channel.write(buf);
                                    this.channel.dataFlush();
                                    buf.clear();
                                    syncPrint(this + ": connected");
                                    state = ESTABLISHED;
                                    wait = TIMEOUT_ESTABLISHED;
                                } else {
                                    buf.clear();
                                    buf.add((byte) PS_ERROR);
                                    buf.add((byte) PS_ERROR_CONNECTED);
                                    this.channel.write(buf);
                                    this.channel.dataFlush();
                                }
                                break;
                            case PS_DISCONNECT:
                                buf.clear();
                                syncPrint(this + ": Request disconnect");
                                w.writeByte((byte) PS_DISCONNECT).writeVString("GOODBYE");
                                this.channel.write(buf);
                                this.channel.dataFlush();
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
                                    if (buf.pos() + 5 < value) {
                                        except_length = value + 5;
                                        continue;
                                    }
                                    except_length = -1;
                                    if (room.master == this.channel) {
                                        if (room.slaves.isEmpty()) {
                                            System.out.println(this + ": 没有接受者: " + buf);
                                            buf.clear();
                                            buf.add((byte) PS_STATE);
                                            buf.add((byte) PS_STATE_DISCARD);
                                            this.channel.write(buf);
                                            this.channel.dataFlush();
                                            buf.clear();
                                            break;
                                        }

                                        List<WrappedSocket> slaves;
                                        synchronized (room.slaves) {
                                            (slaves = this.slavesCopy).addAll(room.slaves);
                                        }
                                        int pos = buf.pos();
                                        buf.pos(0);
                                        w.writeByte((byte) PS_SERVER_DATA).writeInt(value);
                                        buf.pos(pos);
                                        ByteList[] sbs = this.buffers;
                                        if (sbs.length < slaves.size()) {
                                            sbs = new ByteList[slaves.size()];
                                            System.arraycopy(this.buffers, 0, sbs, 0, this.buffers.length);
                                            for (int i = this.buffers.length; i < sbs.length; i++) {
                                                sbs[i] = new ByteList();
                                            }
                                            this.buffers = sbs;
                                        }
                                        for (ByteList bl : sbs) {
                                            bl.setValue(buf.list).pos(pos);
                                        }

                                        ByteList buf2 = new ByteList(10);
                                        buf2.add((byte) PS_STATE);
                                        buf2.add((byte) 0);
                                        buf2.add((byte) 0);
                                        wait = TIMEOUT_TRANSFER * slaves.size();
                                        boolean wrote = true;
                                        x2:
                                        while (wrote) {
                                            wrote = false;
                                            for (int i = 0; i < slaves.size(); i++) {
                                                if (state == SHUTDOWN) return;

                                                WrappedSocket ss = slaves.get(i);
                                                if (ss == null) continue;

                                                ByteList buf1 = sbs[i];
                                                if (buf1.writePos() < buf1.pos()) {
                                                    System.out.println(this + ": => " + ss.socket().getRemoteSocketAddress());
                                                    int wr;
                                                    try {
                                                        wr = ss.write(buf1);
                                                    } catch (Throwable e) {
                                                        buf2.add((byte) i);
                                                        buf2.add((byte) PS_STATE_IO_ERROR);
                                                        slaves.set(i, null);
                                                        break;
                                                    }
                                                    if (wr > 0) {
                                                        wrote = true;
                                                    } else if (wr < -1) {
                                                        buf2.add((byte) i);
                                                        buf2.add((byte) PS_STATE_IO_ERROR);
                                                        slaves.set(i, null);
                                                    }
                                                }

                                                if (wrote) {
                                                    LockSupport.parkNanos(1000);
                                                    if (state == SHUTDOWN) return;
                                                    if (wait-- <= 0) {
                                                        buf2.add((byte) -1);
                                                        buf2.add((byte) PS_STATE_TIMEOUT);
                                                        break x2;
                                                    }
                                                }
                                            }
                                        }

                                        buf.clear();
                                        int len = buf2.pos();
                                        buf2.set(1, (byte) (len >> 8));
                                        buf2.set(2, (byte) len);
                                        this.channel.write(buf2);
                                        this.channel.dataFlush();
                                        wait = TIMEOUT_ESTABLISHED;
                                        this.slavesCopy.clear();
                                    } else {
                                        byte[] ip = channel.socket().getInetAddress().getAddress();
                                        w.writeBytes(ip).writeShort(channel.socket().getPort()).writeByte((byte) ip.length);
                                        int pos = buf.pos();
                                        buf.pos(0);
                                        w.writeByte((byte) PS_SERVER_SLAVE_DATA).writeInt(value + ip.length + 2);
                                        buf.pos(pos);

                                        ByteList buf2 = new ByteList(5);
                                        buf2.add((byte) PS_STATE_SLAVE);
                                        wait = TIMEOUT_TRANSFER;

                                        WrappedSocket ss = room.master;
                                        System.out.println(this + ": => " + ss.socket().getRemoteSocketAddress());
                                        while (state != SHUTDOWN) {
                                            int wr;
                                            try {
                                                wr = ss.write(buf);
                                            } catch (Throwable e) {
                                                buf2.add((byte) PS_STATE_IO_ERROR);
                                                break;
                                            }
                                            if (wr > 0) {
                                                continue;
                                            } else if (wr < -1) {
                                                buf2.add((byte) PS_STATE_IO_ERROR);
                                            }
                                            if(buf.writePos() < buf.pos()) {
                                                LockSupport.parkNanos(1000);
                                                if (wait-- <= 0) {
                                                    buf2.add((byte) PS_STATE_TIMEOUT);
                                                    break;
                                                }
                                            } else {
                                                break;
                                            }
                                        }

                                        buf.clear();
                                        this.channel.write(buf2);
                                        this.channel.dataFlush();
                                        wait = TIMEOUT_ESTABLISHED;
                                    }
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

                System.out.println(this + ": disconnect");
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
                synchronized (room.slaves) {
                    room.slaves.remove(channel);
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
