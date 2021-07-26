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
import roj.net.tcp.serv.util.Notify;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.ByteReader;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.IntStream;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2021/2/4 15:43
 */
public class StreamLikeSequence implements CharSequence {
    CharList cl;
    WrappedSocket socket;
    long timeout;
    int bufOff, maxRecv;
    boolean async;

    public StreamLikeSequence(int initialCapacity, boolean async) {
        this.cl = new CharList(initialCapacity);
        this.async = async;
    }

    public StreamLikeSequence init(WrappedSocket socket, Router router) {
        return init(socket, router.readTimeout(), router.maxLength());
    }

    public StreamLikeSequence init(WrappedSocket socket, long timeout, int maxRecv) {
        this.timeout = System.currentTimeMillis() + timeout;
        this.socket = socket;
        this.bufOff = 0;
        this.maxRecv = maxRecv;
        return this;
    }

    public void release() {
        if (cl.length() > SharedConfig.MAX_CHAR_BUFFER_CAPACITY) {
            cl = new CharList();
        } else {
            cl.clear();
        }
        this.socket = null;
    }

    @Override
    public int length() {
        return bufOff == -1 ? cl.length() : Integer.MAX_VALUE;
    }

    @Override
    public char charAt(int index) {
        ensureLength(index + 1);
        return cl.charAt(index);
    }

    private void ensureLength(int required) {
        int start = bufOff;
        if(start == -1)
            return;
        ByteList buf = socket.buffer();
        try {
            int read;
            while (cl.length() < required) {
                read = socket.read();
                if (read >= 0) {
                    if (read > 0) {
                        start += ByteReader.decodeUTFPartialExternal(start, -1, cl, buf);
                    }

                    LockSupport.parkNanos(1);
                } else {
                    if(read == -1) {
                        bufOff = -1;
                        return;
                    }
                    throw new Notify(new IOException("socket.read() got " + read));
                }

                if(buf.pos() > maxRecv) {
                    throw new Notify(-127);
                }

                if (System.currentTimeMillis() > timeout) {
                    throw new Notify(-128);
                }

                if(cl.length() < required && async) {
                    throw OperationDone.INSTANCE;
                }
            }
        } catch (IOException e) {
            throw new Notify(e);
        } finally {
            bufOff = start;
        }
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        ensureLength(end + 1);
        return cl.subSequence(start, end);
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
        return cl.toString();
    }
}
