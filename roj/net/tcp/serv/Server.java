package roj.net.tcp.serv;

import roj.concurrent.TaskHandler;
import roj.io.NonblockingUtil;
import roj.net.tcp.TCPServer;
import roj.net.tcp.serv.util.ChannelRouter;
import roj.net.tcp.util.InsecureSocket;
import roj.net.tcp.util.SecureSocket;
import roj.net.tcp.util.WrappedSocket;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.GeneralSecurityException;

public class Server extends TCPServer {
    protected final Router router;

    public Server(int port, int maxConnection, Router router, String keyStoreFile, char[] keyPassword) throws IOException, GeneralSecurityException {
        this(new InetSocketAddress(port), maxConnection, router, keyStoreFile, keyPassword);
    }

    public Server(InetSocketAddress address, int maxConnection, Router router, String keyStoreFile, char[] keyPassword) throws IOException, GeneralSecurityException {
        super(address, maxConnection, keyStoreFile, keyPassword);
        this.router = router;
    }

    public Server(int port, int maxConnection, Router router) throws IOException {
        this(new InetSocketAddress(port), maxConnection, router);
    }

    public Server(InetSocketAddress address, int maxConnection, Router router) throws IOException {
        super(address, maxConnection);
        this.router = router;
    }

    @Override
    public void run() {
        TaskHandler handler = getTaskHandler();

        Thread self = Thread.currentThread();
        while (!self.isInterrupted()) {
            try {
                Socket socket = this.socket.accept();
                socket.setReuseAddress(true);
                //socket.setSoLinger(true, 2);

                FileDescriptor fd = NonblockingUtil.fd(socket);

                WrappedSocket cio = (
                        ssl != null ?
                                SecureSocket.get(socket, fd, ssl, false) :
                                new InsecureSocket(socket, fd)
                );

                handler.pushTask(new ChannelRouter(cio, router));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
