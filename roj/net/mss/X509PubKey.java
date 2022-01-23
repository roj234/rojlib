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

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayInputStream;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

/**
 * @author Roj233
 * @since 2021/12/22 12:53
 */
public class X509PubKey implements MSSPubKey<X509Certificate> {
    private static X509TrustManager systemDefault;

    final X509TrustManager tm;
    final CertificateFactory factory;

    public X509PubKey() throws CertificateException, NoSuchAlgorithmException {
        factory = CertificateFactory.getInstance("X.509");
        getDefault();
        tm = systemDefault;
    }

    public X509PubKey(X509TrustManager tm) throws CertificateException {
        factory = CertificateFactory.getInstance("X.509");
        this.tm = tm;
    }

    private static void getDefault() throws NoSuchAlgorithmException {
        if (systemDefault == null) {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            try {
                tmf.init((KeyStore) null);
            } catch (KeyStoreException e) {
                throw new NoSuchAlgorithmException("Unable to find system default trust manger", e);
            }
            for (TrustManager manager : tmf.getTrustManagers()) {
                if (manager instanceof X509TrustManager) {
                    systemDefault = (X509TrustManager) manager;
                    return;
                }
            }
            throw new NoSuchAlgorithmException("Unable to find system default trust manger");
        }
    }

    public X509TrustManager getTrustManager() {
        return tm;
    }

    @Override
    public String name() {
        return "X.509 Verified " + factory.getType();
    }

    @Override
    public int specificationId() {
        return 0x11000000 | (factory.getType().hashCode() % 0xFFE);
    }

    @Override
    public byte[] encode(X509Certificate publicKey) throws GeneralSecurityException {
        return publicKey.getEncoded();
    }

    @Override
    public PublicKey decode(byte[] data) throws GeneralSecurityException {
        X509Certificate crt = (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(data));
        tm.checkClientTrusted(new X509Certificate[]{ crt }, crt.getPublicKey().getAlgorithm());
        return crt.getPublicKey();
    }

    @Override
    public void checkPrivateKey(PrivateKey key) throws GeneralSecurityException {

    }
}
