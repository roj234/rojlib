/*
 * This file is a part of MI
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

import java.util.zip.Checksum;

/**
 * CRC32 not in c++ (Read only)
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/12/5 19:53
 */
public class CRC32 implements Checksum {
    static final int[] crc32_table = new int[256];

    static {
        int i;
        int bit;

        for (i = 0; i < 256; i++) {
            int c = i;

            for (bit = 0; bit < 8; bit++) {
                if ((c & 1) == 1) {
                    c = (c >>> 1) ^ 0xEDB88320;
                } else {
                    c = c >>> 1;
                }

            }
            crc32_table[i] = c;
        }
    }

    private int v;

    public static int crc(int crc, byte b) {
        return (crc >> 8) ^ (crc32_table[(crc ^ b) & 0xff]);
    }

    public static int crc(int crc, byte[] b, int off, int len) {
        len += off;
        final int[] table = crc32_table;
        for (int i = off; i < len; i++) {
            crc = (crc >> 8) ^ (table[(crc ^ b[i]) & 0xff]);
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
        v = 0;
    }
}
