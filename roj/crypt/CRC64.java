/*
 * This file is a part of MoreItems
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2022 Roj234
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
package roj.crypt;

import java.util.zip.Checksum;

/**
 * @author Roj233
 * @since 2022/1/8 19:35
 */
public class CRC64 implements Checksum {
    static final long[] crc64_table = new long[256];

    static {
        int i;
        int bit;

        for (i = 0; i < 256; i++) {
            long c = i;

            for (bit = 0; bit < 8; bit++) {
                if ((c & 1) == 1) {
                    c = (c >>> 1 ^ 0xC96C5795D7870F42L);
                } else {
                    c >>>= 1;
                }
            }
            crc64_table[i] = c;
        }
    }

    private long v = -1L;

    public static long crc(long crc, byte b) {
        return (crc >> 8) ^ (crc64_table[(int) ((crc ^ b) & 0xff)]);
    }

    public static long crc(long crc, byte[] b, int off, int len) {
        len += off;
        final long[] table = crc64_table;
        for (int i = off; i < len; i++) {
            crc = (crc >> 8) ^ (table[(int) ((crc ^ b[i]) & 0xff)]);
        }
        return crc;
    }

    @Override
    public void update(int b) {
        v = crc(v, (byte) b);
    }

    @Override
    public void update(byte[] b, int off, int len) {
        v = crc(v, b, off, len);
    }

    @Override
    public long getValue() {
        return v;
    }

    @Override
    public void reset() {
        v = -1L;
    }
}
