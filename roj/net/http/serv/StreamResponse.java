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
package roj.net.http.serv;

import roj.net.WrappedSocket;
import roj.net.http.Shared;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author Roj234
 * @since  2021/2/16 11:21
 */
public abstract class StreamResponse implements Response {
    protected InputStream stream = null;
    protected boolean eof;

    public void prepare() throws IOException {
        if (stream == null) {
            stream = getStream();
            eof = false;
        }
    }

    protected abstract InputStream getStream() throws IOException;

    public boolean send(WrappedSocket channel) throws IOException {
        if (stream == null) throw new IllegalStateException();
        if (eof) return false;

        int transfer = channel.write(stream, Shared.WRITE_MAX);
        if (transfer < 0)
            eof = true;

        return !eof;
    }

    public void release() throws IOException {
        if (stream != null) {
            stream.close();
            stream = null;
            eof = true;
        }
    }
}
