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

import roj.collect.MyHashMap;
import roj.concurrent.task.CalculateTask;
import roj.config.ParseException;
import roj.io.NonblockingUtil;
import roj.net.tcp.ssl.EngineAllocator;
import roj.net.tcp.ssl.SslConfig;
import roj.net.tcp.ssl.SslEngineFactory;
import roj.net.tcp.util.Action;
import roj.net.tcp.util.InsecureSocket;
import roj.net.tcp.util.SecureSocket;
import roj.net.tcp.util.WrappedSocket;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.ByteWriter;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.Map;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/12/5 14:34
 */
public class HttpClient extends ClientSocket {
    public static EngineAllocator CLIENT_ALLOCATOR;

    static {
        try {
            CLIENT_ALLOCATOR = SslEngineFactory.getSslFactory(new SslConfig() {
                @Override
                public boolean isServerSide() {
                    return false;
                }

                @Override
                public boolean isNeedClientAuth() {
                    return false;
                }

                @Override
                public InputStream getPkPath() {
                    try {
                        return new FileInputStream("server.ks");
                    } catch (FileNotFoundException e) {
                        return null;
                    }
                }

                @Override
                public InputStream getCaPath() {
                    try {
                        return new FileInputStream("server.ks");
                    } catch (FileNotFoundException e) {
                        return null;
                    }
                }

                @Override
                public char[] getPasswd() {
                    return "123456".toCharArray();
                }
            });
        } catch (IOException | GeneralSecurityException e) {
            System.err.println("Failed to initialize SelEngine");
            e.printStackTrace();
        }
    }

    private CharSequence action, path, body;
    private final MyHashMap<CharSequence, CharSequence> header = new MyHashMap<>();
    private final CharList utf8Buf = new CharList();
    private int maxReceive;

    public HttpClient() {}

    public HttpClient type(String type) {
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
    protected WrappedSocket getChannel() throws IOException {
        return ssl ? SecureSocket.get(server.socket(), NonblockingUtil.fd(server), CLIENT_ALLOCATOR, true) : new InsecureSocket(server.socket(), NonblockingUtil.fd(server));
    }

    boolean ssl;

    public HttpClient ssl(boolean ssl) {
        this.ssl = ssl;
        return this;
    }

    public void send() throws IOException {
        connect();

        final long timeout = writeTimeout <= 0 ? Long.MAX_VALUE : writeTimeout + System.currentTimeMillis();

        while (!channel.handShake()) {
            if (System.currentTimeMillis() > timeout) {
                throw new SocketTimeoutException("Timeout " + timeout);
            }
        }

        ByteList buf = channel.buffer();
        buf.clear();

        prepare(buf);

        while (channel.write(buf) > 0) {
            if (System.currentTimeMillis() > timeout) {
                throw new SocketTimeoutException("Timeout " + timeout);
            }
        }

        buf.clear();

        while (!channel.dataFlush());

        header.clear();
    }

    static final String CRLF = "\r\n";

    private void prepare(ByteList buf) {
        CharList text = utf8Buf;
        text.clear();

        if (body != null) {
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

        if (body != null) {
            ByteWriter.writeUTF(buf, body, -1);
        }
        buf.add((byte) '\r');
        buf.add((byte) '\n');
        buf.add((byte) '\r');
        buf.add((byte) '\n');
    }

    public HTTPResponse response() throws ParseException {
        ByteList buffer = channel.buffer();
        try {
            return HTTPResponse.parse(channel, readTimeout, maxReceive <= 0 ? Integer.MAX_VALUE : maxReceive);
        } finally {
            buffer.clear();
        }
    }

    public CalculateTask<HTTPResponse> asyncResponse() {
        return new CalculateTask<>(this::response);
    }

    public HttpClient url(URL url) throws IOException {
        ssl = url.getProtocol().equals("https");
        path = url.getPath();
        header.put("Host", url.getHost());
        createSocket(url.getHost(), url.getPort() == -1 ? url.getDefaultPort() : url.getPort(), false);
        return this;
    }
}
