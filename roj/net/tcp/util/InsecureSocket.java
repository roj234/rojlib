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
package roj.net.tcp.util;

import roj.io.NonblockingUtil;
import roj.util.ByteList;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;

public class InsecureSocket implements WrappedSocket {
    Socket socket;
    FileDescriptor fd;

    ByteList buffer;

    public InsecureSocket(Socket socket, FileDescriptor fd) {
        this.socket = socket;
        this.fd = fd;
        this.buffer = new ByteList();
    }

    @Override
    public FileDescriptor fd() {
        return fd;
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
        return read(65536);
    }

    @Override
    public int read(int max) throws IOException {
        if(socket.isClosed())
            return -1;

        buffer.ensureCapacity(buffer.pos() + max);
        int read;
        do {
            read = NonblockingUtil.normalize(NonblockingUtil.readSocket(fd, buffer, max));
        } while (read == -3 && !socket.isClosed());
        return read;
    }

    @Override
    public ByteList buffer() {
        return buffer;
    }

    @Override
    public int write(ByteList src) throws IOException {
        if(socket.isClosed())
            return -1;

        int wrote;
        do {
            wrote = NonblockingUtil.normalize(NonblockingUtil.writeSocket(fd, src, Shared.WRITE_MAX));
        } while (wrote == -3 && !socket.isClosed());
        return wrote;
    }

    @Override
    public int write(InputStream src, int max) throws IOException {
        if(socket.isClosed())
            return -1;

        int cap = Math.min(Shared.WRITE_MAX, max);
        final ByteList buf = this.buffer;
        buf.clear();
        buf.ensureCapacity(cap);
        buf.readStreamArray(src, max);

        int wrote;
        do {
            wrote = NonblockingUtil.normalize(NonblockingUtil.writeSocket(fd, buf, Shared.WRITE_MAX));
        } while (wrote == -3 && !socket.isClosed());
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
        try {
            socket.shutdownInput();
        } catch (IOException ignored) {}
        try {
            socket.shutdownOutput();
        } catch (IOException ignored) {}
        return true;
    }

    @Override
    public void close() throws IOException {
        socket.close();
        //fd = null;
    }

    @Override
    public void reuse() throws IOException {
        buffer.clear();
    }

    @Override
    public String toString() {
        return "InsSocket{" + socket.getRemoteSocketAddress() + '}';
    }
}
