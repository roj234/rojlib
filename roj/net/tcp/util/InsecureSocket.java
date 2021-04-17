package roj.net.tcp.util;

import roj.io.NonblockingUtil;
import roj.util.ByteList;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class InsecureSocket implements WrappedSocket {
    Socket socket;
    FileDescriptor fd;
    OutputStream output;

    ByteList buffer;

    public InsecureSocket(Socket socket, FileDescriptor fd) throws IOException {
        this.socket = socket;
        this.fd = fd;
        this.output = socket.getOutputStream();
        this.buffer = new ByteList();
    }

    @Override
    public Socket socket() {
        return socket;
    }

    @Override
    public boolean handShake() throws IOException {
        return true;
    }

    @Override
    public int read() throws IOException {
        buffer.ensureCapacity(buffer.pos() + SharedConfig.READ_MAX);
        int read;
        do {
            read = NonblockingUtil.normalize(NonblockingUtil.readSocket(fd, buffer, SharedConfig.READ_MAX));
        } while (read == -3 && output != null);
        return read;
    }

    @Override
    public ByteList buffer() {
        return buffer;
    }

    @Override
    public OutputStream getOut() {
        return output;
    }

    @Override
    public int write(ByteList src) throws IOException {
        int wrote;
        do {
            wrote = NonblockingUtil.normalize(NonblockingUtil.writeSocket(fd, src, SharedConfig.WRITE_MAX));
        } while (wrote == -3 && output != null);
        return wrote;
    }

    @Override
    public int write(InputStream src, int max) throws IOException {
        int cap = Math.min(SharedConfig.WRITE_MAX, max);
        final ByteList buf = this.buffer;
        buf.clear();
        buf.ensureCapacity(cap);
        buf.readStreamArray(src, max);

        int wrote;
        do {
            wrote = NonblockingUtil.normalize(NonblockingUtil.writeSocket(fd, buf, SharedConfig.WRITE_MAX));
        } while (wrote == -3 && output != null);
        buf.clear();
        return wrote;
    }

    @Override
    public boolean dataFlush() throws IOException {
        if (output != null)
            output.flush();
        return true;
    }

    @Override
    public boolean shutdown() throws IOException {
        dataFlush();
        socket.shutdownInput();
        socket.shutdownOutput();
        output = null;
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
