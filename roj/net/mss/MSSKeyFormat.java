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

import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;

/**
 * @author Roj233
 * @since 2021/12/22 12:54
 */
public interface MSSKeyFormat<T> extends MSSClientKey {
    static MSSKeyFormat<?> getInstance(String alg) throws NoSuchAlgorithmException {
        switch (alg) {
            case "RSA":
                return X509KeyFormat.RSA;
            case "EC":
                return X509KeyFormat.EC;
            case "DH":
                return X509KeyFormat.DH;
            case "X509CERT":
                return new X509CertKeyFormat();
            default:
                throw new NoSuchAlgorithmException(alg);
        }
    }

    String getAlgorithm();
    int formatId();

    byte[] encode(T publicKey) throws GeneralSecurityException;
    MSSPubKey decode(byte[] data) throws GeneralSecurityException;
}
