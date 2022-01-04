/*
 * This file is a part of MoreItems
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2022 Roj234
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

import roj.collect.RingBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author Roj233
 * @since 2022/1/8 18:22
 */
public class PipelineWrapper implements Pipeline {
    public final RingBuffer<Pipeline> p = new RingBuffer<>(4);

    public void expandAdd(Pipeline pl) {
        if (p.capacity() == p.size()) {
            p.expand(p.capacity() + 10);
        }
        p.addLast(pl);
    }

    private ByteBuffer t0 = ByteBuffer.allocate(512),
            t1 = ByteBuffer.allocate(512);

    @Override
    public int unwrap(ByteBuffer rcv, ByteBuffer dst) throws IOException {
        Object[] ps = p.getArray();
        int i = p.head();
        int fence = p.tail();

        ByteBuffer t = this.t0;
        t.clear();
        int c;

        do {
            Pipeline p = (Pipeline) ps[i];
            c = p.unwrap(rcv, t);
            if (c > 0) return 1;
            if (c < 0) {
                if (t.capacity() >= -c || t == dst) return c;
                if (t == t0) {
                    t = t0 = ByteBuffer.allocate(-c);
                } else {
                    t = t1 = ByteBuffer.allocate(-c);
                }
                continue;
            }

            (rcv = t).flip();

            t = t == t0 ? t1 : t0;
            t.clear();

            if (i++ == ps.length) i = 0;
            if (i == fence - 1) t = dst;
        } while (i != fence);
        return c;
    }

    @Override
    public int wrap(ByteBuffer src, ByteBuffer snd) throws IOException {
        Object[] ps = p.getArray();
        int i = p.tail();
        int fence = p.head();

        ByteBuffer t = this.t0;
        t.clear();
        int c;

        do {
            Pipeline p = (Pipeline) ps[i];
            c = -p.wrap(src, t);
            if (c > 0) {
                if (t.capacity() >= c || t == snd) return -c;
                if (t == t0) {
                    t = t0 = ByteBuffer.allocate(c);
                } else {
                    t = t1 = ByteBuffer.allocate(c);
                }
                continue;
            }

            (src = t).flip();

            t = t == t0 ? t1 : t0;
            t.clear();

            if (i-- == 0) i = ps.length - 1;
            if (i == fence - 1) t = snd;
        } while (i != fence);
        return c;
    }
}
