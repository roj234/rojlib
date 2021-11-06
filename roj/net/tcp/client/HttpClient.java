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

import roj.collect.LinkedMyHashMap;
import roj.collect.MyHashMap;
import roj.config.ParseException;
import roj.io.EmptyInputStream;
import roj.io.NonblockingUtil;
import roj.net.ssl.EngineAllocator;
import roj.net.ssl.SslEngineFactory;
import roj.net.tcp.util.*;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.ByteWriter;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;

/**
 * 傻逼GIT搞丢了我可怜的这个class，这是反编译出来的
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/12/5 14:34
 */
public class HttpClient extends ClientSocket {
    private CharSequence action, path, body;
    private final MyHashMap<CharSequence, CharSequence> header = new LinkedMyHashMap<>();
    private final CharList utf8Buf = new CharList();
    private int maxReceive;

    public HttpClient() {
        header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,* /*;q=0.8,application/signed-exchange;v=b3;q=0.9")
                .header("Accept-Encoding", "gzip, deflate")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("Connection", "keep-alive")
                .header("Pragma", "no-cache")
                .header("Cache-Control", "no-cache");
    }

    public HttpClient method(String type) {
        if (Action.valueOf(type) == -1)
            throw new IllegalArgumentException(type);
        action = type;
        return this;
    }

    public Map<CharSequence, CharSequence> headers() {
        return header;
    }

    public HttpClient header(CharSequence k, CharSequence v) {
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

    public HttpClient path(CharSequence path) {
        this.path = path;
        return this;
    }

    @Override
    protected WrappedSocket createChannel() throws IOException {
        return clientEngine != null ? SecureSocket.get(server.socket(), NonblockingUtil.fd(server), clientEngine, true) : new InsecureSocket(server.socket(), NonblockingUtil.fd(server));
    }

    EngineAllocator clientEngine;

    public HttpClient ssl(EngineAllocator clientEngine) {
        this.clientEngine = clientEngine;
        return this;
    }

    public void send() throws IOException {
        connect();

        long timeout = readTimeout <= 0 ? Long.MAX_VALUE : readTimeout + System.currentTimeMillis();

        while (!channel.handShake()) {
            LockSupport.parkNanos(100);
            if (System.currentTimeMillis() > timeout) {
                throw new SocketTimeoutException("Handshake");
            }
        }

        ByteList buf = channel.buffer();
        buf.clear();

        prepare(buf);

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

    private void prepare(ByteList buf) {
        CharList text = utf8Buf;
        text.clear();

        if (body != null && body.length() != 0) {
            header.put("Content-Length", Integer.toString(ByteWriter.byteCountUTF8(body)));
        }

        text.append(action).append(" ").append(path).append(" HTTP/1.1").append(CRLF);

        for (Map.Entry<CharSequence, CharSequence> entry : header.entrySet()) {
            text.append(entry.getKey()).append(": ").append(entry.getValue()).append(CRLF);
        }

        if (body == null) {
            text.append(CRLF);
        }

        ByteWriter.writeUTF(buf, text, -1);
        text.clear();

        if (body != null && body.length() != 0) {
            ByteWriter.writeUTF(buf, body, -1);
        }
        buf.add((byte) '\r');
        buf.add((byte) '\n');
        buf.add((byte) '\r');
        buf.add((byte) '\n');
    }

    private InputStream in;

    public HttpHeader response() throws ParseException {
        Object[] data = Shared.SYNC_BUFFER.get();

        StreamLikeSequence plain = (StreamLikeSequence)data[0];
        HTTPHeaderLexer lexer = ((HTTPHeaderLexer)data[1]).init(plain.init(channel, readTimeout, maxReceive <= 0 ? Integer.MAX_VALUE : maxReceive));

        try {
            HttpHeader hdr = HttpHeader.parse(lexer, action);
            if (!"HEAD".contentEquals(action)) {
                in = HttpInputStream.create(hdr, new SocketInputStream(channel, lexer.index).init(hdr.headers.get("Content-Length"), readTimeout));
            } else {
                in = new EmptyInputStream();
                channel.buffer().clear();
            }
            return hdr;
        } catch (IOException e) {
            throw lexer.err("IO Exception", e);
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
        return in;
    }

    public HttpClient url(URL url) throws IOException {
        if(url.getProtocol().equals("https")) {
            try {
                clientEngine = SslEngineFactory.getClientDefault();
            } catch (GeneralSecurityException ignored) {}
        }
        path = url.getPath();
        header.put("Host", url.getHost());
        createSocket(url.getHost(), url.getPort() == -1 ? url.getDefaultPort() : url.getPort(), false);
        return this;
    }
}
