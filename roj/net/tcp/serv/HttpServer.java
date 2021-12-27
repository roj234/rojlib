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
package roj.net.tcp.serv;

import roj.collect.TimedHashMap;
import roj.concurrent.TaskHandler;
import roj.concurrent.TaskPool;
import roj.concurrent.task.ITask;
import roj.io.NIOUtil;
import roj.net.SecureUtil;
import roj.net.ssl.EngineAllocator;
import roj.net.ssl.ServerSslConf;
import roj.net.tcp.PlainSocket;
import roj.net.tcp.SSLSocket;
import roj.net.tcp.WrappedSocket;
import roj.net.tcp.serv.util.ChannelRouter;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.util.concurrent.atomic.AtomicInteger;

public class HttpServer implements Runnable {
    public static final boolean THROTTLING_CHECK_ENABLED = false;
    public static final TimedHashMap<String, AtomicInteger> CONNECTING_ADDRESSES  = new TimedHashMap<>(1000);

    protected final ServerSocket socket;
    protected final EngineAllocator ssl;
    protected final Router router;

    public HttpServer(int port, int maxConnection, Router router, String keyStoreFile, char[] keyPassword) throws IOException, GeneralSecurityException {
        this(new InetSocketAddress(port), maxConnection, router, keyStoreFile, keyPassword);
    }

    public HttpServer(InetSocketAddress address, int maxConnection, Router router, String keyStoreFile, char[] keyPassword) throws IOException, GeneralSecurityException {
        this.ssl = enableSSL(keyStoreFile, keyPassword);
        this.socket = socket(address, maxConnection);
        this.router = router;
    }

    public HttpServer(int port, int maxConnection, Router router) throws IOException {
        this(new InetSocketAddress(port), maxConnection, router);
    }

    public HttpServer(InetSocketAddress address, int maxConnection, Router router) throws IOException {
        this.ssl = null;
        this.socket = socket(address, maxConnection);
        this.router = router;
    }

    protected static ServerSocket socket(InetSocketAddress address, int maxConnection) throws IOException {
        ServerSocket socket = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(address, maxConnection);
        return socket;
    }

    protected static EngineAllocator enableSSL(String keyStore, char[] password) throws IOException, GeneralSecurityException {
        return SecureUtil.getSslFactory(new ServerSslConf(keyStore, password));
    }

    public final ServerSocket getSocket() {
        return socket;
    }

    protected TaskHandler getTaskHandler() {
        final int cpus = Runtime.getRuntime().availableProcessors();
        return new TaskPool(cpus >> 1, cpus << 1, 128);
    }

    @Override
    public void run() {
        TaskHandler handler = getTaskHandler();
        while (true) {
            try {
                Socket c = socket.accept();
                c.setReuseAddress(true);
                c.setKeepAlive(true);

                handler.pushTask(getTaskFor(c));
            } catch (IOException e) {
                if(e.getMessage().contains("close")) return;
                e.printStackTrace();
            }
        }
    }

    protected ITask getTaskFor(Socket client) throws IOException {
        FileDescriptor fd = NIOUtil.fd(client);

        WrappedSocket cio = (
                ssl != null ?
                        SSLSocket.get(client, fd, ssl, false) :
                        new PlainSocket(client, fd)
        );

        return new ChannelRouter(cio, router);
    }
}
