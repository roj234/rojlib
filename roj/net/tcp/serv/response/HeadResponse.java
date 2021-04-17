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

import roj.collect.MyHashMap;
import roj.net.tcp.util.WrappedSocket;
import roj.text.CharList;
import roj.text.TextUtil;

import java.io.IOException;
import java.util.Map;

public final class HeadResponse implements HTTPResponse {
    private final Map<CharSequence, CharSequence> header = new MyHashMap<>();
    private final HTTPResponse delegation;

    public HeadResponse() {
        this.delegation = null;
    }

    public HeadResponse(HTTPResponse delegation) {
        if (delegation.getClass() == HeadResponse.class)
            throw new IllegalArgumentException();
        this.delegation = delegation;
    }

    public void prepare() throws IOException {
        if (delegation != null)
            delegation.prepare();
    }

    public boolean send(WrappedSocket channel) throws IOException {
        return delegation != null && delegation.send(channel);
    }

    public void release() throws IOException {
        if (delegation != null)
            delegation.release();
    }

    public Map<CharSequence, CharSequence> headers() {
        return header;
    }

    @Override
    public void writeHeader(CharList list) {
        for (Map.Entry<CharSequence, CharSequence> entry : header.entrySet()) {
            assert TextUtil.lastIndexOf(entry.getValue(), '\n') == -1;
            list.append(entry.getKey()).append(": ").append(entry.getValue()).append(CRLF);
        }
        if (delegation != null)
            delegation.writeHeader(list);
    }
}
