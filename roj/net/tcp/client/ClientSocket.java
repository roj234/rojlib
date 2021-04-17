package roj.net.tcp.client;

import roj.net.tcp.util.WrappedSocket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/10/9 22:48
 */
public abstract class ClientSocket {
    protected Socket server;
    protected WrappedSocket channel;

    protected int readTimeout = -1, writeTimeout = -1, connectTimeout = -1;
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

    protected Socket createSocket(String server, int port) throws IOException {
        Socket socket;
        if (proxy != null) {
            switch (proxy.type()) {
                case SOCKS:
                    socket = new Socket(proxy);
                    break;
                case DIRECT:
                    socket = new Socket();
                    break;
                default:
                    // Still connecting through a proxy
                    // server & port will be the proxy address and port
                    socket = new Socket(Proxy.NO_PROXY);
                    break;
            }
        } else
            socket = new Socket();

        this.endpoint = new InetSocketAddress(server, port/* & 0xFFFF*/);
        if (readTimeout >= 0)
            socket.setSoTimeout(readTimeout);
        socket.setReuseAddress(true);
        return socket;
    }

    protected void connect() throws IOException {
        if (!connected()) {
            server.connect(endpoint, connectTimeout <= 0 ? 0 : connectTimeout);
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

    public void connectTimeout(int timeout) {
        connectTimeout = timeout;
    }

    public int connectTimeout() {
        return connectTimeout;
    }

    public void writeTimeout(int timeout) {
        writeTimeout = timeout;
    }

    public int writeTimeout() {
        return writeTimeout;
    }

    public void readTimeout(int timeout) {
        if (server != null && timeout >= 0) {
            try {
                server.setSoTimeout(timeout);
            } catch (IOException e) {
                return;
            }
        }
        readTimeout = timeout;
    }

    public int readTimeout() {
        return readTimeout;
    }

    protected abstract WrappedSocket getChannel() throws IOException;
}
