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

import roj.io.IOUtil;
import roj.math.MathUtils;
import roj.net.WrappedSocket;
import roj.net.http.Code;
import roj.net.http.Headers;
import roj.text.ACalendar;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.FastThreadLocal;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import java.util.zip.Deflater;

public class Reply {
    public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
    public static final ACalendar        RFC_DATE    = new ACalendar(TimeZone.getTimeZone("GMT"));

    static final class Local {
        ACalendar date = RFC_DATE.copy();
        Deflater def = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
    }
    static final FastThreadLocal<Local> LocalShared = FastThreadLocal.withInitial(Local::new);

    static final String CRLF = "\r\n";

    private final ByteList hdr;
    private       Response response;
    private final Headers headers;
    private       boolean noKeepAlive;

    public Reply(int code) {
        this.hdr = new ByteList(128);
        this.hdr.putAscii("HTTP/1.1 ").putAscii(Integer.toString(code)).put((byte) ' ')
                .putAscii(Code.getDescription(code)).putAscii("\r\nServer: Async/2.0\r\nConnection: keep-alive" + "\r\n");
        this.headers = new Headers();
    }

    public Reply(int code, Response response) {
        this(code);
        this.response = response;
    }

    public Reply(CharSequence text) {
        this.hdr = new ByteList(128);
        this.hdr.putAscii("HTTP/1.1 200 OK\r\nServer: Async/2.0\r\nConnection: keep-alive\r\n");
        this.response = new StringResponse(text);
        this.headers = new Headers();
    }

    public boolean keepAlive() {
        return !noKeepAlive;
    }

    public Reply connectionClose() {
        if (buf != null) throw new IllegalStateException();
        if (noKeepAlive) return this;
        noKeepAlive = true;
        int len = 12 + MathUtils.digitCount(RequestHandler.KEEP_ALIVE_TIMEOUT);
        hdr.wIndex(hdr.wIndex() - len);
        hdr.putAscii("close\r\n");
        return this;
    }

    public void setResponse(Response response) {
        this.response = response;
    }

    public void header(String k, String v) {
        headers.put(k, v);
    }

    public Headers getHeaders() {
        return headers;
    }

    public ByteList getRawHeaders() {
        return hdr;
    }

    private ByteBuffer buf = null;

    public void prepare() throws IOException {
        if (response != null) response.prepare();
        if (buf == null) {
            CharList tmp = IOUtil.SharedUTFCoder.get().charBuf;
            tmp.clear();
            tmp.ensureCapacity(100);

            if (response != null) response.writeHeader(tmp);
            else tmp.append("Content-Length: 0\r\n");
            headers.encode(tmp);
            //if (!headers.containsKey("Date")) {
            //    hdr.putAscii("Date: ").putAscii(RFC_DATE.copy().toRFCDate(System.currentTimeMillis()).append("\r\n"));
            //}
            ByteList.writeUTF(hdr, tmp.append("\r\n"), -1);
            buf = ByteBuffer.wrap(hdr.list, 0, hdr.wIndex());
        }
        else buf.position(0);
    }

    public boolean send(WrappedSocket channel) throws IOException {
        if (buf == null) throw new IllegalStateException();

        if (buf.remaining() > 0) {
            if (channel.write(buf) <= 0)
                return true;
        }

        if (response != null && response.send(channel)) return true;

        return !channel.dataFlush();
    }

    public void release() throws IOException {
        if (response != null) response.release();
    }
}
