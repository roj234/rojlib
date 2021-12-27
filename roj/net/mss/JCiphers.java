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

import roj.crypt.CipheR;
import roj.util.EmptyArrays;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;

/**
 * @author Roj233
 * @version 0.1
 * @since 2021/12/22 19:18
 */
public final class JCiphers implements MSSCiphers {
    public static final JCiphers AES_CFB8 = new JCiphers("AES/CFB8/NoPadding", 32);

    private final String alg;
    private final int sharedKeySize;

    public JCiphers(String alg, int sharedKeySize) {
        this.alg = alg;
        this.sharedKeySize = sharedKeySize;
    }

    @Override
    public String name() {
        return "AES";
    }

    @Override
    public int specificationId() {
        return 0x00000015;
    }

    @Override
    public int getSharedKeySize() {
        return sharedKeySize;
    }

    @Override
    public CipheR createEncoder() throws GeneralSecurityException {
        return new Delegate(alg, (byte) Cipher.ENCRYPT_MODE);
    }

    @Override
    public CipheR createDecoder() throws GeneralSecurityException {
        return new Delegate(alg, (byte) Cipher.DECRYPT_MODE);
    }

    private static final class Delegate implements CipheR {
        byte[] tmp;
        private final Cipher cip;
        private final byte mode;
        Delegate(String name, byte mode) throws GeneralSecurityException {
            this.cip = Cipher.getInstance(name);
            this.mode = mode;
            this.tmp = EmptyArrays.BYTES;
        }

        @Override
        public String name() {
            return cip.getAlgorithm();
        }

        @Override
        public void setKey(byte[] key, int flags) {
            try {
                cip.init(mode, new SecretKeySpec(key, cip.getAlgorithm().substring(0, cip.getAlgorithm().indexOf('/'))), new IvParameterSpec(new byte[16]));
            } catch (InvalidKeyException | InvalidAlgorithmParameterException e) {
                e.printStackTrace();
            }
        }

        @Override
        public int crypt(ByteBuffer in, ByteBuffer out) throws GeneralSecurityException {
            int rm = in.remaining();
            if (out.remaining() < rm) return CipheR.BUFFER_OVERFLOW;
            if (tmp.length < rm)
                tmp = new byte[rm];
            if (in.hasArray()) {
                int len = cip.doFinal(in.array(), in.arrayOffset() + in.position(), in.remaining(), tmp);
                in.position(in.limit());
                out.put(tmp, 0, len);
            } else {
                int r = in.remaining();
                if (tmp.length < rm + r)
                    tmp = new byte[rm + r];
                in.get(tmp, 0, r);
                int len = cip.doFinal(tmp, 0, r, tmp, r);
                out.put(tmp, r, len);
            }
            return CipheR.OK;
        }
    }
}
