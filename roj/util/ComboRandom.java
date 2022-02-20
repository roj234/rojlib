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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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
        long seed = (seeds[i == seeds.length ? i = 0 : i] * multiplier + addend) & mask;
        seeds[i++] = seed;
        return (int)(seed >>> (48 - bits));
    }

    @Override
    public String toString() {
        return "ComboRandom{" + Arrays.toString(seeds) + '}';
    }

    public static ComboRandom from(String keys) {
        return from(ByteBuffer.wrap(keys.getBytes(StandardCharsets.UTF_8)));
    }

    public static ComboRandom from(byte[] b, int off, int len) {
        return from(ByteBuffer.wrap(b, off, len));
    }

    public static ComboRandom from(ByteBuffer buf) {
        if (!buf.hasRemaining()) throw new IllegalStateException("Empty buffer");

        long[] seed = new long[(buf.remaining() >> 3) + ((buf.remaining() & 7) != 0 ? 1 : 0)];
        int i = 0;
        while (buf.remaining() >= 8) seed[i++] = buf.getLong();
        while (buf.hasRemaining()) {
            seed[i] = (seed[i] << 8) | (buf.get() & 0xFF);
        }
        return new ComboRandom(seed);
    }
}
