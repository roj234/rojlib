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
 * 柏林噪声 <br>
 * <a href="http://devmag.org.za/2009/04/25/perlin-noise/">How to Use Perlin Noise in Your, GamesHerman Tulleken</a>.
 *
 * @author Roj233
 * @version 0.1
 * @since 2021/9/12 13:22
 */
public class Perlin extends Noised {
    public Perlin(Random random, int octaveCount) {
        this(random, octaveCount, 256, 256);
    }

    public Perlin(Random random, int octaveCount, int noiseWidth, int noiseHeight) {
        super(random);

        float[][] whiteNoise = generateWhiteNoise(noiseWidth, noiseHeight, true);

        noise2d = applyNoise2F(whiteNoise, octaveCount, 1f);
        sea = findMedian(10);
    }

    public static float[][] applyNoise2F(float[][] baseNoise, int octaveCount, float persistence) {
        int width = baseNoise.length;
        int height = baseNoise[0].length;

        float[][] perlinNoise = new float[width][height];

        float amplitude = 1.0f;
        float totalAmplitude = 0.0f;

        //blend noise together
        for (int octave = octaveCount - 1; octave >= 0; octave--) {
            amplitude *= persistence;
            totalAmplitude += amplitude;

            int period = 1 << octave; // calculates 2 ^ k
            float frequency = 1.0f / period;

            for (int i = 0; i < width; i++) {
                //calculate the horizontal sampling indices
                int i0 = (i / period) * period;
                int i1 = (i0 + period) % width; //wrap around
                float horizontal_blend = (i - i0) * frequency;

                for (int j = 0; j < height; j++) {
                    //calculate the vertical sampling indices
                    int j0 = (j / period) * period;
                    int j1 = (j0 + period) % height; //wrap around
                    float vertical_blend = (j - j0) * frequency;

                    //blend the top two corners
                    float top = interpolate(baseNoise[i0][j0], baseNoise[i1][j0], horizontal_blend);

                    //blend the bottom two corners
                    float bottom = interpolate(baseNoise[i0][j1], baseNoise[i1][j1], horizontal_blend);

                    //final blend
                    perlinNoise[i][j] += interpolate(top, bottom, vertical_blend) * amplitude;
                }
            }
        }

        //normalisation
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                perlinNoise[i][j] /= totalAmplitude;
            }
        }

        return perlinNoise;
    }

    /**
     * Function returns a linear interpolation between two values.
     * Essentially, the closer alpha is to 0, the closer the resulting value will be to x0;
     * the closer alpha is to 1, the closer the resulting value will be to x1.
     *
     * @param x0 First value.
     * @param x1 Second value.
     * @param alpha Transparency.
     * @return Linear interpolation between two values.
     */
    private static float interpolate(float x0, float x1, float alpha) {
        return x0 * (1 - alpha) + alpha * x1;
    }
}