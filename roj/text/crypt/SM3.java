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
import java.util.Arrays;

/**
 * 国密SM3 - 校验码
 */
public final class SM3 extends BufferedDigest {
    public static final int Tj1 = 0x79cc4519, Tj2 = 0x7a879d8a;

    private final int[] digest = new int[8];

    public SM3() {
        super("SM3", 256);
        engineReset();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected int engineDigest(byte[] buf, int offset, int len) throws DigestException {
        if (len < 32) {
            throw new DigestException("partial digest cannot be returned");
        }
        engineFinish();
        Conv.i2b(digest, 0, 8, buf, offset);
        engineReset();
        return 32;
    }

    @Override
    protected int engineGetDigestLength() {
        return 32;
    }

    @Override
    protected void engineIntDigest() {
        digest(digest, intBuffer);
    }

    @Override
    protected int engineGetIntBufferLength() {
        return 132;
    }

    /**
     * Resets the digest for further use.
     */
    @Override
    protected void engineReset() {
        bufOff = 0;
        Arrays.fill(digest, 0);
    }

    // W.length should be 132 (64 + 68), once process 64 ints (256 bytes)
    public static void digest(int[] digest, int[] W) {
        for (int i = 16; i < 68; i++) {
            W[i] = P1(W[i - 16] ^ W[i - 9] ^ Conv.IRL(W[i - 3], 15))
                    ^ Conv.IRL(W[i - 13], 7) ^ W[i - 6];
        }

        for (int i = 68; i < 132; i++) {
            W[i] = W[i - 68] ^ W[i - 64];
        }

        int t1, t2;
        int a = digest[0];
        int b = digest[1];
        int c = digest[2];
        int d = digest[3];
        int e = digest[4];
        int f = digest[5];
        int g = digest[6];
        int h = digest[7];

        int i = 0;
        for (; i < 16; i++) {
            t1 = Conv.IRL(Conv.IRL(a, 12) + e + Conv.IRL(Tj1, i), 7);
            t2 = t1 ^ Conv.IRL(a, 12);

            t2 = FF1(a, b, c) + d + t2 + W[i + 68];
            d = c;
            c = Conv.IRL(b, 9);
            b = a;
            a = t2;

            t2 = FF1(e, f, g) + h + t1 + W[i];
            h = g;
            g = Conv.IRL(f, 19);
            f = e;
            e = P0(t2);
        }
        for (; i < 64; i++) {
            t1 = Conv.IRL(Conv.IRL(a, 12) + e + Conv.IRL(Tj2, i), 7);
            t2 = t1 ^ Conv.IRL(a, 12);

            t2 = FF1(a, b, c) + d + t2 + W[i + 68];
            d = c;
            c = Conv.IRL(b, 9);
            b = a;
            a = t2;

            t2 = FF1(e, f, g) + h + t1 + W[i];
            h = g;
            g = Conv.IRL(f, 19);
            f = e;
            e = P0(t2);
        }

        digest[0] ^= a;
        digest[1] ^= b;
        digest[2] ^= c;
        digest[3] ^= d;
        digest[4] ^= e;
        digest[5] ^= f;
        digest[6] ^= g;
        digest[7] ^= h;
    }

    private static int FF1(int X, int Y, int Z) {
        return X ^ Y ^ Z;
    }

    private static int FF2(int X, int Y, int Z) {
        return ((X & Y) | (X & Z) | (Y & Z));
    }

    private static int GG(int X, int Y, int Z) {
        return (X & Y) | (~X & Z);
    }

    private static int P0(int X) {
        return X ^ Conv.IRL(X, 9) ^ Conv.IRL(X, 17);
    }

    private static int P1(int X) {
        return X ^ Conv.IRL(X, 15) ^ Conv.IRL(X, 23);
    }
}
