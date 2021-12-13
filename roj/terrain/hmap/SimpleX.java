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
package roj.terrain.hmap;

import java.util.Random;

/**
 * Simplex Noise
 * @author Roj233
 * @version 0.1
 * @since 2021/12/12 15:32
 */
public class SimpleX extends Noised {
    public SimpleX(Random random, int octaveCount) {
        this(random, octaveCount, 256, 256);
    }

    public SimpleX(Random random, int octaveCount, int noiseWidth, int noiseHeight) {
        super(random);
        float[][] whiteNoise = generateWhiteNoise(noiseWidth, noiseHeight, true);

        SimplexNoiser noiser = new SimplexNoiser(random);

        this.noise2d = noiser.genNoise2f(noiseWidth, noiseHeight, octaveCount, 1.0f, 1.0f);
        for (int x = 0; x < noiseWidth; x++) {
            float[] ny = whiteNoise[x];
            float[] ny2 = noise2d[x];
            for (int y = 0; y < noiseHeight; y++) {
                ny2[y] = ny[y] + Math.abs(ny2[y]);
            }
        }

        sea = findMedian(10);
    }
}