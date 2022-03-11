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
import roj.concurrent.ImmediateExecutor;
import roj.concurrent.PrefixFactory;
import roj.concurrent.TaskPool;
import roj.concurrent.task.ITask;
import roj.io.NIOUtil;
import roj.net.MSSSocket;
import roj.net.misc.FDCLoop;
import roj.net.misc.Listener;
import roj.net.misc.Shutdownable;
import roj.net.mss.MSSEngineFactory;
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
public class AEServer extends Listener implements Shutdownable, TaskPool.RejectPolicy {
    static AEServer server;

    // 20分钟
    static final int PIPE_TIMEOUT = 1200_000;

    byte[] info = EmptyArrays.BYTES;

    public void setMOTD(String motd) {
        info = motd.getBytes(StandardCharsets.UTF_8);
    }

    ConcurrentHashMap<String, Room> rooms = new ConcurrentHashMap<>();

    int pipeId;
    ConcurrentHashMap<Integer, PipeGroup> pipes = new ConcurrentHashMap<>();
    final Random rnd;

    private final TaskPool asyncPool;
    private final FDCLoop<Client> man;
    private final MSSEngineFactory factory;

    final AtomicInteger remain;

    public AEServer(InetSocketAddress addr, int conn, MSSEngineFactory factory) throws IOException {
        server = this;

        ServerSocket s = new ServerSocket();
        s.setReuseAddress(true);
        s.bind(addr, conn);
        init(s);

        this.remain = new AtomicInteger(conn);
        this.factory = factory;
        this.rnd = new SecureRandom();

        int thr = Runtime.getRuntime().availableProcessors();
        String p = System.getProperty("AE.client_selectors");
        if (p != null) {
            try {
                thr = Integer.parseInt(p);
            } catch (NumberFormatException ignored) {}
        }
        this.man = new FDCLoop<>(this, "AE 客户端IO", 0, thr, 60000, 100);

        thr = 6;
        p = System.getProperty("AE.executors");
        if (p != null) {
            try {
                thr = Integer.parseInt(p);
            } catch (NumberFormatException ignored) {}
        }
        this.asyncPool = new TaskPool(1, thr, 1, 1, new PrefixFactory("Executor", 120000));
        this.asyncPool.setRejectPolicy(this);
    }

    @Override
    public void onReject(ITask task, int minPending) {
        new ImmediateExecutor(task).start();
    }

    public void asyncExecute(ITask task) {
        asyncPool.pushTask(task);
    }

    @Override
    public void selected(int readyOps) throws Exception {
        Socket c = socket.accept();
        try {
            if(remain.decrementAndGet() <= 0) {
                remain.getAndIncrement();
                c.close();
            } else {
                if (rooms.isEmpty()) pipeId = 0;

                FileDescriptor fd = NIOUtil.fd(c);
                initSocketPref(c);
                Client client = new Client(new MSSSocket(c, fd, factory.newEngine()));
                man.register(client, null);
            }
        } catch (Throwable e) {
            e.printStackTrace();
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

    @Override
    public void shutdown() {
        if(shutdown) return;
        try {
            socket.close();
        } catch (IOException ignored) {}

        asyncPool.shutdown();
        man.shutdown();

        for (Room room : rooms.values()) {
            room.token = null;
            room.master = null;
            synchronized (room.clients) {
                for (Client w : room.clients.values()) {
                    if (w.ch == null) continue;
                    try {
                        write1(w.ch, (byte) PS_ERROR_SHUTDOWN);
                    } catch (IOException ignored) {}

                    try {
                        int t = 10;
                        while (!w.ch.shutdown() && t-- > 0) {
                            LockSupport.parkNanos(10000);
                        }
                        w.close();
                    } catch (IOException ignored) {}
                }
            }
            room.close();
        }

        LockSupport.parkNanos(1000_000);
        shutdown = true;

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

    public void start() throws IOException {
        man.registerListener(this);
    }
}