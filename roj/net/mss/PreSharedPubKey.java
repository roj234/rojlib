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

import roj.text.TextUtil;

import java.security.InvalidKeyException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateException;

/**
 * @author Roj233
 * @version 0.1
 * @since 2021/12/22 12:53
 */
public final class PreSharedPubKey implements MSSPubKey<PublicKey> {
    private final PublicKey[] keys;

    public PreSharedPubKey(PublicKey... keys) {
        assert keys.length > 0;
        this.keys = keys;
    }

    @Override
    public String name() {
        return "PreSharedKey";
    }

    @Override
    public int specificationId() {
        return 0x10100000;
    }

    @Override
    public byte[] encode(PublicKey pk) throws InvalidKeyException {
        for (int i = 0; i < keys.length; i++) {
            if (pk == keys[i]) {
                return new byte[] { (byte) i };
            }
        }
        throw new InvalidKeyException("This key is not supported.");
    }

    @Override
    public PublicKey decode(byte[] data) throws CertificateException {
        if (data.length != 1)
            throw new CertificateException("无效公钥 " + TextUtil.dumpBytes(data));
        return keys[data[0] & 0xFF];
    }

    @Override
    public void checkPrivateKey(PrivateKey key) {}
}
