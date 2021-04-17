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
package roj.net.tcp.client;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;

/**
 * 处理不同传输方式
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/12/5 15:30
 */
class HttpInputStream {
    public static InputStream create(HttpHeader header, SocketInputStream sin) throws IOException {
        InputStream in = sin;

        String TE = header.headers.get("Transfer-Encoding");
        if ("chunked".equalsIgnoreCase(TE)) {
            in = new ChunkedInputStream(sin, header.headers);
        }

        String CE = (header.headers.getOrDefault("Content-Encoding", "identity")).toLowerCase();
        switch (CE) {
            case "gzip":
                in = new GZIPInputStream(in);
            break;
            case "deflate":
                in = check(in);
            break;
            case "compress":
                // br ... 要搞一堆库，算了
            case "br":
                throw new UnsupportedOperationException("Unsupported encoding " + CE);
        }
        return in;
    }

    private static InputStream check(InputStream in) throws IOException {
        in.mark(3);
        byte[] flags = new byte[2];
        if (in.read(flags) < 2) {
            throw new EOFException();
        }
        in.reset();

        // 检测Zlib Header
        boolean wrap = false;
        // CM
        if ((flags[0] & 0xF) == 0x8) {
            // CINFO
            if ((flags[0] >>> 4 & 0xF) <= 7) {
                // FCHECK
                if ((flags[1] & 0x1F) == (flags[0] << 8 | flags[1]) % 31) {
                    wrap = true;
                }
            }
        }
        return new DeflateInputStream(in, new Inflater(wrap));
    }
}
