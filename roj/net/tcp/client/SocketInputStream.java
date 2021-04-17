/*
 * This file is a part of MoreItems
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
package roj.net.tcp.client;

import roj.net.tcp.util.WrappedSocket;
import roj.util.ByteList;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.util.concurrent.locks.LockSupport;

/**
 * 如何把非阻塞包装成阻塞？
 * <br> 傻逼GIT搞丢了我可怜的这个class，这是反编译出来的
 *
 * @author solo6975
 * @version 0.1
 * @since 2021/10/23 21:11
 */
public class SocketInputStream extends InputStream {
    public final WrappedSocket socket;

    private final ByteList buf;
    private int markLimit;

    // Shared
    int bufPos;
    long dataRemain;
    int readTimeout;

    public SocketInputStream(WrappedSocket socket, int bufPos) {
        this.socket = socket;
        this.buf = socket.buffer();
        if (bufPos > 0) {
            this.bufPos = bufPos;
            dataRemain = -(buf.pos() - bufPos);
            moveFirst();
        }
    }

    public SocketInputStream init(String s, int readTimeout) {
        this.readTimeout = readTimeout;
        dataRemain = (s == null ? Integer.MAX_VALUE : (dataRemain + Long.parseLong(s)));
        return this;
    }

    @Override
    public int read() throws IOException {
        if (socket.socket().isClosed()) throw new IOException("Socket closed.");
        if (bufPos < 0) return -1;
        if (buf.pos() == this.bufPos) {
            if (!fill()) return -1;
        }
        return buf.getU(bufPos++);
    }

    private boolean fill() throws IOException {
        if (bufPos > markLimit) markLimit = 0;
        if (markLimit == 0)
            buf.clear();

        int read;
        if (dataRemain > 0) {
            long timeout = System.currentTimeMillis() + readTimeout;
            while ((read = socket.read()) == 0) {
                LockSupport.parkNanos(100L);
                if (System.currentTimeMillis() > timeout) {
                    throw new SocketTimeoutException("Read Timeout");
                }
            }
        } else {
            read = -1;
        }

        if (read < 0) {
            bufPos = -1;
            return false;
        } else {
            dataRemain -= read;
            if (markLimit == 0) bufPos = 0;
            return true;
        }
    }

    @Override
    public int read(@Nonnull byte[] b, int off, int len) throws IOException {
        if (socket.socket().isClosed()) throw new IOException("Socket closed.");
        if (bufPos < 0) return -1;

        if (buf.pos() == this.bufPos) {
            if (!fill()) return -1;
        }

        int read = Math.min(buf.pos() - bufPos, len);
        System.arraycopy(buf.list, bufPos, b, off, read);
        this.bufPos += read;
        return read;
    }

    @Override
    public int available() throws IOException {
        if (socket.socket().isClosed()) throw new IOException("Socket closed.");
        return buf.pos() - bufPos;
    }

    @Override
    public void close() throws IOException {
        while (!socket.shutdown())
            LockSupport.parkNanos(20L);

        socket.close();
    }

    @Override
    public void mark(int limit) {
        if (limit <= 0 || bufPos < 0) return;
        if (bufPos > 0) moveFirst();
        this.markLimit = limit;
    }

    private void moveFirst() {
        ByteList buf = this.buf;
        System.arraycopy(buf.list, bufPos, buf.list, 0, buf.pos() - bufPos);
        buf.pos(buf.pos() - bufPos);
        bufPos = 0;
    }

    @Override
    public boolean markSupported() {
        return true;
    }

    @Override
    public void reset() throws IOException {
        if (socket.socket().isClosed()) throw new IOException("Socket closed.");
        if (markLimit == 0) throw new IOException("Not marked");
        bufPos = 0;
    }

    void pass(int bufPos) {
        this.bufPos = bufPos;
        if (markLimit == 0)
            moveFirst();
    }
}