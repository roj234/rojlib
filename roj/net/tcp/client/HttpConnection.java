/*
 * This file is a part of MoreItems
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

import roj.config.ParseException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * 模拟HttpURLConnection
 * <br> 傻逼GIT搞丢了我可怜的这个class，这是反编译出来的
 *
 * @author solo6975
 * @version 0.1
 * @since 2021/10/23 21:36
 */
public class HttpConnection {
    URL        url;
    HttpClient connector;
    HttpHeader response;

    public HttpConnection(final URL url) {
        this.url = url;
        connector = new HttpClient();
    }

    public void connect() throws IOException {
        //noinspection all
        Thread.interrupted();
        if (!connector.connected()) {
            connector.url(url).send();
            try {
                response = connector.response();
            } catch (ParseException e) {
                throw new IOException(e.toString(), e);
            }
        }
    }

    public void disconnect() throws IOException {
        connector.disconnect();
    }

    public URL getURL() {
        return url;
    }

    public void setURL(URL url) throws IOException {
        disconnect();
        this.url = url;
    }

    public HttpHeader getResponse() throws IOException {
        connect();
        return response;
    }

    public String getHeaderField(String name) throws IOException {
        connect();
        return response.headers.get(name);
    }

    public long getContentLengthLong() throws IOException {
        connect();
        return Long.parseLong(response.headers.getOrDefault("Content-Length", "-1"));
    }

    public int getContentLength() throws IOException {
        connect();
        return Integer.parseInt(response.headers.getOrDefault("Content-Length", "-1"));
    }

    public InputStream getInputStream() throws IOException {
        connect();
        return connector.getInputStream();
    }

    public HttpClient getClient() {
        return connector;
    }
}
