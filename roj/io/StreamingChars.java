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
import roj.util.ByteList;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.util.stream.IntStream;

/**
 * @author Roj234
 * @since  2021/6/15 20:30
 */
public class StreamingChars implements CharSequence {
    InputStream in;
    protected ByteList buf;
    protected int flag;
    int max = -1;

    public StreamingChars() {
        this.buf = new ByteList(256);
    }

    public StreamingChars(int cap) {
        this.buf = new ByteList(cap);
    }

    public StreamingChars maxReceive(int max) {
        this.max = max;
        return this;
    }

    public StreamingChars reset(InputStream in) {
        buf.clear();
        flag = 0;
        this.in = in;
        this.max = Integer.MAX_VALUE;
        return this;
    }

    @Override
    public int length() {
        return (flag & 1) != 0 ? buf.length() : Integer.MAX_VALUE;
    }

    @Override
    public char charAt(int index) {
        ensureRead(index + 1);
        if (index >= buf.length()) return '\0';
        return buf.charAt(index);
    }

    public void ensureRead(int required) {
        if((flag & 1) != 0) return;
        ByteList buf = this.buf;
        if (buf.length() >= required) return;
        try {
            int r = buf.readStream(in, MathUtils.clamp(in.available(), 128, 4096));
            if (r < 0) {
                if(r == -1) {
                    flag |= 1;
                    return;
                }
                throw new IOException("in.read() got " + r);
            }

            if(buf.wIndex() > max) {
                throw new IOException("Buffer pos " + buf.wIndex() + " > " + max);
            }
        } catch (IOException e) {
            flag |= 1;
            Helpers.athrow(e);
        }
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        ensureRead(end + 1);
        return buf.subSequence(start, end);
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
        return buf.readAscii(0, buf.wIndex());
    }
}
