package roj.net.tcp.client;

import roj.net.tcp.util.WrappedSocket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.StandardSocketOptions;
import java.nio.channels.SocketChannel;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/10/9 22:48
 */
public abstract class ClientSocket {
    protected SocketChannel server;
    protected WrappedSocket channel;

    protected int readTimeout = -1, writeTimeout = -1;
    protected InetSocketAddress endpoint;
    protected Proxy proxy;

    public ClientSocket() {

    }

    public ClientSocket(String address, int port) throws IOException {
        createSocket(address, port, false);
    }

    public boolean connected() {
        return server != null && server.isConnected();
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
        this.endpoint = new InetSocketAddress(server, port/* & 0xFFFF*/);
        return (SocketChannel) SocketChannel.open().setOption(StandardSocketOptions.SO_REUSEADDR, true)
                .setOption(StandardSocketOptions.SO_KEEPALIVE, true).configureBlocking(false);
    }

    protected void connect() throws IOException {
        if (!connected()) {
            server.connect(endpoint);
            channel = getChannel();
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

    public void writeTimeout(int timeout) {
        writeTimeout = timeout;
    }

    public int writeTimeout() {
        return writeTimeout;
    }

    public void readTimeout(int timeout) {
        readTimeout = timeout;
    }

    public int readTimeout() {
        return readTimeout;
    }

    protected abstract WrappedSocket getChannel() throws IOException;
}
