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
package roj.net.tcp.voice;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/12/19 13:21
 */

import roj.collect.MyHashMap;
import roj.concurrent.TaskHandler;
import roj.concurrent.TaskPool;
import roj.concurrent.task.ITask;
import roj.io.NonblockingUtil;
import roj.net.tcp.TCPServer;
import roj.net.tcp.util.InsecureSocket;
import roj.net.tcp.util.SecureSocket;
import roj.net.tcp.util.WrappedSocket;
import roj.util.Helpers;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class VoiceServer extends TCPServer {
    // name, users
    final Map<String, Set<User>> chatrooms = new MyHashMap<>();
    // user, users
    final Map<User, Set<User>> fastPath = new MyHashMap<>();

    final AtomicBoolean blockLock = new AtomicBoolean(false);

    public VoiceServer(int port, int maxConnection, String keyStoreFile, char[] keyPassword) throws IOException, GeneralSecurityException {
        this(new InetSocketAddress(port), maxConnection, keyStoreFile, keyPassword);
    }

    public VoiceServer(InetSocketAddress address, int maxConnection, String keyStoreFile, char[] keyPassword) throws IOException, GeneralSecurityException {
        super(address, maxConnection, keyStoreFile, keyPassword);
    }

    public VoiceServer(int port, int maxConnection) throws IOException {
        this(new InetSocketAddress(port), maxConnection);
    }

    public VoiceServer(InetSocketAddress address, int maxConnection) throws IOException {
        super(address, maxConnection);
    }

    public void addUser(String room, User add) {
        synchronized (chatrooms) {
            Set<User> routers = chatrooms.computeIfAbsent(room, Helpers.fnMyHashSet());
            routers.add(add);
            fastPath.put(add, routers);
        }
    }

    public Set<User> getUser(String room, User add) {
        synchronized (chatrooms) {
            return chatrooms.getOrDefault(room, Collections.emptySet());
        }
    }

    public boolean removeUser(String room, User remove) {
        synchronized (chatrooms) {
            Set<User> routers = chatrooms.get(room);
            if (routers == null)
                return false;
            fastPath.remove(remove);
            return routers.remove(remove);
        }
    }

    @Override
    protected ITask getTaskFor(Socket client) throws IOException {
        FileDescriptor fd = NonblockingUtil.fd(client);

        WrappedSocket cio = (
                ssl != null ?
                        SecureSocket.get(client, fd, ssl, false) :
                        new InsecureSocket(client, fd)
        );

        return new VoiceHandler(cio, this);
    }

    protected TaskHandler getTaskHandler() {
        final int cpus = Runtime.getRuntime().availableProcessors();
        return new TaskPool(0, cpus, 8);
    }

    public static final class User {
        String nickname, address;
        VoiceHandler connection;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            User user = (User) o;

            return address.equals(user.address);
        }

        @Override
        public int hashCode() {
            return address.hashCode();
        }
    }
}
