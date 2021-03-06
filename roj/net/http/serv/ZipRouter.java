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

import roj.net.http.Code;
import roj.util.ByteList;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author Roj234
 * @since  2021/2/16 11:17
 */
public class ZipRouter implements Router {
    public final ZipFile zipFs;

    public ZipRouter(ZipFile zipFs) {
        this.zipFs = zipFs;
    }

    @Override
    public Response response(Request req, RequestHandler rh) throws IOException {
        String url = req.path().substring(1);

        boolean flag = url.endsWith("/");
        ZipEntry ze = zipFs.getEntry(flag ? url + "index.html" : url);
        if(ze == null) {
            if(flag) {
                ZipEntry dir = zipFs.getEntry(url);
                if(dir != null && dir.isDirectory()) {
                    rh.reply(403);
                    return StringResponse.httpErr(Code.FORBIDDEN);
                }
            }
            rh.reply(404);
            return StringResponse.httpErr(Code.NOT_FOUND);
        }
        rh.reply(200).header("Cache-Control: max-age=86400");
        return new ZipResponse(url, zipFs.getInputStream(ze));
    }

    private static class ZipResponse extends StreamResponse {
        private final String url;
        private final InputStream in;
        private long length;

        public ZipResponse(String url, InputStream in) {
            this.url = url;
            this.in = in;
        }

        @Override
        public void writeHeader(ByteList list) {
            String type;
            if (url.endsWith(".html"))
                type = "text/html; charset=UTF-8";
            else
                type = "application/octet-stream";

            list.putAscii("Content-Type: ").putAscii(type).putAscii(CRLF)
                .putAscii("Content-Length: ").putAscii(Long.toString(length)).putAscii(CRLF);
        }

        @Override
        protected InputStream getStream() throws IOException {
            length = in.available();
            return in;
        }
    }
}
