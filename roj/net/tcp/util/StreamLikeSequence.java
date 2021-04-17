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

import roj.concurrent.OperationDone;
import roj.net.tcp.serv.Router;
import roj.util.ByteList;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.IntStream;

/**
 * @author Roj234
 * @version 0.1
 * @since  2021/2/4 15:43
 */
public class StreamLikeSequence implements CharSequence {
    private final boolean async;

    WrappedSocket socket;
    long time;
    int maxRecv;
    boolean eof;

    public StreamLikeSequence(boolean async) {
        this.async = async;
    }

    public StreamLikeSequence init(WrappedSocket socket, Router router) {
        return init(socket, router.readTimeout(), router.maxLength());
    }

    public StreamLikeSequence init(WrappedSocket socket, int timeout, int maxRecv) {
        if (!async || time == 0)
            this.time = System.currentTimeMillis() + timeout;
        this.socket = socket;
        this.eof = false;
        this.maxRecv = maxRecv;
        return this;
    }

    public void release() {
        this.socket = null;
    }

    @Override
    public int length() {
        return maxRecv;
    }

    @Override
    public char charAt(int index) {
        fill(index + 1);
        return (char) socket.buffer().get(index);
    }

    private void fill(int want) {
        if(eof) return;
        ByteList buf = socket.buffer();
        try {
            while (buf.pos() < want) {
                if (System.currentTimeMillis() > time) {
                    throw new Notify(-128);
                }

                if(buf.pos() > maxRecv) {
                    throw new Notify(-127);
                }

                int read = socket.read();
                if (read == 0) {
                    if(async) throw OperationDone.INSTANCE;
                    LockSupport.parkNanos(20);
                } else if (read < 0) {
                    if(read == -1) {
                        eof = true;
                        return;
                    }
                    throw new Notify(new IOException("socket.read() got " + read));
                }
            }
        } catch (IOException e) {
            throw new Notify(e);
        }
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        throw new UnsupportedOperationException();
    }

    @Override
    public IntStream chars() {
        throw new UnsupportedOperationException();
    }

    @Override
    public IntStream codePoints() {
        throw new UnsupportedOperationException();
    }

    @Nonnull
    @Override
    public String toString() {
        return socket.buffer().getString();
    }
}
