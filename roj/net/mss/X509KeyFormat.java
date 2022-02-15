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

import roj.util.Helpers;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

/**
 * @author Roj233
 * @since 2021/12/22 12:53
 */
public final class X509KeyFormat implements MSSKeyFormat<PublicKey> {
    public static final X509KeyFormat RSA, EC, DH;

    static {
        X509KeyFormat t;
        try {
            RSA = new X509KeyFormat("RSA", CipherSuite.KEY_X509_RSA);
        } catch (NoSuchAlgorithmException e) {
            Helpers.athrow(e);
            throw null;
        }

        try {
            t = new X509KeyFormat("EC", CipherSuite.KEY_X509_EC);
        } catch (NoSuchAlgorithmException e) {
            t = null;
        }
        EC = t;

        try {
            t = new X509KeyFormat("DH", CipherSuite.KEY_X509_DH);
        } catch (NoSuchAlgorithmException e) {
            t = null;
        }
        DH = t;
    }

    private final KeyFactory factory;
    private final byte id;

    public X509KeyFormat(String alg, int fid) throws NoSuchAlgorithmException {
        factory = KeyFactory.getInstance(alg);
        id = (byte) fid;
    }

    public X509KeyFormat(KeyFactory factory, int fid) {
        this.factory = factory;
        id = (byte) fid;
    }

    @Override
    public String getAlgorithm() {
        return factory.getAlgorithm();
    }

    @Override
    public int formatId() {
        return id & 0xFF;
    }

    @Override
    public byte[] encode(PublicKey pub) {
        return pub.getEncoded();
    }

    @Override
    public MSSPubKey decode(byte[] data) throws GeneralSecurityException {
        return new JPubKey(factory.generatePublic(new X509EncodedKeySpec(data)));
    }
}
