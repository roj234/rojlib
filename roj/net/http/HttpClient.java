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
package roj.net.http;

import roj.config.ParseException;
import roj.io.ChannelOutputStream;
import roj.io.EmptyInputStream;
import roj.io.NIOUtil;
import roj.net.Connector;
import roj.net.JavaSslFactory;
import roj.net.SocketFactory;
import roj.net.SocketSequence;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.FastThreadLocal;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;

/**
 * @author Roj234
 * @since  2020/12/5 14:34
 */
public class HttpClient extends Connector {
    private CharSequence action, body;
    private final Headers header = new Headers();
    private final CharList utf8Buf = new CharList();
    private int maxReceive;
    private URL url;

    public HttpClient() {
        header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,* /*;q=0.8,application/signed-exchange;v=b3;q=0.9")
                .header("Accept-Encoding", "gzip, deflate")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("Connection", "keep-alive")
                .header("Pragma", "no-cache")
                .header("Cache-Control", "no-cache");
        action = "GET";
    }

    public HttpClient method(String type) {
        if (Action.valueOf(type) == -1)
            throw new IllegalArgumentException(type);
        action = type;
        return this;
    }

    public String method() {
        return action.toString();
    }

    public Headers headers() {
        return header;
    }

    public HttpClient header(CharSequence k, String v) {
        this.header.put(k, v);
        return this;
    }

    public void headers(Map<String, String> headers) {
        this.header.clear();
        this.header.putAll(headers);
    }

    public HttpClient body(CharSequence body) {
        this.body = body;
        return this;
    }

    public HttpClient ssl(SocketFactory f) {
        this.factory = f == null ? SocketFactory.PLAIN_FACTORY : f;
        return this;
    }

    public void send() throws IOException {
        if (in != null) in.close();
        if (server == null) {
            createSocket(url.getHost(), url.getPort() < 0 ? url.getDefaultPort() : url.getPort(), false);
        }
        connect();

        long timeout = (readTimeout <= 0 ? 5000 : readTimeout) + System.currentTimeMillis();

        while (!channel.handShake()) {
            LockSupport.parkNanos(100);
            if (System.currentTimeMillis() > timeout) {
                throw new SocketTimeoutException("Handshake");
            }
        }

        ByteBuffer buf = channel.buffer();
        buf.clear();

        prepare(buf);
        buf.flip();

        while (channel.write(buf) > 0) {
            LockSupport.parkNanos(100);
            if (System.currentTimeMillis() > timeout) {
                throw new SocketTimeoutException("Write");
            }
        }

        buf.clear();

        while (!channel.dataFlush()) {
            LockSupport.parkNanos(100);
            if (System.currentTimeMillis() > timeout) {
                throw new SocketTimeoutException("Flush");
            }
        }
    }

    static final String CRLF = "\r\n";

    private void prepare(ByteBuffer buf) {
        CharList text = utf8Buf;
        text.clear();

        if (body != null && body.length() != 0) {
            header.put("Content-Length", Integer.toString(ByteList.byteCountUTF8(body)));
        }

        text.append(action).append(" ").append(url.getPath()).append(" HTTP/1.1").append(CRLF);
        header.encode(text);

        if (body == null) {
            text.append(CRLF);
        }

        writeUTF(buf, text);
        text.clear();

        if (body != null && body.length() != 0) {
            writeUTF(buf, body);
        }
    }

    private static void writeUTF(ByteBuffer nio, CharSequence str) {
        int len = str.length();
        for (int j = 0; j < len; j++) {
            int c = str.charAt(j);
            if ((c >= 0x0001) && (c <= 0x007F)) {
                nio.put((byte) c);
            } else if (c > 0x07FF) {
                nio.put((byte) (0xE0 | ((c >> 12) & 0x0F)))
                   .put((byte) (0x80 | ((c >> 6) & 0x3F)))
                   .put((byte) (0x80 | (c & 0x3F)));
            } else {
                nio.put((byte) (0xC0 | ((c >> 6) & 0x1F)))
                   .put((byte) (0x80 | (c & 0x3F)));
            }
        }
    }

    private InputStream in;

    static final FastThreadLocal<Object[]> LOCAL_LEXER = FastThreadLocal.withInitial(() -> {
        return new Object[] {new SocketSequence(false), new HttpLexer() };
    });

    public HttpHead response() throws ParseException, IOException {
        Object[] data = LOCAL_LEXER.get();

        SocketSequence plain = (SocketSequence)data[0];
        HttpLexer lexer = ((HttpLexer)data[1]).init(plain.init(channel, readTimeout, maxReceive <= 0 ? Integer.MAX_VALUE : maxReceive));

        try {
            HttpHead hdr = HttpHead.parse(lexer);
            ByteBuffer buf = channel.buffer();
            int pos = buf.position();
            buf.position(lexer.index).limit(pos);
            buf.compact();
            if (!"HEAD".contentEquals(action)) {
                in = HttpInputStream.create(hdr, new SocketInputStream(channel).init(hdr.headers.get("Content-Length"), readTimeout));
            } else {
                in = new EmptyInputStream();
            }
            return hdr;
        } finally {
            lexer.init(null);
            plain.release();
        }
    }

    @Override
    public void disconnect() throws IOException {
        if (in != null) {
            in.close();
            in = null;
        }
        super.disconnect();
    }

    public InputStream getInputStream() {
        if (in == null) in = new SocketInputStream(channel).init(null, readTimeout);
        return in;
    }

    public OutputStream getOutputStream() {
        return new ChannelOutputStream(channel);
    }

    public HttpClient url(URL url) throws IOException {
        if(url.getProtocol().equals("https") && factory == SocketFactory.PLAIN_FACTORY) {
            try {
                factory = JavaSslFactory.getClientDefault();
            } catch (GeneralSecurityException ignored) {}
        }
        this.url = url;
        String host = url.getHost();
        if (url.getPort() >= 0) host += ":" + url.getPort();
        header.put("Host", host);
        return this;
    }

    public void setConnected() {
        NIOUtil.setConnected(server, true);
    }
}
