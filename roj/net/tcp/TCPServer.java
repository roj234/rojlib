package roj.net.tcp;

import roj.concurrent.TaskHandler;
import roj.concurrent.pool.TaskPool;
import roj.net.tcp.ssl.EngineAllocator;
import roj.net.tcp.ssl.ServerSslConf;
import roj.net.tcp.ssl.SslEngineFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.security.GeneralSecurityException;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/12/19 13:22
 */
public abstract class TCPServer implements Runnable {
    protected final ServerSocket socket;
    protected EngineAllocator ssl;

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

    protected static ServerSocket socket(InetSocketAddress address, int maxConnection) throws IOException {
        ServerSocket socket = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(address, maxConnection);
        return socket;
    }

    protected static EngineAllocator enableHTTPS(String keyStore, char[] password) throws IOException, GeneralSecurityException {
        return SslEngineFactory.getAnySslEngine(new ServerSslConf(keyStore, password));
    }

    public final ServerSocket getSocket() {
        return socket;
    }

    @Override
    public abstract void run();

    protected TaskHandler getTaskHandler() {
        final int cpus = Runtime.getRuntime().availableProcessors();
        return new TaskPool(cpus, cpus * 3, 128);
    }

}