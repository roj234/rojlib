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
package roj.net.ssl;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2021/2/5 0:26
 */
public final class SslEngineFactory {
    public static final String PROTOCOL = "TLS", KEY_FORMAT = "PKCS12", MANAGER_FORMAT = "SunX509";

    private static SSLContext getSslContext(InputStream pkPath, InputStream caPath, char[] passwd, boolean serverSide) throws IOException, GeneralSecurityException {
        // 密钥管理器
        KeyManagerFactory kmf = null;
        if (serverSide) {
            kmf = getKeyManagerFactory(pkPath, passwd);
        }

        // 信任库
        TrustManagerFactory tmf = null;
        if (caPath != null) {
            tmf = getTrustManagerFactory(caPath, passwd);
        } else if (!serverSide)
            throw new RuntimeException("Client must verify server.");

        SSLContext ctx = SSLContext.getInstance(PROTOCOL);

        // 初始化此上下文
        // 参数一：认证的密钥 参数二：对等信任认证 参数三：伪随机数生成器
        // 单向认证，服务端不用验证客户端，所以第二个参数为null
        ctx.init(kmf == null ? null : kmf.getKeyManagers(), tmf == null ? null : tmf.getTrustManagers(), null);

        return ctx;
    }

    static TrustManagerFactory getTrustManagerFactory(InputStream caPath, char[] passwd) throws GeneralSecurityException, IOException {
        KeyStore tks = KeyStore.getInstance(KEY_FORMAT);
        try (InputStream in = caPath) {
            tks.load(in, passwd);
        }
        TrustManagerFactory tf = TrustManagerFactory.getInstance(MANAGER_FORMAT);
        tf.init(tks);
        return tf;
    }

    static KeyManagerFactory getKeyManagerFactory(InputStream pkPath, char[] passwd) throws IOException, GeneralSecurityException {
        KeyStore ks = KeyStore.getInstance(KEY_FORMAT);
        try (InputStream in = pkPath) {
            ks.load(in, passwd);
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(MANAGER_FORMAT);
        kmf.init(ks, passwd);

        return kmf;
    }

    public static EngineAllocator getSslFactory(SslConfig cfg) throws IOException, GeneralSecurityException {
        SSLContext context = getSslContext(cfg.getPkPath(), cfg.getCaPath(), cfg.getPasswd(), cfg.isServerSide());
        return new Alloc(context, cfg);
    }

    public static EngineAllocator getClientDefault() throws NoSuchAlgorithmException {
        return new Alloc(SSLContext.getDefault(), null);
    }

    private static final class Alloc extends EngineAllocator {
        private final SSLContext context;

        public Alloc(SSLContext context, SslConfig cfg) {
            super(cfg);
            this.context = context;
        }

        @Override
        public SSLEngine allocate() {
            SSLEngine engine = context.createSSLEngine();
            config(engine, config);
            return engine;
        }
    }
}
