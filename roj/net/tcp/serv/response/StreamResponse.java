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
package roj.net.tcp.serv.response;

import roj.net.tcp.util.WrappedSocket;

import java.io.IOException;
import java.io.InputStream;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2021/2/16 11:21
 */
public abstract class StreamResponse implements HTTPResponse {
    protected InputStream stream = null;
    protected long length, position = -1;

    public void prepare() throws IOException {
        if (stream == null) {//stream.close();
            stream = getStream();
            position = 0;
        }
    }

    protected abstract InputStream getStream() throws IOException;

    public boolean send(WrappedSocket channel) throws IOException {
        if (stream == null) throw new IllegalStateException();
        long pos = position;
        if (pos < 0) throw new IllegalStateException();

        if (pos >= length) {
            return false;
        }

        pos += channel.write(stream, length - pos);

        return (position = pos) < length;
    }

    public void release() throws IOException {
        if (stream != null) {
            stream.close();
            stream = null;
            position = -1;
        }
    }
}
