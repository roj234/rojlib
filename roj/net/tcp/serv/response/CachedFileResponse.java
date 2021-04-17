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

import roj.net.tcp.serv.util.ReusableGZOutput;
import roj.net.tcp.util.Shared;
import roj.net.tcp.util.WrappedSocket;
import roj.text.CharList;
import roj.util.ByteList;

import java.io.File;
import java.io.IOException;
import java.net.URI;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/12/5 18:31
 */
public class CachedFileResponse extends FileResponse {
    ByteList buf = new ByteList();

    public CachedFileResponse(File absolute) {
        super(absolute);
    }

    public CachedFileResponse(URI relative) {
        super(relative);
    }

    @Override
    public void prepare() throws IOException {
        if (file.length() == 0)
            throw new IllegalArgumentException("file is empty");
        if (buf.pos() == 0) {
            super.prepare();
            ReusableGZOutput gz = new ReusableGZOutput(buf.asOutputStream(), Shared.WRITE_MAX, 5);

            ByteList buf = new ByteList(Math.min(Shared.WRITE_MAX, stream.available()));
            int delta;
            do {
                delta = buf.readStreamArray(stream, Shared.WRITE_MAX);
                buf.writeToStream(gz);
                buf.clear();
            } while (delta > 0);

            gz.finish();
            gz.close();

            this.buf.trimToSize();
            super.release();
        }
        buf.rewrite();
    }

    int wrote;

    @Override
    public boolean send(WrappedSocket channel) throws IOException {
        if (buf == null)
            throw new IllegalStateException("Not prepared");
        wrote += channel.write(buf);

        return buf.remaining() > 0;
    }

    @Override
    public void release() {
    }

    @Override
    public void writeHeader(CharList list) {
        list.append("Content-Disposition: attachment; filename=\"" + file.getName() + '"').append(CRLF)
                .append("Content-Type: ").append("application/octet-stream").append(CRLF)
                .append("Content-Length: " + this.buf.pos()).append(CRLF)
                .append("Content-Encoding: gzip").append(CRLF);
    }
}
