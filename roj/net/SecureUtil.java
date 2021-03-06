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

import javax.net.ssl.*;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

/**
 * @author Roj234
 * @since  2021/2/5 0:26
 */
public final class SecureUtil {
    public static final String PROTOCOL = "TLS", KEY_FORMAT = "PKCS12", MANAGER_FORMAT = "SunX509";

    static SSLContext getSslContext(InputStream pkPath, InputStream caPath, char[] passwd, boolean serverSide) throws IOException, GeneralSecurityException {
        // 密钥管理器
        KeyManager[] kmf = null;
        if (serverSide) {
            kmf = makeKeyManagers(pkPath, passwd);
        }

        // 信任库
        TrustManager[] tmf = null;
        if (caPath != null) {
            tmf = makeTrustManagers(caPath, passwd);
        } else if (!serverSide)
            throw new RuntimeException("Client must verify server.");

        SSLContext ctx = SSLContext.getInstance(PROTOCOL);

        // 初始化此上下文
        // 参数一：认证的密钥 参数二：对等信任认证 参数三：伪随机数生成器
        // 单向认证，服务端不用验证客户端，所以第二个参数为null
        ctx.init(kmf, tmf, null);

        return ctx;
    }

    static TrustManager[] makeTrustManagers(InputStream caPath, char[] passwd) throws GeneralSecurityException, IOException {
        KeyStore tks = KeyStore.getInstance(KEY_FORMAT);
        try (InputStream in = caPath) {
            tks.load(in, passwd);
        }
        TrustManagerFactory tf = TrustManagerFactory.getInstance(MANAGER_FORMAT);
        tf.init(tks);
        return tf.getTrustManagers();
    }

    public static KeyManager[] makeKeyManagers(InputStream pkPath, char[] passwd) throws IOException, GeneralSecurityException {
        KeyStore ks = KeyStore.getInstance(KEY_FORMAT);
        try (InputStream in = pkPath) {
            ks.load(in, passwd);
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(MANAGER_FORMAT);
        kmf.init(ks, passwd);

        return kmf.getKeyManagers();
    }

}
