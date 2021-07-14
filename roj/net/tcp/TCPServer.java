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
package roj.net.tcp;

import roj.concurrent.TaskHandler;
import roj.concurrent.pool.TaskPool;
import roj.concurrent.task.ITask;
import roj.net.tcp.serv.util.ChannelRouter;
import roj.net.tcp.ssl.EngineAllocator;
import roj.net.tcp.ssl.ServerSslConf;
import roj.net.tcp.ssl.SslEngineFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.security.GeneralSecurityException;
import java.util.ArrayList;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/12/19 13:22
 */
public abstract class TCPServer implements Runnable {
    protected final ServerSocketChannel socket;
    protected EngineAllocator ssl;
    protected ArrayList<ChannelRouter> routers = new ArrayList<>();

    public TCPServer(int port, int maxConnection, String keyStoreFile, char[] keyPassword) throws IOException, GeneralSecurityException {
        this(new InetSocketAddress(port), maxConnection, keyStoreFile, keyPassword);
    }

    public TCPServer(InetSocketAddress address, int maxConnection, String keyStoreFile, char[] keyPassword) throws IOException, GeneralSecurityException {
        this.ssl = enableHTTPS(keyStoreFile, keyPassword);
        this.socket = socket(address, maxConnection);
    }

    public TCPServer(int port, int maxConnection) throws IOException {
        this(new InetSocketAddress(port), maxConnection);
    }

    public TCPServer(InetSocketAddress address, int maxConnection) throws IOException {
        this.ssl = null;
        this.socket = socket(address, maxConnection);
    }

    protected static ServerSocketChannel socket(InetSocketAddress address, int maxConnection) throws IOException {
        /*.configureBlocking(false)*/
        return ServerSocketChannel.open()
                .setOption(StandardSocketOptions.SO_KEEPALIVE, true)
                .setOption(StandardSocketOptions.SO_REUSEADDR, true).bind(address, maxConnection);
    }

    protected static EngineAllocator enableHTTPS(String keyStore, char[] password) throws IOException, GeneralSecurityException {
        return SslEngineFactory.getAnySslEngine(new ServerSslConf(keyStore, password));
    }

    public final ServerSocketChannel getSocket() {
        return socket;
    }

    protected abstract ITask getTaskFor(SocketChannel client) throws IOException;

    protected TaskHandler getTaskHandler() {
        final int cpus = Runtime.getRuntime().availableProcessors();
        return new TaskPool(cpus >> 1, cpus << 1, 128);
    }

    @Override
    public void run() {
        TaskHandler handler = getTaskHandler();

        Selector selector;
        try {
            selector = Selector.open();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        Thread selectorThread = new Thread() {
            @Override
            public void run() {
                Thread self = Thread.currentThread();
                while (!self.isInterrupted()) {
                    try {
                        selector.select();
                        for (SelectionKey key : selector.selectedKeys()) {
                            handler.pushTask((ChannelRouter) key.attachment());
                        }
                        selector.selectedKeys().clear();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        };
        selectorThread.setDaemon(true);
        selectorThread.setName("Selector");
        selectorThread.start();

        Thread self = Thread.currentThread();
        while (!self.isInterrupted()) {
            try {
                SocketChannel socket = this.socket.accept();

                socket.setOption(StandardSocketOptions.SO_REUSEADDR, true)
                        .setOption(StandardSocketOptions.SO_KEEPALIVE, true)
                        .configureBlocking(false);

                selector.wakeup();
                socket.register(selector, SelectionKey.OP_READ, getTaskFor(socket));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        selectorThread.interrupt();
    }
}