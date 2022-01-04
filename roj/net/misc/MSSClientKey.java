/*
 * This file is a part of MoreItems
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
package roj.net.misc;

import roj.net.mss.MSSEngineClient;
import roj.net.mss.PreSharedPubKey;

import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

/**
 * @author Roj233
 * @version 0.3.1
 * @since 2021/8/18 0:09
 */
public final class MSSClientKey {
    public static void loadMSSCert() {
        try {
            InputStream stream = MSSClientKey.class.getResourceAsStream("/META-INF/roj234.cer");
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            X509Certificate cer = (X509Certificate) factory.generateCertificate(stream);
            MSSEngineClient.setDefaultKeyFormats(new PreSharedPubKey(cer));
        } catch (GeneralSecurityException e) {
            e.printStackTrace();
            System.err.println("MSS引擎预共享根证书模式初始化失败");
        }
    }
}