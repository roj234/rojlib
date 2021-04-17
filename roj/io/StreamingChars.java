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
package roj.io;

import roj.math.MathUtils;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.ByteReader;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.IntStream;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2021/6/15 20:30
 */
public class StreamingChars implements CharSequence {
    InputStream in;
    ByteList buffer;
    public    CharList cl;
    protected int      bufOff;
    int max = -1;

    public StreamingChars() {
        this.cl = new CharList(128);
        this.buffer = new ByteList(256);
    }

    public StreamingChars(int initialCapacity) {
        this.cl = new CharList(initialCapacity >> 1);
        this.buffer = new ByteList(initialCapacity);
    }

    public StreamingChars maxReceive(int max) {
        this.max = max;
        return this;
    }

    public StreamingChars reset(InputStream in) {
        buffer.clear();
        cl.clear();
        bufOff = 0;
        this.in = in;
        this.max = Integer.MAX_VALUE;
        return this;
    }

    @Override
    public int length() {
        return bufOff == -1 ? cl.length() : Integer.MAX_VALUE;
    }

    @Override
    public char charAt(int index) {
        ensureRead(index + 1);
        return cl.charAt(index);
    }

    public void ensureRead(int required) {
        int start = bufOff;
        if(start < 0)
            return;
        ByteList buf = this.buffer;
        try {
            int read;
            while (cl.length() < required) {
                read = buffer.readStreamArray(in, MathUtils.clamp(in.available(), 128, 4096));
                if (read >= 0) {
                    if (read > 0) {
                        start += ByteReader.decodeUTFPartialExternal(start, -1, cl, buf);
                    }

                    LockSupport.parkNanos(50);
                } else {
                    if(read == -1) {
                        start = -1;
                        return;
                    }
                    throw new IOException("in.read() got " + read);
                }

                if(buf.pos() > max) {
                    throw new IOException("Buffer read " + buf.pos() + " which is over the limitation " + max);
                }
            }
        } catch (IOException e) {
            Helpers.throwAny(e);
        } finally {
            bufOff = start;
        }
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        ensureRead(end + 1);
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
