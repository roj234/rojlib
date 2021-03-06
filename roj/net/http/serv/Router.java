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

import roj.net.http.IllegalRequestException;

import javax.annotation.Nullable;
import java.io.IOException;

/**
 * @author Roj234
 * @since  2020/11/28 20:54
 */
@FunctionalInterface
public interface Router {
    default int writeTimeout(@Nullable Request req) {
        return 2000;
    }

    default int readTimeout() {
        return 5000;
    }

    default int postTimeout(Request req, int remain) {
        return remain;
    }

    default Response errorCaught(Throwable e, RequestHandler rh, String where) {
        e.printStackTrace();
        rh.reply(500);
        if (where.equals("PARSING_REQUEST")) rh.connClose();
        return StringResponse.forError(0, e);
    }

    Response response(Request req, RequestHandler rh) throws IOException;

    default int maxLength() {
        // HTTP headers + path
        return 8192;
    }

    default void checkHeader(Request req) throws IllegalRequestException {
        //if (req.action() == Action.POST && !acceptsLengthlessPost) {
        //    // length required
        //    throw new IllegalRequestException(411);
        //}
    }

    default long postMaxLength(Request req) {
        return 1048576; // 1MB
    }

    default boolean useCompress(Request req, Response reply) {
        return reply != null && reply.wantCompress();
    }
}
