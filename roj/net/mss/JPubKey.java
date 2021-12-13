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

import java.security.*;
import java.security.spec.X509EncodedKeySpec;

/**
 * @author Roj233
 * @version 0.1
 * @since 2021/12/22 12:53
 */
public final class JPubKey implements MSSPubKey<PublicKey> {
    public static final JPubKey JAVARSA;

    static {
        try {
            JAVARSA = new JPubKey("RSA");
        } catch (NoSuchAlgorithmException e) {
            Helpers.athrow(e);
            throw (RuntimeException)(Throwable) e;
        }
    }

    private final KeyFactory factory;

    public JPubKey(String alg) throws NoSuchAlgorithmException {
        factory = KeyFactory.getInstance(alg);
    }

    public JPubKey(KeyFactory factory) {
        this.factory = factory;
    }

    @Override
    public String name() {
        return factory.getAlgorithm();
    }

    @Override
    public int specificationId() {
        return factory.getAlgorithm().hashCode() & 0xFFF;
    }

    @Override
    public byte[] encode(PublicKey publicKey) throws GeneralSecurityException {
        return publicKey.getEncoded();
    }

    @Override
    public PublicKey decode(byte[] data) throws GeneralSecurityException {
        return factory.generatePublic(new X509EncodedKeySpec(data));
    }

    @Override
    public void checkPrivateKey(PrivateKey privateKey) throws GeneralSecurityException {
        if (!privateKey.getAlgorithm().equals(factory.getAlgorithm()))
            throw new GeneralSecurityException("Invalid private key");
    }
}
