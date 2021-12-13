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
package roj.crypt;

import javax.crypto.BadPaddingException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.Arrays;

import static roj.crypt.Conv.IRL;

/**
 * 国密SM4 - 对称加解密
 */
public final class SM4 implements CipheR {
    public static final String SM4_IV = "SM4_IV";

    public static final int SM4_CHUNKED = 0, SM4_STREAMED = 2, SM4_PADDING = 4;

    private final int[] sKey = new int[32];
    private final int[] tmp  = new int[36];

    private int mode;
    private final ByteBuffer iv = ByteBuffer.allocate(16);

    public SM4() {}

    @Override
    public String name() {
        return "SM4 1.1.2";
    }

    @Override
    public void reset(int cryptMode) {
        this.mode = cryptMode;
        Arrays.fill(sKey, 0);
        iv.putLong(0, 0).putLong(8, 0);
    }

    @Override
    public void setKey(byte[] pass) {
        if(pass.length == 0 || pass.length > 128)
            throw new IllegalArgumentException("pass.length should be (0, 128]!");
        sm4_set_key(tmp, sKey, pass, 0, pass.length);
        if ((mode & DECRYPT) != 0)
            Conv.reverse(sKey, 0, 32);
    }

    @Override
    public void setOption(String key, Object value) {
        if(key.equals(SM4_IV)) {
            byte[] iv = ((byte[]) value);
            if (iv.length != 16)
                throw new IllegalArgumentException("iv.length != 16");
            this.iv.clear();
            this.iv.put(iv);
        }
    }

    @Override
    public int getBlockSize() {
        return 16;
    }

    public static void pad(ByteBuffer buf, boolean unpad) throws BadPaddingException {
        if (unpad) {
            for (int i = buf.position() - 1; i >= 0; i--) {
                byte b = buf.get(i);
                if(b == (byte) 0x80) {
                    buf.position(i);
                    break;
                } else if (b != 0)
                    throw new BadPaddingException();
            }
        } else {
            int lim = buf.limit();
            int p = 16 - (lim & 15);
            buf.limit(lim + p);
            buf.put(lim++, (byte) 0x80);
            while (--p > 0) buf.put(lim++, (byte) 0);
        }
    }

    @Override
    public int crypt(ByteBuffer in, ByteBuffer out) throws GeneralSecurityException {
        if ((mode & (SM4_PADDING | DECRYPT)) == SM4_PADDING) {
            int p = 16 - in.remaining() & 15;
            if (in.capacity() - in.limit() < p)
                return BUFFER_UNDERFLOW;
            if (out.remaining() < in.remaining() + p)
                return BUFFER_OVERFLOW;
            pad(in, false);
        }

        if ((in.remaining() & 15) != 0)
            throw new BadPaddingException(in.remaining() + ", excepting n*16");
        if (out.remaining() < in.remaining())
            return BUFFER_OVERFLOW;

        int k = in.remaining() >> 4;

        int[] T = this.tmp;
        int[] sKey = this.sKey;

        ByteBuffer iv = this.iv;
        if ((mode & DECRYPT) == 0) {
            ByteBuffer clone = out.duplicate();

            while (k-- > 0) {
                T[0] = in.getInt() ^ iv.getInt(); T[1] = in.getInt() ^ iv.getInt();
                T[2] = in.getInt() ^ iv.getInt(); T[3] = in.getInt() ^ iv.getInt();
                int i = 0;
                while (i < 32) {
                    T[i + 4] = T[i] ^ sm4_Lt(T[i + 1] ^ T[i + 2] ^ T[i + 3] ^ sKey[i]);
                    i++;
                }
                out.putInt(T[35]).putInt(T[34]).putInt(T[33]).putInt(T[32]);

                iv = clone;
            }

            this.iv.clear();
            if ((mode & SM4_STREAMED) != 0) {
                iv.limit(iv.position())
                  .position(iv.position() - 16);
                this.iv.put(iv);
            }
        } else {
            int iv0 = iv.getInt(), iv1 = iv.getInt(),
                iv2 = iv.getInt(), iv3 = iv.getInt();

            boolean pad = (mode & SM4_PADDING) != 0;
            while (k-- > 0) {
                T[0] = in.getInt(); T[1] = in.getInt(); T[2] = in.getInt(); T[3] = in.getInt();
                int i = 0;
                while (i < 32) {
                    T[i + 4] = T[i] ^ sm4_Lt(T[i + 1] ^ T[i + 2] ^ T[i + 3] ^ sKey[i]);
                    i++;
                }

                out.putInt(T[35] ^ iv0).putInt(T[34] ^ iv1).putInt(T[33] ^ iv2).putInt(T[32] ^ iv3);
                iv0 = T[0]; iv1 = T[1]; iv2 = T[2]; iv3 = T[3];
            }

            this.iv.clear();
            if ((mode & SM4_STREAMED) != 0) {
                this.iv.putInt(iv0).putInt(iv1).putInt(iv2).putInt(iv3);
            }

            if (pad) {
                pad(out, true);
            }
        }
        return OK;
    }

