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
package roj.net.tcp.serv;

import roj.net.tcp.serv.response.HeadResponse;
import roj.net.tcp.serv.util.Request;
import roj.net.tcp.util.Code;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.Socket;
import java.util.Map;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/12/5 17:20
 */
public class RouterImpl implements Router {
    private final Router router;

    public RouterImpl(Router router) {
        if (router.getClass() == RouterImpl.class)
            throw new IllegalStateException();
        this.router = router;
    }

    @Override
    public Response response(Socket socket, Request request) throws IOException {
        String conn = request.headers().getOrDefault("Connection", "");
        switch (conn) {
            case "close":
                break;
            case "keep-alive":
                // ...
                break;
            case "upgrade":
                return tryUpgrade(socket, request);
        }

        return router.response(socket, request);
    }

    /**
     * Connection: upgrade
     * Upgrade: protocol-name[/protocol-version]
     */
    protected Response tryUpgrade(Socket socket, Request request) {
        String newRequest = request.headers("Upgrade");
        if ("h2c".equals(newRequest)) {
            HeadResponse response = new HeadResponse();

            final Map<CharSequence, CharSequence> headers = response.headers();
            headers.put("Upgrade", "h2c");
            headers.put("Connection", "upgrade");

            return new Reply(Code.SWITCHING_PROTOCOL, response);
        }
        // todo HTTP2.0
        return null;
    }

    @Override
    public int maxLength() {
        return router.maxLength();
    }

    @Override
    public int readTimeout() {
        return router.readTimeout();
    }

    @Override
    public int writeTimeout(@Nullable Request request) {
        return router.writeTimeout(request);
    }
}
