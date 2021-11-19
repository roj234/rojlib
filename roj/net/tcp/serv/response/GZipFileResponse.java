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

import roj.io.ByteBufferOutputStream;
import roj.net.NetworkUtil;
import roj.net.tcp.serv.util.ReusableGZOutput;
import roj.net.tcp.util.Shared;
import roj.net.tcp.util.WrappedSocket;
import roj.text.CharList;
import roj.util.ByteList;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.Deflater;

/**
 * @author Roj234
 * @version 0.1
 * @since  2020/12/5 18:31
 */
public class GZipFileResponse extends FileResponse {
    ReusableGZOutput gz;
    final ByteList tmp = new ByteList();
    final byte[]   hex = new byte[8];

    public GZipFileResponse(File absolute) {
        super(absolute);
        gz = new ReusableGZOutput(Shared.WRITE_MAX, Deflater.DEFAULT_COMPRESSION);
    }

    @Override
    public void prepare() throws IOException {
        super.prepare();
        gz.reset(new ByteBufferOutputStream(null));
        tmp.ensureCapacity(8192);
    }

    @Override
    public boolean send(WrappedSocket channel) throws IOException {
        if (stream == null)
            throw new IllegalStateException();
        if (eof) return false;

        ByteBuffer buf = channel.buffer();
        if (buf.hasRemaining()) {
            channel.writeDirect(buf);
            return true;
        }

        buf.clear();
        ByteBufferOutputStream out = (ByteBufferOutputStream) gz.getOut();
        out.setBuffer(buf);

        int read = tmp.readStream(stream, 8192);
        tmp.writeToStream(gz);
        tmp.clear();

        if (read < 0) {
            gz.finish();
            eof = true;
        }

        if (buf.position() > 0) {
            byte[] hex = this.hex;
            int off = NetworkUtil.number2hex(buf.position(), hex);
            buf.put(hex, off, 8 - off).put((byte) '\r').put((byte) '\n');

            if (read < 0) {
                buf.put(Shared.END_OF_CHUNK);
            }

            buf.flip();
            channel.writeDirect(buf);
        }

        return read > 0;
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
