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
package roj.net.cross.server;

import roj.collect.IntMap;
import roj.concurrent.task.ITaskNaCl;
import roj.config.data.CMapping;
import roj.io.NIOUtil;
import roj.net.SecureUtil;
import roj.net.misc.PacketBuffer;
import roj.net.misc.Pipe;
import roj.net.misc.Shutdownable;
import roj.net.misc.TaskManager;
import roj.net.mss.MSSServerEngineFactory;
import roj.net.mss.PreSharedPubKey;
import roj.net.tcp.MSSSocket;
import roj.net.tcp.PlainSocket;
import roj.net.tcp.WrappedSocket;
import roj.util.FastLocalThread;

import javax.net.ssl.KeyManager;
import javax.net.ssl.X509KeyManager;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Function;

import static roj.net.cross.Util.*;

/**
 * AbyssalEye Server
 *
 * @author Roj233
 * @version 0.1
 * @since 2021/8/17 22:17
 */
public class AEServer implements Runnable, Shutdownable {
    byte[] info = "欢迎使用AbyssalEyeServer Version35".getBytes(StandardCharsets.UTF_8);

    ConcurrentHashMap<String, Room>   rooms   = new ConcurrentHashMap<>();

    int pipeId;
    ConcurrentHashMap<Integer, PipeGroup> pipes = new ConcurrentHashMap<>();
    protected final ServerSocket socket;
    MSSServerEngineFactory factory;

    AtomicInteger connected = new AtomicInteger();
    int maxConn, maxConnPerIp = 128;
    static final Function<InetAddress, AtomicInteger> COUNTER = (x) -> new AtomicInteger();

    TaskManager watcher = new TaskManager();

    public AEServer(InetSocketAddress address, int maxConnection, String keyStore, char[] pass) throws
            IOException, GeneralSecurityException {

        KeyManager[] kmf = SecureUtil.makeKeyManagers(new FileInputStream(keyStore), pass);

        X509Certificate pubKey = null;
        PrivateKey privateKey = null;
        for (KeyManager manager : kmf) {
            if (manager instanceof X509KeyManager) {
                X509KeyManager km = (X509KeyManager) manager;
                String alias = km.chooseServerAlias("RSA", null, null);
                privateKey = km.getPrivateKey(alias);
                pubKey = km.getCertificateChain(alias)[0];
                break;
            }
        }
        if (pubKey == null || privateKey == null)
            throw new NoSuchAlgorithmException("One or more critical parameter to construct the MSS engine is missing");

        PreSharedPubKey pubKeyFormat = new PreSharedPubKey(pubKey);
        this.factory = new MSSServerEngineFactory(pubKeyFormat, pubKey, privateKey);
        this.socket = socket(address, maxConnection);
        this.maxConn = maxConnection;
    }

    public AEServer(InetSocketAddress address, int maxConnection) throws IOException {
        this.socket = socket(address, maxConnection);
        this.maxConn = maxConnection;
    }

    private static ServerSocket socket(InetSocketAddress addr, int conn) throws IOException {
        ServerSocket s = new ServerSocket();
        s.setReuseAddress(true);
        s.bind(addr, conn);
        return s;
    }

    @Override
    public void run() {
        while (true) {
            try {
                Socket c = socket.accept();
                if(connected.incrementAndGet() >= maxConn) {
                    connected.getAndDecrement();
                    c.close();
                } else {
                    FileDescriptor fd = NIOUtil.fd(c);
                    initSocketPref(c);

                    WrappedSocket cio;
                    if (factory != null) {
                        cio = new MSSSocket(c, fd, factory.newEngine());
                    } else {
                        cio = new PlainSocket(c, fd);
                    }
                    watcher.pushTask(new Worker(this, cio));
                }
            } catch (IOException | GeneralSecurityException e) {
                if(e.getMessage().contains("close")) return;
                e.printStackTrace();
            }
        }
    }

    public boolean canCreateRoom = true, canJoinRoom = true;

    public void m_KickRoom(Room room) throws IOException {
        if(room.master == null) return;
        rooms.remove(room.id);
        room.close();
        try (WrappedSocket m = room.master.ch) {
            room.master = null;
            write1(m, (byte) PS_ERROR_SYSTEM_LIMIT);
            while (!m.shutdown()) {
                LockSupport.parkNanos(100);
            }
        }
    }

