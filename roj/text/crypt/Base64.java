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

import roj.text.CharList;
import roj.util.ByteList;

import java.util.Arrays;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2021/2/14 19:38
 */
public final class Base64 {
    // Int2IntBiMap ...
    public static final byte[] B64_CHAR = {
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
            'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
            'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '+', '/', '='
    };

    public static final byte[] B64_CHAR_REV = reverseOf(B64_CHAR, new byte[128]);

    public static byte[] reverseOf(byte[] in, byte[] out) {
        Arrays.fill(out, (byte) -1);
        for (int i = 0; i < in.length; i++)
            out[in[i]] = (byte) i;
        return out;
    }

    public static ByteList encode(ByteList in, ByteList out) {
        return encode(in, out, B64_CHAR);
    }

    public static ByteList encode(ByteList in, ByteList out, byte[] chars) {
        int bits, i = 0;

        int e = in.pos();
        int r = e % 3; // 0, 1 or 2
        e -= r;

        do {
            bits = in.getU(i++) << 16 | in.getU(i++) << 8 | in.getU(i++);

            // no for...
            out.add(chars[bits >> 18 & 0x3f]);
            out.add(chars[bits >> 12 & 0x3f]);
            out.add(chars[bits >> 6 & 0x3f]);
            out.add(chars[bits & 0x3f]);
        } while (i < e);

        if(r != 0) {
            int r1 = in.getU(i++);
            out.add(chars[r1 >> 2]);
            if (r == 1) {
                out.add(chars[(r1 << 4) & 0x3f]);
            } else {
                int r2 = in.getU(i);
                out.add(chars[(r1 << 4) & 0x3f | (r2 >> 4)]);
                out.add(chars[(r2 << 2) & 0x3f]);
            }

            for (r = 3 - r, i = 0; i < r; i++)
                out.add(chars[64]);
        }

        return out;
    }

    public static CharList encode(ByteList in, CharList out) {
        return encode(in, out, B64_CHAR);
    }

    public static CharList encode(ByteList in, CharList out, byte[] chars) {
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

    public static ByteList decode(CharSequence s, ByteList out) {
        return decode(s, 0, s.length(), out, B64_CHAR_REV);
    }

    public static ByteList decode(CharSequence s, int i, int len, ByteList out, byte[] bytesRev) {
        int o1, o2, h3, h4, bits;
        do {
            bits = bytesRev[s.charAt(i++)] << 18 | bytesRev[s.charAt(i++)] << 12;
            h3 = bytesRev[s.charAt(i++)];
            h4 = bytesRev[s.charAt(i++)];
            bits |= h3 << 6 | h4;

            o1 = bits >> 16 & 0xff;
            o2 = bits >> 8 & 0xff;

            if (h3 == 64) {
                out.add((byte) o1);
            } else if (h4 == 64) {
                out.add((byte) o1);
                out.add((byte) o2);
            } else {
                out.add((byte) o1);
                out.add((byte) o2);
                out.add((byte) bits);
            }
        } while (i < len);
        return out;
    }

    public static ByteList decode(ByteList in, ByteList out) {
        return decode(in, out, B64_CHAR_REV);
    }

    public static ByteList decode(ByteList in, ByteList out, byte[] bytesRev) {
        int o1, o2, h3, h4, bits;
        int i = 0;
        do {
            bits = bytesRev[in.getU(i++)] << 18 | bytesRev[in.getU(i++)] << 12;
            h3 = bytesRev[in.getU(i++)];
            h4 = bytesRev[in.getU(i++)];
            bits |= h3 << 6 | h4;

            o1 = bits >> 16 & 0xff;
            o2 = bits >> 8 & 0xff;

            if (h3 == 64) {
                out.add((byte) o1);
            } else if (h4 == 64) {
                out.add((byte) o1);
                out.add((byte) o2);
            } else {
                out.add((byte) o1);
                out.add((byte) o2);
                out.add((byte) bits);
            }
        } while (i < in.pos()); // support not pad
        return out;
    }

    public static CharList decode(CharSequence in, CharList out) {
        return decode(in, out, B64_CHAR_REV);
    }

    public static CharList decode(CharSequence in, CharList out, byte[] bytesRev) {
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
        } while (i < in.length()); // support not pad
        return out;
    }
}