    private static final byte[] SBOX = {
            -42,-112,-23,-2,-52,-31,61,-73,22,-74,20,-62,40,-5,44,5,43,103,-102,118,42,-66,4,-61,-86,68,19,38,73,-122,
            6,-103,-100,66,80,-12,-111,-17,-104,122,51,84,11,67,-19,-49,-84,98,-28,-77,28,-87,-55,8,-24,-107,-128,-33,
            -108,-6,117,-113,63,-90,71,7,-89,-4,-13,115,23,-70,-125,89,60,25,-26,-123,79,-88,104,107,-127,-78,113,100,
            -38,-117,-8,-21,15,75,112,86,-99,53,30,36,14,94,99,88,-47,-94,37,34,124,59,1,33,120,-121,-44,0,70,87,-97,
            -45,39,82,76,54,2,-25,-96,-60,-56,-98,-22,-65,-118,-46,64,-57,56,-75,-93,-9,-14,-50,-7,97,21,-95,-32,-82,
            93,-92,-101,52,26,85,-83,-109,50,48,-11,-116,-79,-29,29,-10,-30,46,-126,102,-54,96,-64,41,35,-85,13,83,78,
            111,-43,-37,55,69,-34,-3,-114,47,3,-1,106,114,109,108,91,81,-115,27,-81,-110,-69,-35,-68,127,17,-39,92,65,
            31,16,90,-40,10,-63,49,-120,-91,-51,123,-67,45,116,-48,18,-72,-27,-76,-80,-119,105,-105,74,12,-106,119,126,
            101,-71,-15,9,-59,110,-58,-124,24,-16,125,-20,58,-36,77,32,121,-18,95,62,-41,-53,57,72
    };

    private static final int[] CK = {
            0x00070e15, 0x1c232a31, 0x383f464d, 0x545b6269,
            0x70777e85, 0x8c939aa1, 0xa8afb6bd, 0xc4cbd2d9,
            0xe0e7eef5, 0xfc030a11, 0x181f262d, 0x343b4249,
            0x50575e65, 0x6c737a81, 0x888f969d, 0xa4abb2b9,
            0xc0c7ced5, 0xdce3eaf1, 0xf8ff060d, 0x141b2229,
            0x30373e45, 0x4c535a61, 0x686f767d, 0x848b9299,
            0xa0a7aeb5, 0xbcc3cad1, 0xd8dfe6ed, 0xf4fb0209,
            0x10171e25, 0x2c333a41, 0x484f565d, 0x646b7279
    };

    private static int sm4_Lt(int ia) {
        int b =  (SBOX[(ia >> 24) & 0xFF] << 24) |
                 (SBOX[(ia >> 16) & 0xFF] << 16) |
                 (SBOX[(ia >> 8 ) & 0xFF] <<  8) |
                 (SBOX[(ia      ) & 0xFF]      );
        return b ^ IRL(b, 2) ^ IRL(b, 10) ^ IRL(b, 18) ^ IRL(b, 24);
    }

    private static int sm4_iRK(int ia) {
        int b =  (SBOX[(ia >> 24) & 0xFF] << 24) |
                 (SBOX[(ia >> 16) & 0xFF] << 16) |
                 (SBOX[(ia >> 8 ) & 0xFF] <<  8) |
                 (SBOX[(ia      ) & 0xFF]      );
        return b ^ IRL(b, 13) ^ IRL(b, 23);
    }

    private static void sm4_set_key(int[] tmp, int[] skey, byte[] key, int keyOff, int keyLen) {
        Arrays.fill(tmp, 0);
        Conv.b2i(key, keyOff, keyLen, tmp, 0);
        // FK[0, 4]
        for (int i = 0; i < 32; i++) {
            tmp[i    ] ^= 0xa3b1bac6;
            tmp[i + 1] ^= 0x56aa3350;
            tmp[i + 2] ^= 0x677d9197;
            tmp[i + 3] ^= 0xb27022dc;
            skey[i] = tmp[i + 4] = tmp[i] ^ sm4_iRK(tmp[i + 1] ^ tmp[i + 2] ^ tmp[i + 3] ^ CK[i]);
        }
    }
}
