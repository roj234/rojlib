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
package roj.math;

import roj.text.CharList;
import roj.util.Base64;
import roj.util.ByteList;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import java.io.UTFDataFormatException;

/**
 * 一种加密算法
 *
 * @author zsy
 * @version 0.1
 * @since 2021/6/19 10:07
 */
public class MyCrypt {
    public static void main(String[] args) throws UTFDataFormatException {
        if(args.length < 3) {
            System.out.println("MyCrypt E|D str pass");
            return;
        }

        ByteList out = new ByteList(), out2 = new ByteList();
        ByteList pass = ByteWriter.encodeUTF(args[2]);
        CharList cl = new CharList();
        if(args[0].equals("E")) {
            short[] box = new short[1024];


            ByteList in = ByteWriter.encodeUTF(args[1]);
            encrypt(in, pass, out, box);
            System.out.println("Encrypt:");
            System.out.println(Base64.encode(out, cl));

            decrypt(out, pass, out2, box);

            cl.clear();
            ByteReader.decodeUTF(-1, cl, out2);
            System.out.println("Decrypt:");
            System.out.println(cl);
        } else {
            Base64.decode(args[1], out);
            decrypt(out, pass, out2, null);
            ByteReader.decodeUTF(-1, cl, out2);

            System.out.println("Decrypt:");
            System.out.println(cl);
        }
    }

    public static void encrypt(ByteList in, ByteList pass, ByteList out, short[] box) {
        int rndPar1 = (int) System.currentTimeMillis();
        for (int i = 31; i >= 0; i--) {
            rndPar1 = 31 * rndPar1 ^ (in.get(i % in.pos()) << (pass.get(i % pass.pos()) & 31));
        }
        // 上面只是随机化

        // 初始化交换盒子
        box = initBox(box);

        swap(box, revMix(myHash(pass), rndPar1), pass);
        // 打乱盒子

        // 总大小
        int pos = in.pos() + 4;

        // 把随机时间参数写入输出
        out.add((byte) (rndPar1 >>> 24));
        out.add((byte) (rndPar1 >>> 16));
        if(pos > 63) {
            xor(in, rndPar1 >> 8, 0, in.pos() / 2, out, box);
            out.add((byte) (rndPar1 >>> 8)); // pos / 2

            xor(in, rndPar1 - 123, in.pos() / 2, in.pos(), out, box);
            System.out.println(in.pos() / 2);
            out.add((byte) rndPar1);
        } else {
            out.add((byte) (rndPar1 >>> 8));
            out.add((byte) rndPar1);

            xor(in, -rndPar1, 0, in.pos(), out, box);
        }
    }

    public static void decrypt(ByteList in, ByteList pass, ByteList out, short[] box) {
        int pos = in.pos();

        int rndPar1;

        if(pos > 63) {
            rndPar1 = ((in.get(0) & 0xFF) << 24) | ((in.get(1) & 0xFF) << 16) | ((in.get(in.pos() / 2) & 0xFF) << 8) | (in.get(in.pos() - 1) & 0xFF);
        } else {
            rndPar1 = ((in.get(0) & 0xFF) << 24) | ((in.get(1) & 0xFF) << 16) | ((in.get(2) & 0xFF) << 8) | (in.get(3) & 0xFF);
        }
        // 拿到随机时间参数

        box = initBox(box);

        swap(box, revMix(myHash(pass), rndPar1), pass);

        if(pos > 63) {
            System.out.println(in.pos() / 2 - 2);
            xor(in, rndPar1 >> 8, 2, in.pos() / 2 - 2, out, box);
            xor(in, rndPar1 - 123, in.pos() / 2 - 1, in.pos() - 2, out, box);
        } else {
            xor(in, -rndPar1, 4, in.pos(), out, box);
        }
    }

    static short[] initBox(short[] box) {
        box = box != null ? box : new short[1024];
        for (int i = 0; i < 1024; i++) {
            box[i] = (short) (i & 1023);
        }
        return box;
    }

    static void xor(ByteList in, int i, int off, int len, ByteList out, short[] box) {
        int j = 0;
        while (off < len) {
            int boxIdx1 = i++ & 1023;
            short tmp = box[boxIdx1];
            box[boxIdx1] = box[j];
            box[j] = tmp;
            j = (j + tmp) & 1023;

            out.add((byte) (in.get(off++) ^ (box[(box[boxIdx1] + tmp) & 1023] & 255)));
        }
    }

    /**
     * 交换box位置
     */
    static void swap(short[] box, int offX, ByteList rnd) {
        int j = 0;
        if(rnd.pos() < 1024) {
            int off = offX & 0xFFFF;
            for (int i = 768; i < 1024; i++) {
                int c = rnd.get((i + off) % rnd.pos()) ^ off;

                j = (j + box[i] + c) & 1023;

                short tmp = box[i];
                box[i] = box[j];
                box[j] = tmp;
            }
            off = offX >>> 16;
            for (int i = 0; i < 768; i++) {
                int c = rnd.get((i + off) % rnd.pos()) ^ off;

                j = (j + box[i] + c) & 1023;

                short tmp = box[i];
                box[i] = box[j];
                box[j] = tmp;
            }
        } else {
            int off = offX & 0xFFFF;
            int off2 = offX >>> 16;
            for (int i = 0; i < rnd.pos(); i++) {
                int c = rnd.get((i + off) % rnd.pos()) ^ off2;

                int k = i & 1023;

                j = (j + box[k] + c) & 1023;

                short tmp = box[k];
                box[k] = box[j];
                box[j] = tmp;
            }
        }
    }

    static int myHash(ByteList s) {
        if(s.pos() == 0)
            return 0;

        int hash = 1, sum = 0;
        for (int i = 0; i < s.pos(); i++) {
            hash = 31 * hash + s.get(i);
            sum += s.get(i);
        }

        return hash ^ sum;
    }

    static int revMix(int a, int b) {
        int v = 0;
        for (int i = 0; i < 32; i++) {
            v |= (((a >>> i) ^ (b >>> (31 - i))) & 1) << i;
        }
        return v;
    }
}
