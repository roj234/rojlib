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

import roj.math.Vec2d;
import roj.terrain.RectX;

import java.util.Arrays;
import java.util.Random;

/**
 * @author Roj233
 * @since 2021/12/12 17:16
 */
public abstract class Noised implements HeightMap {
    protected final Random r;
    protected float[][] noise2d;
    protected float     sea;

    protected Noised(Random r) {
        this.r = r;
    }

    @Override
    public boolean isWater(Vec2d p, RectX bounds, Random random) {
        int x = (int) (p.x / bounds.width * (noise2d.length - 1));
        int y = (int) (p.y / bounds.height * (noise2d[0].length - 1));

        return noise2d[x][y] < sea;
    }

    @Override
    public float getSeaLevel() {
        return sea;
    }

    @Override
    public float getExceptedHeight(Vec2d p, RectX bounds, Random random) {
        int x = (int) (p.x / bounds.width * (noise2d.length - 1));
        int y = (int) (p.y / bounds.height * (noise2d[0].length - 1));

        return noise2d[x][y];
    }

    protected final float findMedian(int sampleSize) {
        int[] count = new int[sampleSize];

        for (float[] yNoises : noise2d) {
            for (float noise : yNoises) {
                noise *= sampleSize;
                for (int k = 1; k < sampleSize; k++) {
                    if (noise < k) {
                        count[k - 1]++;
                        break;
                    }
                }
            }
        }

        int n = 0;
        for (int i = 1; i < count.length; i++) {
            if (count[i] > count[n]) n = i;
        }
        System.out.println(Arrays.toString(count));

        return (float) (n + 0.5) / sampleSize;
    }

    protected final float[][] generateWhiteNoise(int width, int height, boolean keepOcean) {
        float[][] noise = new float[width][height];
        if (keepOcean) {
            for (int i = width / 25; i < width * 0.96f; i++) {
                for (int j = height / 25; j < height * 0.96f; j++) {
                    noise[i][j] = r.nextFloat();
                }
            }
        } else {
            for (int i = 0; i < width; i++) {
                for (int j = 0; j < height; j++) {
                    noise[i][j] = r.nextFloat();
                }
            }
        }
        return noise;
    }
}
