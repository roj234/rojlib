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
package ilib.api;

import roj.math.MathUtils;

import java.util.Random;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class RandomEntry {
    public final int min, add;
    public final double[] cdf;

    public static final Random rand = new Random();

    public RandomEntry(int min, int max, double[] factor) {
        this.min = min; //1
        this.add = max - min; //1
        if (add == 0 || factor == null) {
            this.cdf = null;
        } else
            this.cdf = MathUtils.pdf2cdf(factor); // 0.7 1
    }

    public int get() {
        if (add == 0)
            return min;
        if (cdf == null)
            return MathUtils.randomRange(rand, min, min + add);
        return min + MathUtils.cdfRandom(rand, cdf);
    }
}
