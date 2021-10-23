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

import roj.net.NetworkUtil;
import roj.net.tcp.serv.util.ReusableGZOutput;
import roj.net.tcp.util.Shared;
import roj.net.tcp.util.WrappedSocket;
import roj.text.CharList;
import roj.util.ByteList;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.zip.Deflater;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/12/5 18:31
 */
public class GZipFileResponse extends FileResponse {
    ReusableGZOutput gz;
    final ByteList zipped = new ByteList();
    final byte[] hex = new byte[10];

    public GZipFileResponse(File absolute) {
        super(absolute);
    }

    public GZipFileResponse(URI relative) {
        super(relative);
    }

    @Override
    public void prepare() throws IOException {
        super.prepare();

        if (this.gz == null) {
            this.gz = new ReusableGZOutput(zipped.asOutputStream(), Shared.WRITE_MAX, Deflater.DEFAULT_COMPRESSION);

            zipped.ensureCapacity(Math.min(Shared.WRITE_MAX, stream.available()));

            hex[8] = '\r';
            hex[9] = '\n';
        }
    }

    @Override
    public boolean send(WrappedSocket channel) throws IOException {
        if (stream == null)
            throw new IllegalStateException();
        long pos = position;
        if (pos < 0)
            throw new IllegalStateException();

        if (pos >= length) {
            return false;
        }

        ByteList buf = channel.buffer();
        buf.clear();

        long delta = buf.readStreamArray(stream, Shared.WRITE_MAX);
        buf.writeToStream(gz);
        buf.clear();

        pos += delta;

        boolean undone = (position = pos) < length;

        if (!undone) {
            gz.finish();
        }

        if ((buf = this.zipped).pos() > 0) {
            byte[] hex = this.hex;
            int off = NetworkUtil.number2hex(buf.pos(), hex);

            channel.write(new ByteList.ReadOnlySubList(hex, off, 10 - off));

            buf.addAll(hex, 8, 2);

            if (!undone) {
                buf.addAll(Shared.END_OF_CHUNK);
            }

            channel.write(buf);
            buf.clear();
        }

        return undone;
    }

    @Override
    public void release() throws IOException {
        super.release();
        gz.reset(null);
    }

    @Override
    public void writeHeader(CharList list) {
        super.writeHeader(list);
        list.append("Content-Disposition: attachment; filename=\"" + file.getName() + '"').append(CRLF)
                .append("Transfer-Encoding: chunked").append(CRLF)
                .append("Content-Encoding: gzip").append(CRLF);
    }
}
