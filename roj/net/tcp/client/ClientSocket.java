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
package roj.net.tcp.client;

import roj.net.tcp.util.WrappedSocket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketTimeoutException;
import java.net.StandardSocketOptions;
import java.nio.channels.SocketChannel;
import java.util.concurrent.locks.LockSupport;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/10/9 22:48
 */
public abstract class ClientSocket {
    protected SocketChannel server;
    protected WrappedSocket channel;

    protected int connectTimeout = -1, readTimeout = -1;
    protected InetSocketAddress endpoint;
    protected Proxy proxy;

    public ClientSocket() {

    }

    public ClientSocket(String address, int port) throws IOException {
        createSocket(address, port, false);
    }

    public boolean connected() {
        return server != null && (server.isConnected() || server.isConnectionPending());
    }

    public ClientSocket proxy(Proxy proxy) {
        this.proxy = proxy;
        return this;
    }

    /**
     * Open a connection to the server.
     */
    public ClientSocket createSocket(String address, int port, boolean instant) throws IOException {
        disconnect();
        server = createSocket(address, port);
        if (instant) {
            connect();
        }
        return this;
    }

    protected SocketChannel createSocket(String server, int port) throws IOException {
        this.endpoint = new InetSocketAddress(server, port);
        return (SocketChannel) SocketChannel.open()
                                            .setOption(StandardSocketOptions.SO_REUSEADDR, true)
                                            .setOption(StandardSocketOptions.TCP_NODELAY, true)
                                            .configureBlocking(false);
    }

    protected void connect() throws IOException {
        if (!connected()) {
            server.connect(endpoint);
            long to = System.currentTimeMillis() + connectTimeout;
            while (!server.finishConnect()) {
                LockSupport.parkNanos(100);
                if (connectTimeout > 0 && System.currentTimeMillis() > to)
                    throw new SocketTimeoutException();
            }
            server.finishConnect();
            channel = createChannel();
        }
    }

    /**
     * Close an open connection to the server.
     */
    public void disconnect() throws IOException {
        if (server != null) {
            if(channel != null) {
                while (!channel.dataFlush()) ;
                while (!channel.shutdown()) ;
                channel.close();
                channel = null;
            }

            server.close();
            server = null;
        }
    }

    public WrappedSocket getChannel() throws IOException {
        connect();
        return channel;
    }

    public void readTimeout(int timeout) {
        readTimeout = timeout;
    }

    public int readTimeout() {
        return readTimeout;
    }

    public void connectTimeout(int timeout) {
        connectTimeout = timeout;
    }

    public int connectTimeout() {
        return connectTimeout;
    }

    protected abstract WrappedSocket createChannel() throws IOException;
}
