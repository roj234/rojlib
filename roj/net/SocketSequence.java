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

import roj.concurrent.OperationDone;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.IntStream;

/**
 * @author Roj234
 * @since  2021/2/4 15:43
 */
public final class SocketSequence implements CharSequence {
    private final boolean async;
    private WrappedSocket ch;
    private long dead;
    private int max;

    public SocketSequence(boolean async) {
        this.async = async;
    }

    public SocketSequence init(WrappedSocket ch, int timeout, int max) {
        if (!async) dead = System.currentTimeMillis() + (timeout <= 0 ? 5000 : timeout);
        else dead = Long.MAX_VALUE;
        this.ch = ch;
        this.max = max <= 0 ? Integer.MAX_VALUE : max;
        return this;
    }

    public void release() {
        this.ch = null;
    }

    @Override
    public int length() {
        return max;
    }

    @Override
    public char charAt(int index) {
        fill(index + 1);
        return (char) ch.buffer().get(index);
    }

    private void fill(int want) {
        if(max <= 0) return;

        ByteBuffer buf = ch.buffer();
        try {
            while (buf.position() < want) {
                if (System.currentTimeMillis() > dead) {
                    throw new IOException("timeout");
                }

                if(buf.position() > max) {
                    throw new IOException("overflow");
                }

                int read = ch.read();
                if (read == 0) {
                    if(async) throw OperationDone.INSTANCE;
                    LockSupport.parkNanos(10000);
                } else if (read < 0) {
                    if(read == -1) {
                        max = buf.position();
                        return;
                    }
                    throw new IOException("read() got " + read);
                }
            }
        } catch (IOException e) {
            Helpers.athrow(e);
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
        ByteBuffer bb = ch.buffer();
        char[] data = new char[bb.position()];
        for (int i = 0; i < data.length; i++) {
            data[i] = (char) bb.get(i);
        }
        return new String(data);
    }
}
