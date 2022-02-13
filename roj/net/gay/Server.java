/*
 * This file is a part of MoreItems
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2022 Roj234
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
package roj.net.gay;

import roj.concurrent.TaskPool;
import roj.concurrent.task.ITaskNaCl;
import roj.io.NIOUtil;
import roj.net.MSSSocket;
import roj.net.NetworkUtil;
import roj.net.mss.*;
import roj.util.ByteList;
import roj.util.FastLocalThread;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Function;

/**
 * @author solo6975
 * @since 2022/1/3 17:25
 */
public class Server extends FastLocalThread {
    public static void main(String[] args) throws IOException, GeneralSecurityException {
        System.out.println("线性版本管理系统 Gay 1.0 联网更新服务器");
        if (args.length < 2) {
            System.out.println("参数: Server <port> <base path>");
            return;
        }
        int port = Integer.parseInt(args[0]);
        Server s = new Server(new InetSocketAddress(InetAddress.getLocalHost(), port), 40);
        s.repo = Repository.init(new File(args[1]));
        s.repo.load();
        s.setName("Gay Server Acceptor");
        s.start();
    }

    static final int IDENTIFIER = 0xABCDEF12;

    final ServerSocket     socket;
    final MSSEngineFactory factory;
    Repository repo;

    AtomicInteger connected = new AtomicInteger();
    int maxConn;
    static final Function<InetAddress, AtomicInteger> COUNTER = (x) -> new AtomicInteger();

    TaskPool watcher = new TaskPool(1, 10, 1);

    public Server(InetSocketAddress address, int max) throws IOException, GeneralSecurityException {
        KeyPair pair = NetworkUtil.genAndStoreRSAKey(new File("g_server.key"),
                                                     new File("g_client.key"), new byte[2]);
        if (pair == null)
            throw new NoSuchAlgorithmException("A critical parameter to construct the MSS engine is missing");

        SimpleEngineFactory factory = new SimpleEngineFactory(JKeyFormat.JAVARSA, pair.getPublic(), pair.getPrivate());
        factory.setPSK(new MSSKeyPair[]{
            new SimplePSK(233, pair.getPublic(), pair.getPrivate())
        });
        this.factory = factory;
        this.socket = socket(address, max);
        this.maxConn = max;
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
                    watcher.pushTask(new Task(this, new MSSSocket(c, NIOUtil.fd(c), factory.newEngine())));
                }
            } catch (IOException | GeneralSecurityException e) {
                if(e.getMessage().contains("close")) return;
                e.printStackTrace();
            }
        }
    }

    static final class Task implements ITaskNaCl {
        final Server owner;
        final MSSSocket conn;
        public Task(Server owner, MSSSocket conn) {
            this.owner = owner;
            this.conn = conn;
        }

        @Override
        public void calculate(Thread thread) throws Exception {
            try {
                int timeout = 5000;
                while (!conn.handShake()) {
                    LockSupport.parkNanos(20);
                    if (timeout-- == 0) {
                        System.out.println(conn + " 握手超时");
                        return;
                    }
                }
                timeout = 5000;
                ByteBuffer rb = conn.buffer();
                while (rb.position() < 8) {
                    int read = conn.read();
                    if (read == 0) {
                        LockSupport.parkNanos(20);
                        if (timeout-- == 0) {
                            System.out.println(conn + " 读取超时");
                            return;
                        }
                        continue;
                    }
                    if (read < 0) {
                        System.out.println(conn + " 连接断开 " + read);
                        return;
                    }
                }
                if (rb.getInt(0) != IDENTIFIER) {
                    System.out.println(conn + " 无效客户端");
                    while (!conn.shutdown() && timeout-- > 0) ;
                    return;
                }
                ByteList w = new ByteList(1024);
                Repository repo = owner.repo;
                if (rb.getInt(4) != repo.get()) {
                    Ver.genPatch(repo.get(rb.getInt(4)), repo.get(repo.get()), w);
                    if (rb.capacity() < w.wIndex() + 8) {
                        rb = ByteBuffer.allocateDirect(w.wIndex() + 8);
                    }
                    rb.clear();
                    rb.putInt(IDENTIFIER).putInt(repo.get())
                      .putInt(w.wIndex()).put(w.list, 0, w.wIndex());
                } else {
                    rb.putInt(IDENTIFIER).putInt(repo.get()).putInt(0);
                }
                rb.flip();
                timeout = 10000;
                while (timeout-- > 0) {
                    int wr = conn.write(rb);
                    if (wr == 0) {
                        if (!rb.hasRemaining()) break;
                        LockSupport.parkNanos(20);
                    }
                    if (wr < 0) {
                        System.out.println(conn + " 连接断开 " + wr);
                        return;
                    }
                }
                NIOUtil.clean(rb);
                timeout = 1000;
                while (!conn.shutdown() && timeout-- > 0) ;
            } finally {
                conn.close();
            }
        }

        @Override
        public boolean isDone() {
            return conn.socket().isClosed();
        }
    }
}
