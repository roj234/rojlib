package roj.net.tcp.util;

import roj.io.NonblockingUtil;
import roj.util.ByteList;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.SocketChannel;

public class InsecureSocket implements WrappedSocket {
    SocketChannel socket;
    FileDescriptor fd;

    ByteList buffer;

    public InsecureSocket(SocketChannel socket, FileDescriptor fd) throws IOException {
        this.socket = socket;
        this.fd = fd;
        this.buffer = new ByteList();
    }

    @Override
    public SocketChannel socket() {
        return socket;
    }

    @Override
    public boolean handShake() throws IOException {
        return true;
    }

    @Override
    public int read() throws IOException {
        if(!socket.isOpen())
            return -1;

        buffer.ensureCapacity(buffer.pos() + SharedConfig.READ_MAX);
        int read;
        do {
            read = NonblockingUtil.normalize(NonblockingUtil.readSocket(fd, buffer, SharedConfig.READ_MAX));
        } while (read == -3 && socket.isOpen());
        return read;
    }

    @Override
    public ByteList buffer() {
        return buffer;
    }

    @Override
    public int write(ByteList src) throws IOException {
        if(!socket.isOpen())
            return -1;

        int wrote;
        do {
            wrote = NonblockingUtil.normalize(NonblockingUtil.writeSocket(fd, src, SharedConfig.WRITE_MAX));
        } while (wrote == -3 && socket.isOpen());
        return wrote;
    }

    @Override
    public int write(InputStream src, int max) throws IOException {
        if(!socket.isOpen())
            return -1;

        int cap = Math.min(SharedConfig.WRITE_MAX, max);
        final ByteList buf = this.buffer;
        buf.clear();
        buf.ensureCapacity(cap);
        buf.readStreamArray(src, max);

        int wrote;
        do {
            wrote = NonblockingUtil.normalize(NonblockingUtil.writeSocket(fd, buf, SharedConfig.WRITE_MAX));
        } while (wrote == -3 && socket.isOpen());
        buf.clear();
        return wrote;
    }

    @Override
    public boolean dataFlush() throws IOException {
        return true;
    }

    @Override
    public boolean shutdown() throws IOException {
        dataFlush();
        socket.shutdownInput();
        socket.shutdownOutput();
        return true;
    }

    @Override
    public void close() throws IOException {
        socket.close();
        fd = null;
    }

    @Override
    protected void finalize() throws Throwable {
        if (fd != null) {
            socket.close();
            fd = null;
        }
    }
}
