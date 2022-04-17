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
package roj.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketException;

/**
 * @author Roj234
 * @since  2020/10/9 22:48
 */
public abstract class Connector {
    protected Socket        server;
    protected WrappedSocket channel;
    protected SocketFactory factory = SocketFactory.PLAIN_FACTORY;

    protected int connectTimeout = -1, readTimeout = -1;
    protected InetSocketAddress endpoint;
    protected Proxy proxy;

    public Connector() {

    }

    public Connector(String address, int port) throws IOException {
        createSocket(address, port, false);
    }

    public boolean connected() {
        return server != null && server.isConnected();
    }

    public Connector proxy(Proxy proxy) {
        this.proxy = proxy;
        return this;
    }

    /**
     * Open a connection to the server.
     */
    public Connector createSocket(String address, int port, boolean instant) throws IOException {
        disconnect();
        this.endpoint = new InetSocketAddress(address, port);
        server = new Socket();
        server.setSoTimeout(connectTimeout <= 0 ? 5000 : connectTimeout);
        server.setReuseAddress(true);
        if (instant) {
            connect();
        }
        return this;
    }

    protected void connect() throws IOException {
        if (!connected()) {
            try {
                server.connect(endpoint);
            } catch (SocketException e) {
                if ("Socket closed".equals(e.getMessage())) {
                    server = new Socket();
                    server.setSoTimeout(connectTimeout <= 0 ? 5000 : connectTimeout);
                    server.setReuseAddress(true);
                    connect();
                } else {
                    throw e;
                }
            }
            channel = factory.wrap(server);
        }
    }

    /**
     * Close an open connection to the server.
     */
    public void disconnect() throws IOException {
        Socket server = this.server;
        if (server != null) {
            WrappedSocket c = this.channel;
            if(c != null) {
                try {
                    while (!c.dataFlush()) ;
                    while (!c.shutdown()) ;
                } catch (Throwable ignored) {}
                try {
                    c.close();
                } catch (Throwable ignored) {}
                channel = null;
            }

            server.close();
            this.server = null;
        }
    }

    public WrappedSocket getChannel() throws IOException {
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

    public InetSocketAddress getEndpoint() {
        return endpoint;
    }

    public WrappedSocket getAsyncChannel() throws IOException {
        return channel = factory.wrap(server);
    }
}
