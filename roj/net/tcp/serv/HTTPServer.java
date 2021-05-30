package roj.net.tcp.serv;

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
import java.nio.channels.SocketChannel;
import java.security.GeneralSecurityException;

public class HTTPServer extends TCPServer {
    protected final Router router;

    public HTTPServer(int port, int maxConnection, Router router, String keyStoreFile, char[] keyPassword) throws IOException, GeneralSecurityException {
        this(new InetSocketAddress(port), maxConnection, router, keyStoreFile, keyPassword);
    }

    public HTTPServer(InetSocketAddress address, int maxConnection, Router router, String keyStoreFile, char[] keyPassword) throws IOException, GeneralSecurityException {
        super(address, maxConnection, keyStoreFile, keyPassword);
        this.router = router;
    }

    public HTTPServer(int port, int maxConnection, Router router) throws IOException {
        this(new InetSocketAddress(port), maxConnection, router);
    }

    public HTTPServer(InetSocketAddress address, int maxConnection, Router router) throws IOException {
        super(address, maxConnection);
        this.router = router;
    }

    @Override
    protected ITask getTaskFor(SocketChannel client) throws IOException {
        FileDescriptor fd = NonblockingUtil.fd(client);

        WrappedSocket cio = (
                ssl != null ?
                        SecureSocket.get(client, fd, ssl, false) :
                        new InsecureSocket(client, fd)
        );

        return new ChannelRouter(cio, router);
    }
}
