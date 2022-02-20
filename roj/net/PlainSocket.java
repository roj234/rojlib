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
package roj.net;

import roj.io.NIOUtil;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;

public class PlainSocket implements WrappedSocket {
    public static final ByteBuffer EMPTY = ByteBuffer.allocate(0);

    Socket socket;
    FileDescriptor fd;

    ByteBuffer rBuf, wBuf;

    public PlainSocket(Socket socket, FileDescriptor fd) {
        this.socket = socket;
        this.fd = fd;
        this.rBuf = ByteBuffer.allocateDirect(4096);
        this.wBuf = EMPTY;
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
        return read(rBuf.remaining() + 1);
    }
//
//    @Override
//    public int read(ByteBuffer dst) throws IOException {
//        if(socket.isClosed()) return -1;
//
//        int read;
//        do {
//            read = NIOUtil.readToNativeBuffer(fd, dst,
//                                              NIOUtil.SOCKET_FD);
//        } while (read == -3 && !socket.isClosed());
//        return read;
//    }

    @Override
    public int read(int max) throws IOException {
        if(socket.isClosed()) return -1;

        ByteBuffer buf = this.rBuf;
        if (buf.position() == buf.capacity()) {
            buf = expandReadBuffer(buf.capacity() << 1);
        }

        int lim = buf.limit();
        if(max >= 0) buf.limit(Math.min(buf.position() + max, lim));
        int read;
        try {
            do {
                read = NIOUtil.readToNativeBuffer(fd, buf,
                                                  NIOUtil.SOCKET_FD);
            } while (read == -3 && !socket.isClosed());
        } catch (IOException e) {
            socket.close();
            throw e;
        } finally {
            buf.limit(lim);
        }
        return read;
    }

    protected ByteBuffer expandReadBuffer(int cap) {
        ByteBuffer cur = rBuf;
        ByteBuffer next = ByteBuffer.allocateDirect(cap);
        cur.flip();
        next.put(cur);
        NIOUtil.clean(cur);
        return rBuf = next;
    }

    @Override
    public ByteBuffer buffer() {
        return rBuf;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        if(socket.isClosed())
            return -1;
        if (!dataFlush()) return 0;

        ByteBuffer tmp;
        if (!src.isDirect()) {
            if (!src.hasArray()) {
                int cap = Math.min(WRITE_ONCE, src.remaining());
                if (wBuf.capacity() < cap)
                    wBuf = ByteBuffer.allocate(cap);
                else
                    wBuf.clear();
                wBuf.put(src).flip();
                tmp = wBuf;
            } else {
                tmp = src;
            }
        } else {
            tmp = null;
        }

        int w;
        try {
            do {
                if (tmp != null) {
                    w = NIOUtil.swrite(fd, tmp.array(), tmp.position(), tmp.limit(), NIOUtil.SOCKET_FD);
                    if (w > 0) {
                        tmp.position(tmp.position() + w);
                    }
                } else {
                    w = NIOUtil.writeFromNativeBuffer(fd, src, NIOUtil.SOCKET_FD);
                }
            } while (w == -3 && !socket.isClosed());
        } catch (IOException e) {
            socket.close();
            throw e;
        }

        return tmp == wBuf ? tmp.limit() : w;
    }

    @Override
    public boolean dataFlush() throws IOException {
        if (wBuf.hasRemaining()) {
            int w;
            try {
                do {
                    w = NIOUtil.swrite(fd, wBuf.array(), wBuf.position(), wBuf.limit(), NIOUtil.SOCKET_FD);
                    if (w > 0)
                        wBuf.position(wBuf.position() + w);
                } while (w == -3 && !socket.isClosed());
            } catch (IOException e) {
                socket.close();
                throw e;
            }
            if (w < 0) socket.close();
        }
        return !wBuf.hasRemaining();
    }

    @Override
    public boolean shutdown() throws IOException {
        return dataFlush();
    }

    @Override
    public boolean isOpen() {
        return !socket.isClosed();
    }

    @Override
    public void close() throws IOException {
        socket.close();
        // NIOUtil.close(fd); // upper line would do that
        if (rBuf != null) NIOUtil.clean(rBuf);
        rBuf = null;
        wBuf = EMPTY;
    }

    @Override
    public void reset() throws IOException {
        if (socket.isClosed())
            throw new IOException("Socket closed");
        rBuf.clear();
    }

    @Override
    public String toString() {
        return "Socket[" + socket.getRemoteSocketAddress() + ']';
    }
}
