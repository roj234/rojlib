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

import roj.net.SecureUtil;

import javax.net.ssl.KeyManager;
import javax.net.ssl.X509KeyManager;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

/**
 * @author Roj233
 * @version 0.1
 * @since 2021/12/24 22:44
 */
public final class MSSServerEngineFactory {
    private final PrivateKey privateKey;
    private final byte[] publicKey;
    private final int pubKeyFormat;

    public <T> MSSServerEngineFactory(MSSPubKey<T> format, T publicKey, PrivateKey key) throws GeneralSecurityException {
        this.privateKey = key;
        this.publicKey = format.encode(publicKey);
        this.pubKeyFormat = format.specificationId();
        format.checkPrivateKey(key);
    }

     MSSServerEngineFactory(PrivateKey key, byte[] publicKey, int pubKeyFormat) {
        this.privateKey = key;
        this.publicKey = publicKey;
        this.pubKeyFormat = pubKeyFormat;
    }

    public MSSEngineServer newEngine() throws GeneralSecurityException {
        return new MSSEngineServer().__init(pubKeyFormat, publicKey, privateKey);
    }

    public static MSSServerEngineFactory fromKeystore(InputStream ks, char[] pass) throws IOException, GeneralSecurityException {
        KeyManager[] kmf = SecureUtil.makeKeyManagers(ks, pass);

        X509Certificate pubKey = null;
        PrivateKey privateKey = null;
        for (KeyManager manager : kmf) {
            if (manager instanceof X509KeyManager) {
                X509KeyManager km = (X509KeyManager) manager;
                String alias = km.chooseServerAlias("RSA", null, null);
                privateKey = km.getPrivateKey(alias);
                pubKey = km.getCertificateChain(alias)[0];
                break;
            }
        }
        if (pubKey == null)
            throw new NoSuchAlgorithmException("No such key");

        PreSharedCertificate pkf = new PreSharedCertificate(pubKey);
        return new MSSServerEngineFactory(privateKey, new byte[1], pkf.specificationId());
    }
}