    protected int createRoom(Worker worker, boolean owner, String id, String token) {
        Room room = rooms.get(id);
        if(owner != (room == null)) return PS_ERROR_AUTH;
        if(room == null) {
            if(!canCreateRoom)
                return PS_ERROR_SYSTEM_LIMIT;
            rooms.put(id, new Room(id, worker, token));
            return -1;
        }
        if(!canJoinRoom || room.locked) {
            return PS_ERROR_SYSTEM_LIMIT;
        }
        return room.preLogin(worker, token);
    }

    @Override
    public boolean wasShutdown() {
        return shutdown;
    }

    public void shutdown() {
        if(watcher == null) return;
        try {
            socket.close();
        } catch (IOException ignored) {}
        shutdown = true;

        for (Room room : rooms.values()) {
            room.token = null;

            Worker master = room.master;
            synchronized (room.clients) {
                for (Worker w : room.clients.values()) {
                    w.self.interrupt();
                    if (w.ch == null) continue;
                    try {
                        write1(w.ch, (byte) PS_ERROR_SHUTDOWN);
                    } catch (IOException ignored) {}
                }
            }
            if (master != null) {
                master.self.interrupt();
                room.master = null;
            }
            room.close();
        }

        watcher.waitUntilFinish();
        watcher = null;

        System.out.println("服务器关闭");
    }

    public static final class Room {
        Worker master;
        String id, token;

        String motdString;
        byte[] motd, portMap;

        final IntMap<Worker> clients = new IntMap<>();
        int index;

        // 配置项
        public boolean locked;

        // 统计数据
        long creation;

        public Room(String id, Worker owner, String token) {
            this.master = owner;
            this.id = id;
            this.token = token;
            this.clients.put(0, owner);
            this.index = 1;
            this.creation = System.currentTimeMillis() / 1000;
            owner.room = this;
        }

        public int preLogin(Worker worker, String token) {
            if(!token.equals(this.token))
                return PS_ERROR_AUTH;
            synchronized (clients) {
                clients.put(worker.clientId = index++, worker);
            }
            worker.room = this;
            return -1;
        }

        public void close() {
            token = null;
            synchronized (clients) {
                clients.clear();
            }
        }

        public void kick(int id) {
            synchronized (clients) {
                clients.remove(id);
            }
        }

        public CMapping serialize() {
            long up = 0, down = 0;
            synchronized (master.selfPipes) {
                if (!master.selfPipes.isEmpty()) {
                    for (PipeGroup group : master.selfPipes.values()) {
                        Pipe ref = group.pairRef;
                        if (ref != null) {
                            up += ref.getnBytesToH();
                            down += ref.getnBytesToC();
                        }
                    }
                }
            }

            CMapping json = new CMapping();
            json.put("id", id);
            json.put("pass", token);
            json.put("time", creation);
            json.put("up", up);
            json.put("down", down);
            json.put("users", clients.size());
            json.put("index", index);
            json.put("motd", motdString);
            json.put("master", master == null ? "断线" : master.ch.socket().getRemoteSocketAddress().toString());
            return json;
        }

        public void hostInit(Worker w, byte[] motd, byte[] port) {
            this.motd = motd;
            motdString = new String(motd, StandardCharsets.UTF_8);
            this.portMap = port;
        }
    }

    boolean shutdown;

    static final class Worker extends FastLocalThread implements ITaskNaCl {
        AEServer      server;
        WrappedSocket ch;

        Room   room;
        int    clientId;
        Thread self;

        // 统计数据 (可以删除)
        long creation, lastHeart;

        private Stated state;

        @Override
        public String toString() {
            return "[" + ch.socket().getRemoteSocketAddress() + "/" + state.getClass().getSimpleName() + "] #" + clientId;
        }

        public Worker(AEServer aeServer, WrappedSocket ch) {
            this.server = aeServer;
            this.ch = ch;
            this.creation = System.currentTimeMillis() / 1000;
            this.state = Handshake.HANDSHAKE;
            this.self = this;
            setDaemon(true);
            setName("SW " + ch.socket().getRemoteSocketAddress());
        }

