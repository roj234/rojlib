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

import roj.net.tcp.serv.util.Request;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.Socket;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/11/28 20:54
 */
@FunctionalInterface
public interface Router {
    default int writeTimeout(@Nullable Request request) {
        return 2000;
    }

    default int readTimeout() {
        return 5000;
    }

    /**
     * any root router should return a {@link Reply}
     */
    Response response(Socket socket, Request request) throws IOException;

    default int maxLength() {
        return 1048576; // 1MB
    }

    default boolean checkAction(int action) {
        return action != -1;
    }

    default int postMaxLength() {
        return maxLength();
    }
}
