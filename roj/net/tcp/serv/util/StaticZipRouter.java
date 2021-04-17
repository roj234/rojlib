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
package roj.net.tcp.serv.util;

import roj.net.tcp.serv.Reply;
import roj.net.tcp.serv.Response;
import roj.net.tcp.serv.Router;
import roj.net.tcp.serv.response.StreamResponse;
import roj.net.tcp.serv.response.StringResponse;
import roj.net.tcp.util.Code;
import roj.text.CharList;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2021/2/16 11:17
 */
public class StaticZipRouter implements Router {
    public final ZipFile zipFs;

    public StaticZipRouter(ZipFile zipFs) {
        this.zipFs = zipFs;
    }

    @Override
    public int readTimeout() {
        return 3000;
    }

    @Override
    public int writeTimeout(@Nullable Request request) {
        return 5000;
    }

    @Override
    public Response response(Socket socket, Request request) throws IOException {
        String url = request.path().substring(1);

        boolean flag = url.endsWith("/");
        ZipEntry ze = zipFs.getEntry(flag ? url + "index.html" : url);
        if(ze == null) {
            if(flag) {
                ZipEntry dir = zipFs.getEntry(url);
                if(dir != null && dir.isDirectory())
                    return new Reply(Code.FORBIDDEN, StringResponse.errorResponse(Code.FORBIDDEN, null));
            }
            return new Reply(Code.NOT_FOUND, StringResponse.errorResponse(Code.NOT_FOUND, null));
        }
        return new Reply(Code.OK, new ZipResponse(url, zipFs.getInputStream(ze)));
    }

    private static class ZipResponse extends StreamResponse {
        private final String url;
        private final InputStream in;

        public ZipResponse(String url, InputStream in) {
            this.url = url;
            this.in = in;
        }

        @Override
        public void writeHeader(CharList list) {
            String type;
            if (url.endsWith(".html"))
                type = "text/html; charset=UTF-8";
            else
                type = "application/octet-stream";

            list.append("Content-Type: ").append(type).append(CRLF)
                    .append("Content-Length: ").append(Long.toString(length)).append(CRLF);
        }

        @Override
        protected InputStream getStream() throws IOException {
            length = in.available();
            return in;
        }
    }
}
