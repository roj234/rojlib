package roj.net.tcp.voice;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/12/19 13:21
 */

import roj.collect.MyHashMap;
import roj.concurrent.TaskHandler;
import roj.concurrent.pool.TaskPool;
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
    public void run() {
        TaskHandler handler = getTaskHandler();

        Thread current = Thread.currentThread();

        while (!current.isInterrupted()) {
            while (blockLock.get()) {
                Thread.yield();
            }

            try {
                Socket socket = this.socket.accept();
                socket.setReuseAddress(true);
                socket.setSoLinger(true, 2);

                FileDescriptor fd = NonblockingUtil.fd(socket);

                WrappedSocket cio = (
                        ssl != null ?
                                SecureSocket.get(socket, fd, ssl, false) :
                                new InsecureSocket(socket, fd)
                );

                handler.pushTask(new VoiceHandler(cio, this));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    protected TaskHandler getTaskHandler() {
        final int cpus = Runtime.getRuntime().availableProcessors();
        return new TaskPool(0, cpus, 4);
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
