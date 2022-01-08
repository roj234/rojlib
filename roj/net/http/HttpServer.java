/*
 * This file is a part of MI
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
package roj.net.http;

import roj.collect.TimedHashMap;
import roj.concurrent.TaskPool;
import roj.net.SocketFactory;
import roj.net.http.serv.RequestHandler;
import roj.net.http.serv.Router;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

public class HttpServer implements Runnable {
    public static final boolean THROTTLING_CHECK_ENABLED = false;
    public static final TimedHashMap<String, AtomicInteger> CONNECTING_ADDRESSES  = new TimedHashMap<>(1000);

    final ServerSocket   socket;
    final SocketFactory factory;
    final Router        router;

    public HttpServer(InetSocketAddress address, int conn, Router router) throws IOException {
        this(address, conn, router, SocketFactory.PLAIN_FACTORY);
    }

    public HttpServer(InetSocketAddress address, int conn, Router router, SocketFactory factory) throws IOException {
        this.factory = factory;
        ServerSocket socket = this.socket = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(address, conn);
        this.router = router;
    }

    public final ServerSocket getSocket() {
        return socket;
    }

    @Override
    public void run() {
        final int cpus = Runtime.getRuntime().availableProcessors();
        TaskPool pool = new TaskPool(cpus >> 1, cpus << 1, 128);
        while (true) {
            Socket c;
            try {
                c = socket.accept();
            } catch (IOException e) {
                break;
            }
            try {
                c.setReuseAddress(true);
                pool.pushTask(new RequestHandler(factory.wrap(c), router));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
