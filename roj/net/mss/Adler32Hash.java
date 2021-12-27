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

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.zip.Adler32;

/**
 * @author Roj233
 * @version 0.1
 * @since 2021/12/29 16:03
 */
public class Adler32Hash extends Adler32 implements MSSHash {
    private final ByteBuffer tmp4a = ByteBuffer.allocate(4);

    @Override
    public int length() {
        return 4;
    }

    @Override
    public void writeHash(ByteBuffer payload, ByteBuffer outBuf, CipheR encoder) throws GeneralSecurityException {
        reset();
        int p = payload.position();
        update(payload);
        payload.position(p);

        tmp4a.putInt((int) getValue());
        tmp4a.clear();
        encoder.crypt(tmp4a, outBuf);
        tmp4a.clear();
    }

    @Override
    public Object readHash(ByteBuffer inBuf, CipheR decoder) throws GeneralSecurityException {
        int lim = inBuf.limit();
        inBuf.limit(inBuf.position() + 4);
        decoder.crypt(inBuf, tmp4a);
        tmp4a.clear();
        inBuf.limit(lim);

        return tmp4a;
    }

    @Override
    public boolean checkHash(Object _hash, ByteBuffer payload) {
        if (_hash != tmp4a) throw new IllegalArgumentException();
        int hash = tmp4a.getInt();
        tmp4a.clear();

        reset();
        int p = payload.position();
        update(payload);
        payload.position(p);

        return hash == (int) getValue();
    }

    @Override
    public int computeHandshakeHash(byte[] b) {
        reset();
        update(b, 0, b.length);
        return (int) getValue();
    }
}
