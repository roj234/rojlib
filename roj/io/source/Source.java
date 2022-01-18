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
package roj.io.source;

import roj.util.ByteList;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.channels.FileChannel;

/**
 * @author Roj233
 * @since 2021/8/18 13:36
 */
public interface Source extends Closeable, AutoCloseable {
    void seek(long pos) throws IOException;
    default long skip(long amount) throws IOException {
        long position = position();
        long pos = Math.min(position + amount, length());
        seek(pos);
        return pos - position;
    }
    default int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }
    int read(byte[] b, int off, int len) throws IOException;
    default void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }
    void write(byte[] b, int off, int len) throws IOException;
    long position() throws IOException;
    void setLength(long length) throws IOException;
    long length() throws IOException;

    default void readFully(byte[] b, int off, int len) throws IOException {
        do {
            int n = read(b, off, len);
            if (n < 0) throw new EOFException();
            off += n;
            len -= n;
        } while (len > 0);
    }

    default void read(ByteList buf, int length) throws IOException {
        buf.ensureCapacity(length);
        readFully(buf.list, 0, length);
    }

    default FileChannel channel() {
        return null;
    }

    default void reopen() throws IOException, UnsupportedOperationException {
        throw new UnsupportedEncodingException();
    }
}
