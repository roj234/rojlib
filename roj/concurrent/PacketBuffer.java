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
package roj.concurrent;

import roj.util.EmptyArrays;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.LockSupport;

/**
 * @author Roj233
 * @since 2021/12/30 12:31
 */
public final class PacketBuffer {
    static final AtomicIntegerFieldUpdater<PacketBuffer> BAR = AtomicIntegerFieldUpdater.newUpdater(PacketBuffer.class, "barrier");
    static final AtomicReferenceFieldUpdater<Buf, Thread> OWN = AtomicReferenceFieldUpdater.newUpdater(Buf.class, Thread.class, "owner");
    //static final AtomicReferenceFieldUpdater<Buf, Buf> NEXT = AtomicReferenceFieldUpdater.newUpdater(Buf.class, Buf.class, "next");
    //static final AtomicReferenceFieldUpdater<PacketBuffer, Buf> TAIL = AtomicReferenceFieldUpdater.newUpdater(PacketBuffer.class, Buf.class, "tail");

    public PacketBuffer() {
        this(false, 10);
    }

    public PacketBuffer(boolean fifo, int capacity) {
        if (fifo) {
            head = new Buf();
        } else {
            head = null;
            buffers = new Buf[capacity];
            for (int i = 0; i < buffers.length; i++) {
                buffers[i] = new Buf();
            }
        }
    }

    public boolean hasMore() {
        return barrier > 0;
    }

    private volatile Buf[] buffers;
    private volatile int   barrier;
    private final    Buf   head;
    private          Buf   tail;

    public boolean isFIFO() {
        return head != null;
    }

    public boolean take(ByteBuffer b) {
        for(;;) {
            int bar = barrier;
            if (bar == 0) return false;
            if (bar > 0 && BAR.compareAndSet(this, bar--, -1)) {
                Buf h = buffers != null ? buffers[bar] : head.next;

                if (b.remaining() < h.len) {
                    bar++;
                } else {
                    b.put(h.data, 0, h.len);
                    h.len = 0;
                    if (buffers == null) syncUnlink();
                    LockSupport.unpark(OWN.getAndSet(h, null));
                }

                if (!BAR.compareAndSet(this, -1, bar)) {
                    barrier = 0;
                    throw new IllegalStateException();
                }
                return h.len == 0;
            }
        }
    }

    public byte[] poll() {
        for(;;) {
            int bar = barrier;
            if (bar == 0) return null;
            if (bar > 0 && BAR.compareAndSet(this, bar--, -1)) {
                Buf h;
                byte[] b;
                if (buffers == null) {
                    h = syncUnlink();
                    b = h.data;
                } else {
                    h = buffers[bar];
                    b = Arrays.copyOf(h.data, h.len);
                }

                LockSupport.unpark(OWN.getAndSet(h, null));

                if (!BAR.compareAndSet(this, -1, bar)) {
                    barrier = 0;
                    throw new IllegalStateException();
                }
                return b;
            }
        }
    }

    private Buf syncUnlink() {
        Buf next = head.next;
        if (next == null) return null;
        if (next == tail) tail = null;
        head.next = next.next;
        next.next = null;
        return next;
    }

    public final void offer(ByteBuffer b) {
        while (!offerOnce(b, false)) {
            LockSupport.parkNanos(1000);
        }
    }

    public final void offerAndAwait(ByteBuffer b) {
        while (!offerOnce(b, true)) {
            LockSupport.parkNanos(1000);
        }
    }

    public boolean offerOnce(ByteBuffer b, boolean await) {
        int bar = barrier;
        if (buffers != null && bar == buffers.length) {
            Buf[] buf = buffers;
            if (BAR.compareAndSet(this, bar, -1)) {
                if (buf == buffers) {
                    Buf[] newBuffers = new Buf[buffers.length + 10];
                    System.arraycopy(buffers, 0, newBuffers, 0, buffers.length);
                    buffers = newBuffers;
                }
                if (!BAR.compareAndSet(this, -1, bar)) {
                    barrier = bar;
                    throw new IllegalStateException();
                }
            } else {
                return false;
            }
        }

        // FIFO也许可以不要用独占锁...
        if (bar >= 0 && BAR.compareAndSet(this, bar, -1)) {
            Buf h;
            if (buffers != null) {
                h = buffers[bar];
                if (h.data.length < (h.len = b.remaining()))
                    h.data = new byte[h.len];
            } else {
                // already synchronized
                Buf prevTail = this.tail;
                this.tail = h = new Buf();
                if (prevTail != null) prevTail.next = h;
                else head.next = h;
                h.data = new byte[h.len = b.remaining()];
            }
            b.get(h.data, 0, h.len);
            h.owner = await ? Thread.currentThread() : null;
            if (!BAR.compareAndSet(this, -1, bar + 1)) {
                barrier = bar;
                throw new IllegalStateException();
            }
            if (await) {
                if (h.owner == Thread.currentThread()) {
                    LockSupport.park(this);
                }
            }
            return true;
        }
        return false;
    }

    static final class Buf {
        byte[] data = EmptyArrays.BYTES;
        int len;
        volatile Thread owner;

        /*volatile */Buf next;
    }
}
