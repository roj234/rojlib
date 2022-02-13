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
package roj.net.mss;

import java.io.ByteArrayInputStream;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

/**
 * @author Roj233
 * @since 2021/12/22 12:53
 */
public class X509KeyFormat implements MSSKeyFormat<X509Certificate> {
    private final CertificateFactory factory;

    public X509KeyFormat() {
        try {
            factory = CertificateFactory.getInstance("X.509");
        } catch (Throwable e) {
            throw new Error();
        }
    }

    @Override
    public String name() {
        return "X.509 " + factory.getType();
    }

    @Override
    public int formatId() {
        return CipherSuite.KEY_X509_CERTIFICATE;
    }

    @Override
    public byte[] encode(X509Certificate publicKey) throws GeneralSecurityException {
        return publicKey.getEncoded();
    }

    @Override
    public MSSPubKey decode(byte[] data) throws GeneralSecurityException {
        X509Certificate crt = (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(data));
        return new X509CertKey(0, crt);
    }

    @Override
    public void checkPrivateKey(PrivateKey key) {}
}
