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
package roj.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 长种子随机数
 *
 * @author Roj233
 * @since 2021/7/10 14:16
 */
public class ComboRandom extends Random {
    final long[] seeds;
    int i = 0;

    private static final long multiplier = 0x5DEECE66DL;
    private static final long addend = 0xBL;
    private static final long mask = (1L << 48) - 1;

    public ComboRandom(long[] randoms) {
        this.seeds = randoms;
    }

    @Override
    protected int next(int bits) {
        long seed = seeds[i == seeds.length ? i = 0 : i];
        seed = (seed * multiplier + addend) & mask;
        seeds[i++] = seed;
        return (int)(seed >>> (48 - bits));
    }

    public static ComboRandom from(String keys) {
        List<Long> randoms = new ArrayList<>();
        long tmp = 0;
        int i = 0;
        while (i < keys.length()) {
            char c = keys.charAt(i++);
            tmp = tmp * 31 + c;
            if((i & 31) == 0) {
                randoms.add(tmp);
                if((i & 63) == 0)
                    tmp <<= 1;
                else
                    tmp --;
            }
        }
        randoms.add(tmp);
        long[] seed = new long[randoms.size()];
        for (int j = 0; j < randoms.size(); j++) {
            seed[j] = randoms.get(j);
        }

        return new ComboRandom(seed);
    }
}
