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
package roj.net.http;

import roj.net.WrappedSocket;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.InvalidMarkException;
import java.util.concurrent.locks.LockSupport;

/**
 * 如何把非阻塞包装成阻塞？
 * @author solo6975
 * @since 2021/10/23 21:11
 */
public class SocketInputStream extends InputStream {
    final WrappedSocket socket;

    int pos;
    private int markLimit, markPos;

    // Shared
    long dataRemain;
    int readTimeout;

    public SocketInputStream(WrappedSocket socket) {
        this.socket = socket;
        dataRemain = socket.buffer().remaining();
    }

    public SocketInputStream init(String s, int readTimeout) {
        this.readTimeout = readTimeout <= 0 ? 5000 : readTimeout;
        dataRemain = (s == null ? Integer.MAX_VALUE : (dataRemain + Long.parseLong(s)));
        return this;
    }

    @Override
    public int read() throws IOException {
        if (socket.socket().isClosed()) throw new IOException("Socket closed.");
        if (markLimit == -1) return -1;
        ByteBuffer buf = socket.buffer();
        if (pos == buf.position()) {
            if (!fill()) return -1;
        }
        return buf.get(pos++) & 0xFF;
    }

    private boolean fill() throws IOException {
        ByteBuffer buf = socket.buffer();
        if (buf.position() > markLimit) markLimit = 0;
        if (markLimit <= 0) {
            buf.clear();
            pos = 0;
        }

        int read;
        if (dataRemain > 0) {
            long timeout = System.currentTimeMillis() + readTimeout;
            while ((read = socket.read()) == 0) {
                LockSupport.parkNanos(100L);
                if (System.currentTimeMillis() > timeout) {
                    throw new SocketTimeoutException(readTimeout + " ms");
                }
            }
        } else {
            read = -1;
        }

        if (read < 0) {
            markLimit = -1;
            return false;
        } else {
            dataRemain -= read;
            return true;
        }
    }

    @Override
    public int read(@Nonnull byte[] b, int off, int len) throws IOException {
        if (socket.socket().isClosed()) throw new IOException("Socket closed.");
        if (markLimit == -1) return -1;

        ByteBuffer buf = socket.buffer();
        if (buf.position() == pos) {
            if (!fill()) return -1;
        }

        int p = buf.position();
        int read = Math.min(p - pos, len);
        buf.position(pos);
        buf.get(b, off, read).position(p);
        pos += read;
        return read;
    }

    @Override
    public int available() throws IOException {
        if (socket.socket().isClosed()) throw new IOException("Socket closed.");
        ByteBuffer buf = socket.buffer();
        return buf.position() - pos;
    }

    @Override
    public void close() throws IOException {
        ByteBuffer buf = socket.buffer();
        buf.limit(0);
    }

    @Override
    public void mark(int limit) {
        if (limit <= 0 || markLimit == -1) return;
        ByteBuffer buf = socket.buffer();
        if (limit > buf.capacity()) throw new InvalidMarkException();
        if (pos > 0) {
            buf.flip().position(pos);
            buf.compact();
        }
        markPos = pos;
        markLimit = limit + buf.position();
    }

    @Override
    public boolean markSupported() {
        return true;
    }

    @Override
    public void reset() throws IOException {
        if (socket.socket().isClosed()) throw new IOException("Socket closed.");
        ByteBuffer buf = socket.buffer();
        buf.position(markPos);
    }

    void pass(int bufPos) {
        ByteBuffer buf = socket.buffer();
        if (markLimit <= 0) {
            buf.flip().position(bufPos);
            buf.compact();
            pos = 0;
        } else {
            pos = bufPos;
        }
    }
}