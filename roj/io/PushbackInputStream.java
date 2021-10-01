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
package roj.io;

import javax.annotation.Nonnull;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Your description here
 *
 * @author solo6975
 * @version 0.1
 * @since 2021/10/1 21:39
 */
public class PushbackInputStream extends FilterInputStream {
    byte[] buffer;
    int    bOff;
    int    bLen;

    public PushbackInputStream(InputStream in) {
        super(in);
    }

    public void setBuffer(@Nonnull byte[] b, int off, int len) {
        this.buffer = b;
        this.bOff = off;
        this.bLen = len;
    }

    @Override
    public int read(@Nonnull byte[] b, int off, int len) throws IOException {
        int k = bOff;
        if (k < bLen) {
            int rm = bLen - k;
            if (len <= rm) {
                if(len == rm) {
                    bOff = bLen = 0;
                } else {
                    bOff = k + len;
                }
                System.arraycopy(buffer, k, b, off, len);
                return len;
            } else {
                bOff = bLen = 0;
                System.arraycopy(buffer, k, b, off, rm);
                int read = in.read(b, off + rm, len - rm);
                return (read > 0 ? read : 0) + rm;
            }
        } else {
            return in.read(b, off, len);
        }
    }

    @Override
    public int read() throws IOException {
        if (bOff < bLen) {
            byte b = buffer[bOff++];
            if(bOff == bLen)
                bOff = bLen = 0;
            return b;
        } else {
            return in.read();
        }
    }

    @Override
    public int available() throws IOException {
        return in.available() + bLen - bOff;
    }

    public int getBufferPos() {
        return bOff;
    }
}
