package roj.net.misc;

import roj.io.NIOUtil;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.channels.SelectionKey;

/**
 * @author Roj233
 * @since 2022/3/1 21:39
 */
public abstract class Listener implements Selectable {
    protected ServerSocket socket;
    protected FileDescriptor fd;

    protected SelectionKey key;

    public Listener() {}
    public Listener(ServerSocket socket) throws IOException {
        init(socket);
    }

    public final void init(ServerSocket socket) throws IOException {
        this.socket = socket;
        this.fd = NIOUtil.fd(socket);
    }

    @Override
    public final boolean isClosedOn(SelectionKey key) {
        return socket.isClosed() || !key.isValid();
    }
}
