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
package roj.net.misc;

import roj.util.EmptyArrays;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.locks.LockSupport;

/**
 * @author Roj233
 * @version 0.1
 * @since 2021/12/30 12:31
 */
public final class PacketBuffer {
    static final AtomicIntegerFieldUpdater<PacketBuffer> CAS = AtomicIntegerFieldUpdater.newUpdater(PacketBuffer.class, "barrier");

    public PacketBuffer() {
        this(10);
    }

    public PacketBuffer(int capacity) {
        buffers = new Buf[capacity];
        for (int i = 0; i < buffers.length; i++) {
            buffers[i] = new Buf();
        }
    }

    private volatile Buf[] buffers;
    private volatile int barrier;

    public boolean take(ByteBuffer b) {
        for(;;) {
            int bar = barrier;
            if (bar == 0) return false;
            if (bar > 0 && CAS.compareAndSet(this, bar--, -1)) {
                Buf h = buffers[bar];
                if (b.remaining() < h.len) {
                    bar++;
                } else {
                    b.put(h.data, 0, h.len);
                    h.len = 0;
                }
                if (!CAS.compareAndSet(this, -1, bar)) {
                    barrier = 0;
                    throw new IllegalStateException();
                }
                return h.len == 0;
            }
            LockSupport.parkNanos(5);
        }
    }

    public byte[] poll() {
        for(;;) {
            int bar = barrier;
            if (bar == 0) return null;
            if (bar > 0 && CAS.compareAndSet(this, bar--, -1)) {
                Buf h = buffers[bar];
                byte[] b = Arrays.copyOf(h.data, h.len);
                if (!CAS.compareAndSet(this, -1, bar)) {
                    barrier = 0;
                    throw new IllegalStateException();
                }
                return b;
            }
            LockSupport.parkNanos(5);
        }
    }

    public void offer(ByteBuffer b) {
        while (!offerOnce(b)) {
            LockSupport.parkNanos(5);
        }
    }

    public boolean offerOnce(ByteBuffer b) {
        int bar = barrier;
        if (bar == buffers.length) {
            Buf[] buf = buffers;
            if (CAS.compareAndSet(this, bar, -3)) {
                if (buf == buffers) {
                    Buf[] newBuffers = new Buf[buffers.length + 10];
                    System.arraycopy(buffers, 0, newBuffers, 0, buffers.length);
                    buffers = newBuffers;
                }
                if (!CAS.compareAndSet(this, -3, bar)) {
                    barrier = bar;
                    throw new IllegalStateException();
                }
            } else {
                return false;
            }
        }
        if (bar >= 0 && CAS.compareAndSet(this, bar, -2)) {
            Buf h = buffers[bar];
            if (h.data.length < (h.len = b.remaining()))
                h.data = new byte[h.len];
            b.get(h.data, 0, h.len);
            if (!CAS.compareAndSet(this, -2, bar + 1)) {
                barrier = bar;
                throw new IllegalStateException();
            }
            return true;
        }
        return false;
    }

    static final class Buf {
        byte[] data = EmptyArrays.BYTES;
        int len;
    }
}
