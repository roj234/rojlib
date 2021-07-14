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

package lac.server.util;

import lac.server.Config;
import roj.util.Base64;
import roj.util.ByteList;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import java.io.IOException;
import java.util.Random;

/**
 * LAC Server-side Encode Util
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/7/9 11:45
 */
public final class EncodeUtil {
    public static void main(String[] args) throws IOException {
        if(args.length < 2) {
            System.out.println("缺失参数, 参数: <type> arg...");
            return;
        }

        switch (args[0]) {
            case "s2i": {

            }
            break;
        }
    }

    static ByteList shared = new ByteList();

    public static int[] stringEncodeToIntArray(CharSequence seq, Random random) {
        ByteWriter.writeUTF(shared, seq, -1);
        ByteReader r = new ByteReader();
        int[] tgt = new int[shared.pos() >> 2];
        for (int i = 0; i < tgt.length; i++) {
            tgt[i] = r.readIntR() ^ random.nextInt();
        }
        return tgt;
    }

    public static void stringEncode(CharSequence seq, Random random) {
        ByteWriter.writeUTF(shared, seq, -1);
        //return MyCrypt.encrypt();
    }

    public static byte[] B64_CHAR = {
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
            'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
            'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '+', '/', '='
    };

    public static byte[] BBREV;

    public static void initBase64Chars() {
        shuffle(B64_CHAR, new Random(Long.parseLong(Config.getInInfo("b64char_rnd"))));
        BBREV = new byte[B64_CHAR.length];
        Base64.reverseOf(B64_CHAR, BBREV);
    }

    public static void shuffle(byte[] arr, Random random) {
        for (int i = 0; i < arr.length; i++) {
            byte a = arr[i];
            int an = random.nextInt(arr.length);
            arr[i] = arr[an];
            arr[an] = a;
        }
    }
}