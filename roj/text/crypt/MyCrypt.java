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

import roj.math.MathUtils;
import roj.util.ByteList;

import java.security.DigestException;
import java.security.MessageDigest;

/**
 * 一种加密算法
 *
 * @author zsy
 * @version 1.0.3
 * @since 2021/6/19 10:07
 */
public final class MyCrypt implements ICrypt {
    private static int myHash(ByteList s) {
        if(s.pos() == 0)
            return 0;

        int hash = 1, sum = 0;
        for (int i = 0; i < s.pos(); i++) {
            hash = 31 * hash + s.get(i);
            sum += s.get(i);
        }

        return hash ^ sum;
    }

    private static int revMix(int a, int b) {
        int v = 0;
        for (int i = 0; i < 32; i++) {
            v |= (((a >>> i) ^ (b >>> (31 - i))) & 1) << i;
        }
        return v;
    }

    private final char[] box;
    private final byte flag;
    private final MessageDigest digest;
    private final byte[]        digestCache;

    public MyCrypt() {
        this(new NotMd5(), 512);
    }

    public MyCrypt(int boxSize) {
        this(new NotMd5(), boxSize);
    }

    public MyCrypt(MessageDigest myDigest, int boxSize) {
        if (MathUtils.getMin2PowerOf(boxSize) != boxSize)
            throw new IllegalArgumentException("boxSize 不是二的幂 " + boxSize);
        this.flag = 0;
        this.box = new char[boxSize];
        this.digest = myDigest;
        this.digestCache = myDigest == null ? null : new byte[myDigest.getDigestLength()];
    }

    public MyCrypt(ByteList initial_forever_password, int boxSize) {
        if (MathUtils.getMin2PowerOf(boxSize) != boxSize)
            throw new IllegalArgumentException("boxSize 不是二的幂 " + boxSize);
        this.flag = 1;
        this.box = new char[boxSize];
        this.digest = null;
        this.digestCache = null;
        reset();
        swap(box, (int) System.currentTimeMillis(), initial_forever_password);
    }

    public void reset() {
        char[] box = this.box;
        final int mask = box.length - 1;
        for (int i = 0; i < box.length; i++) {
            box[i] = (char) (i & mask);
        }
    }

    /**
     * 交换box位置
     */
    private static void swap(char[] box, int offX, ByteList rnd) {
        int mask = box.length - 1;
        int j = 0;
        if(rnd.pos() <= mask) {
            int off = offX & 0xFFFF;
            for (int i = (mask + 1) >> 1; i <= mask; i++) {
                int c = rnd.get((i + off) % rnd.pos()) ^ off;

                j = (j + box[i] + c) & mask;

                char tmp = box[i];
                box[i] = box[j];
                box[j] = tmp;
            }
            off = offX >>> 16;
            int v = (mask + 1) >> 1;
            for (int i = 0; i < v; i++) {
                int c = rnd.get((i + off) % rnd.pos()) ^ off;

                j = (j + box[i] + c) & mask;

                char tmp = box[i];
                box[i] = box[j];
                box[j] = tmp;
            }
        } else {
            int off = offX & 0xFFFF;
            int off2 = offX >>> 16;
            for (int i = 0; i < rnd.pos(); i++) {
                int c = rnd.get((i + off) % rnd.pos()) ^ off2;

                int k = i & mask;

                j = (j + box[k] + c) & mask;

                char tmp = box[k];
                box[k] = box[j];
                box[j] = tmp;
            }
        }
    }

    private static void crypt(byte[] in, int i, int off, int len, ByteList out, char[] box) {
        int mask = box.length - 1;
        int j = 0;
        while (off < len) {
            int boxIdx1 = i++ & mask;
            char tmp = box[boxIdx1];
            box[boxIdx1] = box[j];
            box[j] = tmp;
            j = (j + tmp) & mask;

            out.add((byte) (in[off++] ^ (box[(box[boxIdx1] + tmp) & mask] & 255)));
        }
    }

    @Override
    public String name() {
        return "MyCrypt 1.1.1";
    }

    @Override
    public ByteList encrypt(ByteList in, ByteList pass, ByteList out) throws DigestException {
        int rndPar1 = (int) System.currentTimeMillis();
        int op = in.offset() + in.pos();
        for (int i = 31; i >= 0; i--) {
            rndPar1 = 31 * rndPar1 ^ (in.get(i % op) << (pass.get(i % pass.pos()) & 31));
        }
        // 上面只是随机化

        if ((flag & 1) == 0) {
            // 初始化交换盒子
            reset();
            // 打乱盒子
            swap(box, revMix(myHash(pass), rndPar1), pass);
        }

        // 总大小
        if (digest != null) {
            digest.reset();
            digest.update(in.list, in.offset(), op);
            digest.digest(digestCache, 0, digestCache.length);
        }

        // 把随机时间参数写入输出
        out.add((byte) (rndPar1 >>> 24));
        out.add((byte) (rndPar1 >>> 16));
        int pos = op + 4;
        if(pos > 63) {
            crypt(in.list, rndPar1 >> 8, in.offset(), op / 2, out, box);
            out.add((byte) (rndPar1 >>> 8)); // pos / 2

            crypt(in.list, rndPar1 - 123, in.offset() + op / 2, op, out, box);
            out.add((byte) rndPar1);
        } else {
            out.add((byte) (rndPar1 >>> 8));
            out.add((byte) rndPar1);

            crypt(in.list, -rndPar1, in.offset(), op, out, box);
        }
        if(digestCache != null)
            crypt(digestCache, rndPar1, 0, digestCache.length, out, box);
        return out;
    }

    @Override
    public ByteList decrypt(ByteList in, ByteList pass, ByteList out) throws DigestException {
        int pos = in.offset() + in.pos();
        if (digest != null)
            pos -= digestCache.length;

        int rndPar1;

        if(pos > 63) {
            rndPar1 = ((in.get(0) & 0xFF) << 24) | ((in.get(1) & 0xFF) << 16) | ((in.get(pos / 2) & 0xFF) << 8) | (in.get(pos - 1) & 0xFF);
        } else {
            rndPar1 = ((in.get(0) & 0xFF) << 24) | ((in.get(1) & 0xFF) << 16) | ((in.get(2) & 0xFF) << 8) | (in.get(3) & 0xFF);
        }
        // 拿到随机时间参数

        if ((flag & 1) == 0) {
            // 初始化交换盒子
            reset();
            // 打乱盒子
            swap(box, revMix(myHash(pass), rndPar1), pass);
        }

        if(pos > 63) {
            crypt(in.list, rndPar1 >> 8, 2 + in.offset(), pos / 2, out, box);
            crypt(in.list, rndPar1 - 123, in.offset() + pos / 2 + 1, pos - 1, out, box);
        } else {
            crypt(in.list, -rndPar1, in.offset() + 4, pos, out, box);
        }
        if(digest != null) {
            int dLen = digestCache.length;
            crypt(in.list, rndPar1, pos, pos + dLen, out, box);
            out.pos(out.pos() - dLen);

            digest.reset();
            digest.update(out.list, 0, out.pos());
            digest.digest(digestCache, 0, dLen);
            int begin = out.pos();
            byte[] a = out.list;
            for (int i = 0; i < dLen; i++) {
                if(digestCache[i] != a[begin + i]) {
                    throw new DigestException("Checksum error");
                }
            }
        }
        return out;
    }
}
