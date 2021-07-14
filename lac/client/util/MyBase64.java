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
package lac.client.util;

import lac.server.note.DefaultObfuscatePolicy;

import java.util.Arrays;
import java.util.Random;

/**
 * Base64
 *
 * @author Roj234
 * @version 0.1
 * @since  2021/2/14 19:38
 */
@DefaultObfuscatePolicy(onlyHaveStatic = true)
public final class MyBase64 {
    public static byte[] TABLEENC, TABLEDEC;

    public static void shuffle(byte[] arr, Random random) {
        for (int i = 0; i < arr.length; i++) {
            byte a = arr[i];
            int an = random.nextInt(arr.length);
            arr[i] = arr[an];
            arr[an] = a;
        }
    }

    public static byte[] reverseOf(byte[] in, byte[] out) {
        Arrays.fill(out, (byte) -1);
        for (int i = 0; i < in.length; i++)
            out[in[i]] = (byte) i;
        return out;
    }

    public static StringBuilder encode(ByteList in, StringBuilder out, byte[] chars) {
        int bits, i = 0;

        int e = in.pos();
        int r = e % 3; // 0, 1 or 2
        e -= r;

        do {
            bits = in.getU(i++) << 16 | in.getU(i++) << 8 | in.getU(i++);

            // no for...
            out.append((char) chars[bits >> 18 & 0x3f])
            .append((char) chars[bits >> 12 & 0x3f])
            .append((char) chars[bits >> 6 & 0x3f])
            .append((char) chars[bits & 0x3f]);
        } while (i < e);

        if(r != 0) {
            int r1 = in.getU(i++);
            out.append((char) chars[r1 >> 2]);
            if (r == 1) {
                out.append((char) chars[(r1 << 4) & 0x3f]);
            } else {
                int r2 = in.getU(i);
                out.append((char) chars[(r1 << 4) & 0x3f | (r2 >> 4)])
                        .append((char) chars[(r2 << 2) & 0x3f]);
            }

            for (r = 3 - r, i = 0; i < r; i++)
                out.append((char) chars[64]);
        }

        return out;
    }

    public static StringBuilder decode(CharSequence in, StringBuilder out, byte[] bytesRev) {
        int o1, o2, h3, h4, bits;
        int i = 0;
        do {
            bits = bytesRev[in.charAt(i++)] << 18 | bytesRev[in.charAt(i++)] << 12;
            h3 = bytesRev[in.charAt(i++)];
            h4 = bytesRev[in.charAt(i++)];
            bits |= h3 << 6 | h4;

            o1 = bits >> 16 & 0xff;
            o2 = bits >> 8 & 0xff;

            if (h3 == 64) {
                out.append((char) o1);
            } else if (h4 == 64) {
                out.append((char) o1);
                out.append((char) o2);
            } else {
                out.append((char) o1);
                out.append((char) o2);
                out.append((char) bits);
            }
        } while (i < in.length()); // supports no-pad
        return out;
    }
}
