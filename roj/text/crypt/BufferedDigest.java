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
package roj.text.crypt;

import java.security.DigestException;
import java.security.MessageDigest;

/**
 * Your description here
 *
 * @author solo6975
 * @version 0.1
 * @since 2021/10/3 15:06
 */
public abstract class BufferedDigest extends MessageDigest {
    protected final int[]  intBuffer;
    protected final byte[] byteBuffer;
    protected int          bufOff;

    protected BufferedDigest(String algorithm) {
        super(algorithm);
        this.intBuffer = new int[engineGetIntBufferLength()];
        this.byteBuffer = new byte[intBuffer.length << 2];
    }

    protected BufferedDigest(String algorithm, int byteLen) {
        super(algorithm);
        this.intBuffer = new int[engineGetIntBufferLength()];
        this.byteBuffer = new byte[byteLen];
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected abstract int engineDigest(byte[] buf, int offset, int len) throws DigestException;

    /**
     * {@inheritDoc}
     */
    @Override
    protected int engineGetDigestLength() {
        return engineGetIntBufferLength() << 2;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected final void engineUpdate(byte input) {
        byteBuffer[bufOff++] = input;
        if (bufOff == byteBuffer.length) {
            Conv.b2i(byteBuffer, 0, byteBuffer.length, intBuffer, 0);
            engineIntDigest();
            bufOff = 0;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected final void engineUpdate(byte[] input, int off, int len) {
        int max = byteBuffer.length;
        int bufOff = this.bufOff;
        if(bufOff != 0) {
            int require = Math.min(max - bufOff, len);
            System.arraycopy(input, off, byteBuffer, bufOff, require);
            if ((this.bufOff = (bufOff + require)) < max) {
                return;
            }
            Conv.b2i(byteBuffer, 0, max, intBuffer, 0);
            engineIntDigest();
            this.bufOff = 0;
            off += require;
            len -= require;
        }

        int r = len & (max - 1);
        len -= r;
        for (len += off; off < len; off += max) {
            Conv.b2i(input, off, max, intBuffer, 0);
            engineIntDigest();
        }
        this.bufOff = r;
        if(r > 0) {
            System.arraycopy(input, off, byteBuffer, 0, r);
        }
    }

    protected abstract void engineIntDigest();

    protected abstract int engineGetIntBufferLength();

    protected final void engineFinish() {
        if (bufOff != 0) {
            Conv.b2i(byteBuffer, 0, bufOff, intBuffer, 0);
            engineIntDigest();
            bufOff = 0;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected byte[] engineDigest() {
        try {
            byte[] buf = new byte[engineGetDigestLength()];
            engineDigest(buf, 0, buf.length);
            return buf;
        } catch (DigestException e) {
            throw new IllegalStateException("Should not happen", e);
        }
    }
}
