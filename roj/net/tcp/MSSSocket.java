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
package roj.net.tcp;


import roj.io.IOUtil;
import roj.io.NonblockingUtil;
import roj.net.mss.MSSEngine;
import roj.net.mss.MSSEngineClient;
import roj.net.mss.MSSException;
import roj.net.tcp.util.Shared;

import java.io.EOFException;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.ByteBuffer;

/**
 * My Secure Socket No Certificate
 */
public class MSSSocket extends PlainSocket {
    private final MSSEngine engine;

    private ByteBuffer networkIn, networkOut, outCopy;

    private boolean hsDone, shutdown;

    public MSSSocket(Socket sc, FileDescriptor fd, MSSEngine server) {
        super(sc, fd);

        engine = server;

        networkIn = ByteBuffer.allocateDirect(4096);
        networkOut = ByteBuffer.allocateDirect(4096);
        outCopy = networkOut.duplicate();
        networkOut.limit(0);
    }

    public MSSSocket(Socket sc, FileDescriptor fd) {
        this(sc, fd, new MSSEngineClient());
    }

    private void expandNetIn(int _size) {
        ByteBuffer bb = ByteBuffer.allocateDirect(_size);
        networkIn.flip();
        bb.put(networkIn);
        IOUtil.clean(networkIn);
        networkIn = bb;
    }

    private void expandNetOut(int _size) {
        ByteBuffer bb = ByteBuffer.allocateDirect(_size);
        networkOut.flip();
        bb.put(networkOut);
        IOUtil.clean(networkOut);
        outCopy = bb.duplicate();
        bb.flip();
        networkOut = bb;
    }

    private boolean _write(ByteBuffer bb) throws IOException {
        if (bb.hasRemaining()) {
            int w;
            do {
                w = NonblockingUtil.normalize(
                        NonblockingUtil.writeFromNativeBuffer(fd, bb,
                                                              NonblockingUtil.SOCKET_FD));
            } while (w == -3 && !socket.isClosed());
        }
        return !bb.hasRemaining();
    }

    @SuppressWarnings("fallthrough")
    public boolean handShake() throws IOException {
        if (hsDone) {
            return true;
        }

        if (networkOut.hasRemaining()) {
            if (!_write(networkOut)) {
                return false;
            }

            if (engine.isHandshakeDone())
                hsDone = true;
            return hsDone;
        }

        if (_read(networkIn) < 0) {
            engine.close(null);
            return hsDone;
        }

        needIO:
        do {
            networkIn.flip();
            int result = engine.handshake(outCopy, networkIn);
            networkIn.compact();

            switch (result) {
                case MSSEngine.HS_OK:
                    if (engine.getHandShakeStatus() == MSSEngine.HS_FINISHED) {
                        hsDone = true;
                        break needIO;
                    }
                    if (outCopy.position() > 0) {
                        networkOut.position(0).limit(outCopy.position());
                        outCopy.clear();
                        break needIO;
                    }
                    break;
                case MSSEngine.BUFFER_UNDERFLOW:
                    result = engine.getBufferSize();
                    if (result > networkIn.capacity()) {
                        expandNetIn(result);
                    }

                    break needIO;
                case MSSEngine.BUFFER_OVERFLOW:
                    result = outCopy.capacity() + engine.getBufferSize();
                    expandNetOut(result);
                    break;
            }
        } while (true);

        return hsDone;
    }

    private int _read(ByteBuffer buffer) throws IOException {
        return NonblockingUtil.normalize(
                NonblockingUtil.readToNativeBuffer(fd, buffer,
                                                   NonblockingUtil.SOCKET_FD));
    }

    int pushback;
    public int read(int max) throws IOException {
        if (!hsDone)
            throw new MSSException("Not handshake");

        int nread;
        if(pushback > 0) {
            nread = Math.min(pushback, max);
            rBuf.position(rBuf.position() + nread);
            pushback -= nread;
            if (nread == max) {
                return max;
            }
            max -= nread;
        } else {
            nread = 0;
        }

        if (_read(networkIn) < 0) {
            engine.close(null);
            return -1;
        }

        do {
            int dt = rBuf.position();
            networkIn.flip();
            int result = engine.unwrap(networkIn, rBuf);
            networkIn.compact();
            dt = rBuf.position() - dt;

            if (dt > max) {
                max = 0;
                int of = pushback = dt - max;
                rBuf.position(rBuf.position() - of);
            }

            if (result < 0) {
                // OVERFLOW
                // Reset the application buffer size.
                result = -result;
                if (result > rBuf.capacity()) {
                    expandReadBuffer(result);
                }
            } else if (result > 0) {
                // UNDERFLOW
                // Resize buffer if needed.
                if (result > networkIn.capacity()) {
                    expandNetIn(result);
                }
                break;
            } else {
                nread += dt;
            }
        } while (max > 0);
        return nread;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        if (!hsDone) throw new MSSException("Not handshake");
        if (!dataFlush()) return 0;

        networkOut.clear();
        int dt = src.remaining();
        engine.wrap(src, networkOut);
        dt -= src.remaining();
        networkOut.flip();

        if (networkOut.hasRemaining())
            _write(networkOut);

        return dt;
    }

    @Override
    public int write(InputStream src, int max) throws IOException {
        if (!hsDone) throw new MSSException("Not handshake");
        if (!dataFlush()) return 0;

        int cap = Math.min(Shared.WRITE_MAX, max);
        if (wBuf.capacity() < cap) wBuf = ByteBuffer.allocate(cap);
        else wBuf.clear();
        int len = src.read(wBuf.array(), 0, cap);
        if (len <= 0) return len;
        wBuf.limit(len);

        networkOut.clear();
        engine.wrap(wBuf, networkOut);
        networkOut.flip();

        return len;
    }

    public boolean dataFlush() throws IOException {
        if (networkOut.hasRemaining()) {
            _write(networkOut);
        } else if (wBuf.hasRemaining()) {
            networkOut.clear();
            int dt = wBuf.position();
            engine.wrap(wBuf, networkOut);
            dt = wBuf.position() - dt;
            networkOut.flip();
            if (dt == 0)
                throw new EOFException("Unknown state occurred");
        }

        return !networkOut.hasRemaining();
    }

    public boolean shutdown() throws IOException {
        if (!shutdown) {
            engine.close("SHUTDOWN");
            shutdown = true;
        }

        if (networkOut.hasRemaining() && _write(networkOut)) {
            return false;
        }
        if (engine.isClosed()) return true;

        networkOut.clear();
        engine.wrap(null, networkOut);
        networkOut.flip();

        if (networkOut.hasRemaining()) {
            _write(networkOut);
        }

        return !networkOut.hasRemaining() && engine.isClosed();
    }

    @Override
    public void reuse() throws IOException {
        if (shutdown)
            throw new IOException("Stream closed.");
        hsDone = false;
        rBuf.clear();
        engine.reset();
    }
}
