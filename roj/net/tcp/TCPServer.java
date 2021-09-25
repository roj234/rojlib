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
import roj.concurrent.TaskPool;
import roj.concurrent.task.ITask;
import roj.net.ssl.EngineAllocator;
import roj.net.ssl.ServerSslConf;
import roj.net.ssl.SslEngineFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.GeneralSecurityException;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/12/19 13:22
 */
public abstract class TCPServer implements Runnable {
    protected final ServerSocket socket;
    protected EngineAllocator ssl;

    public TCPServer(int port, int maxConnection, String keyStoreFile, char[] keyPassword) throws IOException, GeneralSecurityException {
        this(new InetSocketAddress(port), maxConnection, keyStoreFile, keyPassword);
    }

    public TCPServer(InetSocketAddress address, int maxConnection, String keyStoreFile, char[] keyPassword) throws IOException, GeneralSecurityException {
        this.ssl = enableSSL(keyStoreFile, keyPassword);
        this.socket = socket(address, maxConnection);
    }

    public TCPServer(int port, int maxConnection) throws IOException {
        this(new InetSocketAddress(port), maxConnection);
    }

    public TCPServer(InetSocketAddress address, int maxConnection) throws IOException {
        this.ssl = null;
        this.socket = socket(address, maxConnection);
    }

    protected static ServerSocket socket(InetSocketAddress address, int maxConnection) throws IOException {
        ServerSocket socket = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(address, maxConnection);
        return socket;
    }

    protected static EngineAllocator enableSSL(String keyStore, char[] password) throws IOException, GeneralSecurityException {
        return SslEngineFactory.getSslFactory(new ServerSslConf(keyStore, password));
    }

    public final ServerSocket getSocket() {
        return socket;
    }

    protected abstract ITask getTaskFor(Socket client) throws IOException;

    protected TaskHandler getTaskHandler() {
        final int cpus = Runtime.getRuntime().availableProcessors();
        return new TaskPool(cpus >> 1, cpus << 1, 128);
    }

    @Override
    public void run() {
        TaskHandler handler = getTaskHandler();
        while (true) {
            try {
                Socket socket = this.socket.accept();
                socket.setReuseAddress(true);
                socket.setKeepAlive(true);

                handler.pushTask(getTaskFor(socket));
            } catch (IOException e) {
                if(e.getMessage().contains("close")) return;
                e.printStackTrace();
            }
        }
    }
}