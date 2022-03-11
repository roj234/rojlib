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

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;

/**
 * @author Roj234
 * @since  2021/2/16 11:17
 */
public class DirRouter implements Router {
    public final File dir;

    public DirRouter(File dir) {
        this.dir = dir;
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
    public Response response(Request req, RequestHandler rh) throws IOException {
        String url = req.path();
        File f = new File(dir, url.endsWith("/") ? url + "index.html" : url);
        if(!f.isFile()) {
            if(url.endsWith("/")) {
                rh.reply(403);
                return StringResponse.httpErr(Code.FORBIDDEN);
            }
            rh.reply(404);
            return StringResponse.httpErr(Code.NOT_FOUND);
        }
        return new FileResponse(f);
    }
}
