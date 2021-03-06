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
import roj.util.ByteList;
import roj.util.EmptyArrays;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * @author Roj233
 * @since 2021/12/22 19:18
 */
public final class JCiphers implements MSSCiphers {
    private final String alg;
    private final int keySize;

    public JCiphers(String alg, int sharedKeySize) {
        this.alg = alg;
        this.keySize = sharedKeySize;
    }

    @Override
    public int getKeySize() {
        return keySize;
    }

    @Override
    public CipheR createEncoder() {
        return new Delegate(alg, (byte) Cipher.ENCRYPT_MODE);
    }

    @Override
    public CipheR createDecoder() {
        return new Delegate(alg, (byte) Cipher.DECRYPT_MODE);
    }

    private static final class Delegate implements CipheR {
        byte[] tmp;
        private final Cipher cip;
        private final byte mode;
        Delegate(String name, byte mode) {
            try {
                this.cip = Cipher.getInstance(name);
            } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
                throw new IllegalStateException("Unable to create the Cipher", e);
            }
            this.mode = mode;
            this.tmp = EmptyArrays.BYTES;
        }

        @Override
        public String name() {
            return cip.getAlgorithm();
        }

        @Override
        public void setKey(byte[] key, int flags) {
            SecretKeySpec sk = new SecretKeySpec(key, cip.getAlgorithm().substring(0, cip.getAlgorithm().indexOf('/')));
            try {
                cip.init(mode, sk, new IvParameterSpec(Arrays.copyOf(key, 16)));
            } catch (InvalidKeyException | InvalidAlgorithmParameterException e) {
                e.printStackTrace();
                System.out.println("Failed to initialize cipher");
            }
        }

        @Override
        public int crypt(ByteBuffer in, ByteBuffer out) throws GeneralSecurityException {
            int rm = in.remaining();
            if (out.remaining() < rm) return CipheR.BUFFER_OVERFLOW;
            if (tmp.length < rm)
                tmp = new byte[rm];
            if (in.hasArray()) {
                int len = cip.update(in.array(), in.arrayOffset() + in.position(), in.remaining(), tmp);
                in.position(in.limit());
                out.put(tmp, 0, len);
            } else {
                int r = in.remaining();
                if (tmp.length < rm + r)
                    tmp = new byte[rm + r];
                in.get(tmp, 0, r);
                int len = cip.update(tmp, 0, r, tmp, r);
                out.put(tmp, r, len);
            }
            return CipheR.OK;
        }

        @Override
        public void crypt(ByteList in, ByteList out) throws GeneralSecurityException {
            out.ensureCapacity(in.wIndex());
            int len = cip.update(in.list, in.arrayOffset(), in.wIndex(), out.list, out.wIndex());
            out.wIndex(out.wIndex() + len);
        }
    }
}
