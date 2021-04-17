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
import roj.concurrent.task.ITask;
import roj.io.NonblockingUtil;
import roj.net.tcp.TCPServer;
import roj.net.tcp.serv.util.ChannelRouter;
import roj.net.tcp.util.InsecureSocket;
import roj.net.tcp.util.SecureSocket;
import roj.net.tcp.util.WrappedSocket;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.util.concurrent.atomic.AtomicInteger;

public class HttpServer extends TCPServer {
    public static final boolean THROTTLING_CHECK_ENABLED = false;
    public static final TimedHashMap<String, AtomicInteger> CONNECTING_ADDRESSES  = new TimedHashMap<>(1000);

    protected final Router router;

    public HttpServer(int port, int maxConnection, Router router, String keyStoreFile, char[] keyPassword) throws IOException, GeneralSecurityException {
        this(new InetSocketAddress(port), maxConnection, router, keyStoreFile, keyPassword);
    }

    public HttpServer(InetSocketAddress address, int maxConnection, Router router, String keyStoreFile, char[] keyPassword) throws IOException, GeneralSecurityException {
        super(address, maxConnection, keyStoreFile, keyPassword);
        this.router = router;
    }

    public HttpServer(int port, int maxConnection, Router router) throws IOException {
        this(new InetSocketAddress(port), maxConnection, router);
    }

    public HttpServer(InetSocketAddress address, int maxConnection, Router router) throws IOException {
        super(address, maxConnection);
        this.router = router;
    }

    @Override
    protected ITask getTaskFor(Socket client) throws IOException {
        FileDescriptor fd = NonblockingUtil.fd(client);

        WrappedSocket cio = (
                ssl != null ?
                        SecureSocket.get(client, fd, ssl, false) :
                        new InsecureSocket(client, fd)
        );

        return new ChannelRouter(cio, router);
    }
}
