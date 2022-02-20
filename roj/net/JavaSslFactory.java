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
package roj.net;

import roj.io.NIOUtil;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.net.Socket;
import java.util.function.Supplier;

/**
 * @author Roj234
 * @since  2021/2/5 0:33
 */
public class JavaSslFactory implements SocketFactory, Supplier<SSLEngine> {
    final SSLContext context;
    final SslConfig config;

    public JavaSslFactory(SSLContext context, SslConfig config) {
        this.context = context;
        this.config = config;
    }

    public SSLEngine get() {
        SSLEngine engine = context.createSSLEngine();
        if (config == null) {
            engine.setNeedClientAuth(false);
            engine.setUseClientMode(true);
        } else {
            engine.setUseClientMode(!config.isServerSide());
            // false为单向认证，true为双向认证
            engine.setNeedClientAuth(config.isNeedClientAuth());
        }
        return engine;
    }

    @Override
    public WrappedSocket wrap(Socket sc) throws IOException {
        return new SSLSocket(sc, NIOUtil.fd(sc), this);
    }
}