        @Override
        public void run() {
            catcher:
            try {
                while (state != null && !server.shutdown) {
                    state = state.next(this);
                    if (state == PipeLogin.PIPE_OK) break catcher;
                }

                state = Closed.CLOSED;

                while (!ch.shutdown()) {
                    LockSupport.parkNanos(100);
                }
                ch.close();
            } catch (Throwable e) {
                syncPrint(this + ": 异常 " + e.getMessage());
                if (e.getClass() != IOException.class)
                    e.printStackTrace();

                try {
                    write1(ch, (byte) PS_ERROR_IO);
                    ch.shutdown();
                } catch (IOException ignored) {}

                try {
                    ch.close();
                } catch (IOException ignored) {}
            }

            if(room != null) {
                if(room.master == this) {
                    syncPrint(this + ": 解散 " + room.id);
                    room.master = null;
                    server.rooms.remove(room.id);
                    room.close();
                } else if (clientId > 0) {
                    synchronized (room.clients) {
                        room.clients.remove(clientId);
                    }
                    Worker mw = room.master;
                    if (mw != null) {
                        ByteBuffer x = ByteBuffer.allocate(5)
                                                 .put((byte) PH_CLIENT_LOGOUT)
                                                 .putInt(clientId);
                        x.flip();
                        mw.sync(x);
                    }
                }
            }

            ch = null;
            server.connected.getAndDecrement();

            synchronized (this) {
                notify();
            }
        }

        public CMapping serialize() {
            CMapping json = new CMapping();
            json.put("id", clientId);
            json.put("ip", ch.socket().getRemoteSocketAddress().toString());
            json.put("state", state.getClass().getSimpleName());
            json.put("time", creation);
            json.put("heart", lastHeart / 1000);
            return json;
        }

        @Override
        public void calculate(Thread thread) {
            self = thread;
            self.setName("SW " + ch.socket().getRemoteSocketAddress());
            run();
            self.setName("SW Idle");
        }

        @Override
        public boolean isDone() {
            return ch == null;
        }

        public void pollPackets() throws IOException {
            byte[] data = packets.poll();
            if (data != null) {
                ByteBuffer src = ByteBuffer.wrap(data);
                for (;;) {
                    ch.write(src);
                    if (src.hasRemaining()) LockSupport.parkNanos(50);
                    else break;
                }
            }

            if (!selfPipes.isEmpty()) {
                for (Iterator<PipeGroup> itr = selfPipes.values().iterator(); itr.hasNext(); ) {
                    PipeGroup group = itr.next();
                    if (group.life < 0 || (group.pairRef != null && group.pairRef.isReleased())) {
                        itr.remove();
                    }
                }
            }
        }

        private final PacketBuffer packets = new PacketBuffer();
        public void sync(ByteBuffer rb) {
            packets.offer(rb);
        }

        private final IntMap<PipeGroup> selfPipes = new IntMap<>();
        private       PipeGroup         pending;
        public String generatePipe(int[] tmp) {
            if (pending != null) return "管道 #" + pending.id + " 处于等待状态";
            if (clientId == 0) return "只有客户端才能请求管道";
            /*if (server.pipes.isEmpty()) server.pipeId = 0;
            else */if (server.pipes.size() > 30) return "服务器等待打开的管道过多";
            if (selfPipes.size() > 10) return "你打开的管道过多";

            PipeGroup group = pending = new PipeGroup();
            group.downOwner = this;
            group.id = server.pipeId++;
            group.upPass = (int) (new Object().hashCode() ^ hashCode() ^ System.currentTimeMillis()) + 1;
            group.downPass = (int) (new Object().hashCode() ^ hashCode() ^ System.currentTimeMillis()) + 2;
            group.life = 120;

            tmp[0] = group.id;
            tmp[1] = group.upPass;

            selfPipes.put(group.id, group);
            synchronized (room.master.selfPipes) {
                room.master.selfPipes.put(group.id, group);
            }
            server.pipes.put(group.id, group);

            return null;
        }

        public long getPendingPipeId() {
            return ((long)pending.id << 32) | pending.downPass;
        }

        public Worker closePipe(int pipeId) {
            PipeGroup group = selfPipes.remove(pipeId);
            if (group == null) {
                syncPrint(this + ": PCC 无效的管道: " + pipeId);
                return null;
            }
            if (group.pairRef != null) {
                try {
                    group.pairRef.release();
                } catch (IOException ignored) {}
            }
            group.life = -1;
            return clientId != 0 ? room.master : group.downOwner;
        }

        public PipeGroup getPipe(int pipeId) {
            return selfPipes.get(pipeId);
        }

        public void pendingPipeOpen() {
            pending = null;
        }
    }

    static final class PipeGroup {
        int life;
        int id, upPass, downPass;
        FileDescriptor upConnFD, downConnFD;
        Worker downOwner;
        Pipe pairRef;
    }
}