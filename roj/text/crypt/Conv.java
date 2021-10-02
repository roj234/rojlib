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

import roj.text.TextUtil;

/**
 * Your description here
 *
 * @author solo6975
 * @version 0.1
 * @since 2021/10/3 11:12
 */
public final class Conv {
    public static int b2i(byte[] src, int sOff) {
        return (src[sOff] & 0xFF) << 24 | (src[sOff + 1] & 0xFF) << 16 | (src[sOff + 2] & 0xFF) << 8 | (src[sOff + 3] & 0xFF);
    }
    public static long b2i_l(byte[] src, int sOff) {
        return 0xFFFFFFFFL & ((src[sOff] & 0xFF) << 24 | (src[sOff + 1] & 0xFF) << 16 | (src[sOff + 2] & 0xFF) << 8 | (src[sOff + 3] & 0xFF));
    }
    public static void i2b(byte[] dst, int dOff, int n) {
        dst[dOff    ] = (byte) (n >> 24);
        dst[dOff + 1] = (byte) (n >> 16);
        dst[dOff + 2] = (byte) (n >> 8);
        dst[dOff + 3] = (byte) n;
    }

    public static int[] b2i(byte[] src, int sOff, int len, int[] dst, int dOff) {
        int more = len & 3;
        if(dst.length - dOff < len / 4 + (more > 0 ? 1 : 0))
            throw new ArrayIndexOutOfBoundsException();

        len -= more;
        len += sOff;
        while (sOff < len) {
            dst[dOff++] = (src[sOff] & 0xFF) << 24 | (src[sOff + 1] & 0xFF) << 16 | (src[sOff + 2] & 0xFF) << 8 | (src[sOff + 3] & 0xFF);
            sOff += 4;
        }
        if(more != 0) {
            len += more;
            int n = 0, sh = 24;
            while (sOff < len) {
                n |= (src[sOff++] & 0xFF) << sh;
                sh -= 8;
            }
            dst[dOff] = n;
        }
        return dst;
    }

    public static byte[] i2b(int[] src, int sOff, int len, byte[] dst, int dOff) {
        if(dst.length < len << 2)
            throw new ArrayIndexOutOfBoundsException();
        for (len += sOff; sOff < len; sOff++) {
            int n = src[sOff];
            dst[dOff    ] = (byte) (n >> 24);
            dst[dOff + 1] = (byte) (n >> 16);
            dst[dOff + 2] = (byte) (n >> 8);
            dst[dOff + 3] = (byte) n;
            dOff += 4;
        }
        return dst;
    }

    // Int Rotate Left
    public static int IRL(int n, int bit) {
        return (n << bit) | (n >>> bit);
    }

    public static int[] reverse(int[] arr, int i, int length) {
        if (--length <= 0)
            return arr; // empty or one
        // i = 0, arr.length = 4, e = 2
        // swap 0 and 3 swap 1 and 2
        for (int e = Math.max((length + 1) >> 1, 1); i < e; i++) {
            int a = arr[i];
            arr[i] = arr[length - i];
            arr[length - i] = a;
        }
        return arr;
    }

    public static byte[] hex2bytes(CharSequence hex) {
        int length = hex.length() / 2;
        byte[] d = new byte[length];
        for (int i = 0; i < length; i++) {
            d[i] = (byte) ((TextUtil.c2i_hex(hex.charAt(i << 1)) << 4) | TextUtil.c2i_hex(hex.charAt(1 + (i << 1))));
        }
        return d;
    }
}
