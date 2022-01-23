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
import roj.io.NIOUtil;
import roj.net.MSSSocket;
import roj.net.misc.Shutdownable;
import roj.net.misc.TaskManager;
import roj.net.mss.MSSServerEngineFactory;
import roj.util.EmptyArrays;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import static roj.net.cross.Util.*;

/**
 * AbyssalEye Server
 *
 * @author Roj233
 * @since 2021/8/17 22:17
 */
public class AEServer implements Runnable, Shutdownable {
    static AEServer  server;
    // 20分钟
    static final int PIPE_TIMEOUT = 1200_000;

    byte[] info = EmptyArrays.BYTES;

    public void setMOTD(String motd) {
        info = motd.getBytes(StandardCharsets.UTF_8);
    }

    ConcurrentHashMap<String, Room> rooms = new ConcurrentHashMap<>();

    int pipeId;
    ConcurrentHashMap<Integer, PipeGroup> pipes = new ConcurrentHashMap<>();
    Random rnd;

    protected final ServerSocket socket;
    private final MSSServerEngineFactory factory;

    AtomicInteger remain;

    TaskManager watcher = new TaskManager();

    public AEServer(InetSocketAddress addr, int conn, MSSServerEngineFactory factory) throws IOException {
        ServerSocket s = this.socket = new ServerSocket();
        s.setReuseAddress(true);
        s.bind(addr, conn);
        this.remain = new AtomicInteger(conn);
        this.factory = factory;
        this.rnd = new SecureRandom();
    }

    @Override
    public void run() {
        server = this;
        while (true) {
            Socket c;
            try {
                c = socket.accept();
            } catch (IOException e) {
                break;
            }
            try {
                if(remain.decrementAndGet() <= 0) {
                    remain.getAndIncrement();
                    c.close();
                } else {
                    if (rooms.isEmpty()) pipeId = 0;

                    FileDescriptor fd = NIOUtil.fd(c);
                    initSocketPref(c);
                    watcher.pushTask(new Client(new MSSSocket(c, fd, factory.newEngine())));
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    public boolean canCreateRoom = true, canJoinRoom = true;

    protected int login(Client worker, boolean owner, String id, String token) {
        Room room = rooms.get(id);
        if(owner != (room == null)) return PS_ERROR_AUTH;
        if(room == null) {
            if(!canCreateRoom) return PS_ERROR_SYSTEM_LIMIT;
            rooms.put(id, new Room(id, worker, token));
            return -1;
        }
        if(!canJoinRoom || room.locked) return PS_ERROR_SYSTEM_LIMIT;
        if(!token.equals(room.token)) return PS_ERROR_AUTH;
        IntMap<Client> clients = room.clients;
        synchronized (clients) {
            if (clients.size() == 1) room.index = 1;
            clients.put(worker.clientId = room.index++, worker);
        }
        worker.room = room;
        return -1;
    }

    @Override
    public boolean wasShutdown() {
        return shutdown;
    }

    public void shutdown() {
        if(shutdown) return;
        try {
            socket.close();
        } catch (IOException ignored) {}

        for (Room room : rooms.values()) {
            room.token = null;
            room.master = null;
            synchronized (room.clients) {
                for (Client w : room.clients.values()) {
                    w.self.interrupt();
                    LockSupport.unpark(w.self);
                    if (w.ch == null) continue;
                    try {
                        write1(w.ch, (byte) PS_ERROR_SHUTDOWN);
                    } catch (IOException ignored) {}
                }
            }
            room.close();
        }

        LockSupport.parkNanos(1000_000);
        shutdown = true;

        watcher.waitUntilFinish();
        watcher = null;

        System.out.println("服务器关闭");
    }

    boolean shutdown;

    long lastCheckTime;
    void pipeTimeoutHandler() {
        long time = System.currentTimeMillis();
        if (time - lastCheckTime < 1000) return;
        lastCheckTime = time;
        // noinspection all
        for (Iterator<PipeGroup> itr = pipes.values().iterator(); itr.hasNext(); ) {
            PipeGroup group = itr.next();
            if (group.timeout > time) {
                itr.remove();
            }
        }
    }
}