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


import roj.crypt.CipheR;
import roj.io.NIOUtil;
import roj.net.mss.MSSEngine;
import roj.net.mss.MSSEngineClient;
import roj.net.mss.MSSEngineFactory;
import roj.net.mss.MSSException;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.ByteBuffer;

/**
 * My Secure Socket
 */
public class MSSSocket extends PlainSocket {
    // A RSA public key is ~900B in X.509 format
    // Hoping expandNetOut would never be called
    private static final int BUFFER_CAPACITY = 1;

    private static MSSEngineFactory alloc;
    public static void setDefaultAllocator(MSSEngineFactory factory) {
        alloc = factory;
    }
    public static MSSEngineFactory getDefaultAllocator() {
        return alloc;
    }

    private final MSSEngine engine;

    private ByteBuffer outCopy;

    public MSSSocket(Socket sc, FileDescriptor fd, MSSEngine server) {
        super(sc, fd);

        engine = server;

        hsOut = ByteBuffer.allocateDirect(BUFFER_CAPACITY);
        outCopy = hsOut.duplicate();
        hsOut.limit(0);
    }

    public MSSSocket(Socket sc, FileDescriptor fd) {
        this(sc, fd, alloc == null ? new MSSEngineClient() : alloc.newEngine());
    }

    // region handshake

    private ByteBuffer hsOut;

    private void expandNetOut(int _size) {
        ByteBuffer bb = ByteBuffer.allocateDirect(_size);
        bb.put(hsOut);
        NIOUtil.clean(hsOut);
        outCopy = bb.duplicate();
        bb.flip();
        hsOut = bb;
    }

    @SuppressWarnings("fallthrough")
    public boolean handShake() throws IOException {
        if (hsOut == null) {
            return true;
        }

        if (hsOut.hasRemaining()) {
            super.write(hsOut);
            if (hsOut.hasRemaining()) {
                return false;
            }

            if (engine.isHandshakeDone()) hsDone();
            return hsOut == null;
        }

        if (super.read(rBuf.remaining() + 1) < 0) {
            engine.close(null);
            return hsOut == null;
        }

        needIO:
        do {
            rBuf.flip();
            int result = engine.handshake(outCopy, rBuf);
            rBuf.compact();

            switch (result) {
                case MSSEngine.HS_OK:
                    if (outCopy.position() > 0) {
                        hsOut.position(0).limit(outCopy.position());
                        outCopy.clear();
                        super.write(hsOut);
                        break needIO;
                    }
                    if (engine.getHandShakeStatus() == MSSEngine.HS_FINISHED) {
                        break needIO;
                    }
                    break;
                case MSSEngine.BUFFER_UNDERFLOW:
                    result = engine.getBufferSize();
                    if (result > rBuf.capacity()) {
                        expandReadBuffer(result);
                    }

                    break needIO;
                case MSSEngine.BUFFER_OVERFLOW:
                    expandNetOut(outCopy.capacity() + engine.getBufferSize());
                    break;
            }
        } while (true);
        if (engine.getHandShakeStatus() == MSSEngine.HS_FINISHED) {
            if (!hsOut.hasRemaining()) hsDone();
        }

        return hsOut == null;
    }

    private void hsDone() throws MSSException {
        NIOUtil.clean(hsOut);
        hsOut = null;

        engine.checkError();

        outCopy = rBuf.duplicate();

        d = engine.getDecoder();
        e = engine.getEncoder();

        byte[] preFlight = engine.getPreflightData();
        if (preFlight != null) rBuf.put(preFlight);

        outCopy.position(0).limit(rBuf.position());
        rBuf.clear();
        try {
            d.crypt(outCopy, rBuf);
        } catch (Throwable e) {
            try {
                close();
            } catch (IOException ignored) {}
            throw new MSSException("Cipher fault", e);
        }
    }

    // endregion

    private CipheR d, e;

    public CipheR getDecrypter() {
        return d;
    }

    public CipheR getEncrypter() {
        return e;
    }

    public MSSEngine getEngine() {
        return engine;
    }

    public int read(int max) throws IOException {
        if (hsOut != null) throw new MSSException("Not handshake");

        int begin = rBuf.position();
        int nread = super.read(max);
        if (nread <= 0) return nread;

        if (!NIOUtil.directBufferEquals(outCopy, rBuf))
            outCopy = rBuf.duplicate();
        outCopy.position(begin).limit(rBuf.position());
        rBuf.position(begin);
        try {
            d.crypt(outCopy, rBuf);
        } catch (Throwable e) {
            try {
                close();
            } catch (IOException ignored) {}
            throw new MSSException("Cipher fault", e);
        }
        return nread;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        if (hsOut != null) throw new MSSException("Not handshake");
        if (!dataFlush()) return 0;

        int cap = Math.min(WRITE_ONCE, src.remaining());
        if (wBuf.capacity() < cap) wBuf = ByteBuffer.allocate(cap);
        else wBuf.clear();

        int pos = src.position();
        int lim = src.limit();
        src.limit(src.position() + cap);
        try {
            e.crypt(src, wBuf);
        } catch (Throwable e) {
            src.position(pos);
            wBuf.position(0).limit(0);
            throw new MSSException("Cipher fault", e);
        } finally {
            src.limit(lim);
        }
        wBuf.flip();

        dataFlush();
        return src.position() - pos;
    }

    @Override
    public int write(InputStream src, int max) throws IOException {
        if (hsOut != null) throw new MSSException("Not handshake");
        if (!dataFlush()) return 0;

        int cap = Math.min(WRITE_ONCE, max) << 1;
        if (wBuf.capacity() < cap) wBuf = ByteBuffer.allocate(cap);
        else wBuf.clear();
        int len = src.read(wBuf.array(), 0, cap >> 1);
        if (len <= 0) return len;
        wBuf.limit(len);

        try {
            ByteBuffer clone = wBuf.duplicate();
            clone.position(len).limit(clone.capacity());
            e.crypt(wBuf, clone);
            wBuf.position(len).limit(clone.position());
        } catch (Throwable e) {
            wBuf.position(0).limit(0);
            throw new MSSException("Cipher fault", e);
        }

        dataFlush();
        return len;
    }

    public boolean shutdown() throws IOException {
        if (outCopy != null) {
            engine.close(null);
            outCopy = null;
        }
        return dataFlush();
    }

    @Override
    public void close() throws IOException {
        super.close();
        if (hsOut != null) {
            NIOUtil.clean(hsOut);
            hsOut = null;
        }

        if (outCopy != null) {
            engine.close(null);
            outCopy = null;
        }
    }

    @Override
    public void reset() throws IOException {
        if (outCopy == null)
            throw new IOException("Socket closed.");
        if (hsOut != null) {
            hsOut.position(0).limit(0);
            outCopy.clear();
            engine.reset();
        } else {
            e.setKey(engine._getSharedKey(), CipheR.ENCRYPT);
            d.setKey(engine._getSharedKey(), CipheR.DECRYPT);
        }
        wBuf.position(0).limit(0);
        rBuf.clear();
    }
}
