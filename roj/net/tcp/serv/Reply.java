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
package roj.net.tcp.serv;

import roj.net.tcp.serv.response.HTTPResponse;
import roj.net.tcp.util.Action;
import roj.net.tcp.util.Code;
import roj.net.tcp.util.Shared;
import roj.net.tcp.util.WrappedSocket;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.ByteWriter;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Objects;

public class Reply implements Response {
    @Nonnull
    private final Code         code;
    @Nonnull
    private final HTTPResponse response;
    private final boolean headerOnly;

    public Reply(Code code, HTTPResponse response) {
        this(code, response, -1);
    }

    public Reply(Code code, HTTPResponse response, int action) {
        this.code = Objects.requireNonNull(code, "code");
        this.response = Objects.requireNonNull(response, "response");
        this.headerOnly = (action == Action.HEAD);
    }

    private ByteList buf = null;

    protected ByteList headers() {
        Object[] data = Shared.SYNC_BUFFER.get();

        CharList header = (CharList) data[2];
        header.ensureCapacity(100);
        header.clear();

        try {
            response.writeHeader(header.append("HTTP/1.1 ").append(code.toString()).append(CRLF)
                    .append("Server: Async/1.2").append(CRLF));

            ByteList bl = new ByteList(header.append(CRLF).length());

            ByteWriter.writeUTF(bl, header, -1);

            return bl;
        } finally {
            if (header.arrayLength() > Shared.MAX_CHAR_BUFFER_CAPACITY)
                data[0] = new CharList(Shared.MAX_CHAR_BUFFER_CAPACITY);
            else
                header.clear();
        }
    }

    public void prepare() throws IOException {
        response.prepare();
        if (buf == null)
            buf = headers();
        else
            buf.rewrite();
    }

    public boolean send(WrappedSocket channel) throws IOException {
        if (buf == null)
            throw new IllegalStateException();

        if (buf.remaining() > 0) {
            if (channel.write(buf) <= 0)
                return true;
        }

        if (!headerOnly) {
            if (response.send(channel))
                return true;
        }

        return !channel.dataFlush();
    }

    public void release() throws IOException {
        response.release();
    }
}
