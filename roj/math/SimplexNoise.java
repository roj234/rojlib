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

public class SimplexNoise {  // Simplex noise in 2D, 3D and 4D
    private static final double[] grad3 = {1, 1, 0   , -1, 1, 0   , 1, -1, 0   , -1, -1, 0   ,
            1, 0, 1   , -1, 0, 1   , 1, 0, -1   , -1, 0, -1   ,
            0, 1, 1   , 0, -1, 1   , 0, 1, -1   , 0, -1, -1   };

    private static final double[] grad4 = {0, 1, 1, 1   , 0, 1, 1, -1   , 0, 1, -1, 1   , 0, 1, -1, -1   ,
            0, -1, 1, 1   , 0, -1, 1, -1   , 0, -1, -1, 1   , 0, -1, -1, -1   ,
            1, 0, 1, 1   , 1, 0, 1, -1   , 1, 0, -1, 1   , 1, 0, -1, -1   ,
            -1, 0, 1, 1   , -1, 0, 1, -1   , -1, 0, -1, 1   , -1, 0, -1, -1   ,
            1, 1, 0, 1   , 1, 1, 0, -1   , 1, -1, 0, 1   , 1, -1, 0, -1   ,
            -1, 1, 0, 1   , -1, 1, 0, -1   , -1, -1, 0, 1   , -1, -1, 0, -1   ,
            1, 1, 1, 0   , 1, 1, -1, 0   , 1, -1, 1, 0   , 1, -1, -1, 0   ,
            -1, 1, 1, 0   , -1, 1, -1, 0   , -1, -1, 1, 0   , -1, -1, -1, 0   };

    // To remove the need for index wrapping, double the permutation table length
    private static final short[] perm = new short[512];
    private static final short[] permMod12 = new short[512];

    static {
        short[] p = {151, 160, 137, 91, 90, 15,
                131, 13, 201, 95, 96, 53, 194, 233, 7, 225, 140, 36, 103, 30, 69, 142, 8, 99, 37, 240, 21, 10, 23,
                190, 6, 148, 247, 120, 234, 75, 0, 26, 197, 62, 94, 252, 219, 203, 117, 35, 11, 32, 57, 177, 33,
                88, 237, 149, 56, 87, 174, 20, 125, 136, 171, 168, 68, 175, 74, 165, 71, 134, 139, 48, 27, 166,
                77, 146, 158, 231, 83, 111, 229, 122, 60, 211, 133, 230, 220, 105, 92, 41, 55, 46, 245, 40, 244,
                102, 143, 54, 65, 25, 63, 161, 1, 216, 80, 73, 209, 76, 132, 187, 208, 89, 18, 169, 200, 196,
                135, 130, 116, 188, 159, 86, 164, 100, 109, 198, 173, 186, 3, 64, 52, 217, 226, 250, 124, 123,
                5, 202, 38, 147, 118, 126, 255, 82, 85, 212, 207, 206, 59, 227, 47, 16, 58, 17, 182, 189, 28, 42,
                223, 183, 170, 213, 119, 248, 152, 2, 44, 154, 163, 70, 221, 153, 101, 155, 167, 43, 172, 9,
                129, 22, 39, 253, 19, 98, 108, 110, 79, 113, 224, 232, 178, 185, 112, 104, 218, 246, 97, 228,
                251, 34, 242, 193, 238, 210, 144, 12, 191, 179, 162, 241, 81, 51, 145, 235, 249, 14, 239, 107,
                49, 192, 214, 31, 181, 199, 106, 157, 184, 84, 204, 176, 115, 121, 50, 45, 127, 4, 150, 254,
                138, 236, 205, 93, 222, 114, 67, 29, 24, 72, 243, 141, 128, 195, 78, 66, 215, 61, 156, 180};
        System.arraycopy(p, 0, perm, 0, 256);
        System.arraycopy(p, 0, perm, 256, 256);
        for (int i = 0; i < 256; i++) {
            permMod12[i] = (short) (p[i] % 12);
        }
        System.arraycopy(permMod12, 0, permMod12, 256, 256);
    }

    // Skewing and unskewing factors for 2, 3, and 4 dimensions
    private static final double F2 = 0.5 * (Math.sqrt(3.0) - 1.0);
    private static final double G2 = (3.0 - Math.sqrt(3.0)) / 6.0;

    private static final double F3 = 1.0 / 3.0;
    private static final double G3 = 1.0 / 6.0;

    private static final double F4 = (Math.sqrt(5.0) - 1.0) / 4.0;
    private static final double G4 = (5.0 - Math.sqrt(5.0)) / 20.0;

    private static double dot(int i, double x, double y) {
        i *= 3;
        return grad3[i] * x + grad3[i + 1] * y;
    }

    private static double dot(int i, double x, double y, double z) {
        i *= 3;
        return grad3[i++] * x + grad3[i++] * y + grad3[i] * z;
    }

    private static double dot(int i, double x, double y, double z, double w) {
        i <<= 2;
        return grad4[i++] * x + grad4[i++] * y + grad4[i++] * z + grad4[i] * w;
    }

    // 2D simplex noise
    public static double noise(double xin, double yin) {
        double n0, n1, n2; // Noise contributions from the three corners
        // Skew the input space to determine which simplex cell we're in
        double s = (xin + yin) * F2; // Hairy factor for 2D
        int i = MathUtils.floor(xin + s);
        int j = MathUtils.floor(yin + s);
        double t = (i + j) * G2;
        // Unskew the cell origin back to (x,y) space
        double x0 = xin - i + t; // The x,y distances from the cell origin
        double y0 = yin - j + t;

        // For the 2D case, the simplex shape is an equilateral triangle.
        // Determine which simplex we are in.
        int i1; // Offsets for second (middle) corner of simplex in (i,j) coords
        // lower triangle, XY order: (0,0)->(1,0)->(1,1)
        // upper triangle, YX order: (0,0)->(0,1)->(1,1)
        i1 = x0 > y0 ? 1 : 0;

        // A step of (1,0) in (i,j) means a step of (1-c,-c) in (x,y), and
        // a step of (0,1) in (i,j) means a step of (-c,1-c) in (x,y), where
        // c = (3-sqrt(3))/6

        // Work out the hashed gradient indices of the three simplex corners
        int ii = i & 255;
        int jj = j & 255;
        // Calculate the contribution from the three corners
        double t0 = 0.5 - x0 * x0 - y0 * y0;
        if (t0 < 0) n0 = 0.0;
        else {
            int gi0 = permMod12[ii + perm[jj]];
            t0 *= t0;
            n0 = t0 * t0 * dot(gi0, x0, y0);  // (x,y) of grad3 used for 2D gradient
        }
        double x1 = x0 - i1 + G2; // Offsets for middle corner in (x,y) unskewed coords
        double y1 = y0 - 1 + i1 + G2;
        double t1 = 0.5 - x1 * x1 - y1 * y1;
        if (t1 < 0) n1 = 0.0;
        else {
            int gi1 = permMod12[ii + i1 + perm[jj + 1 - i1]];
            t1 *= t1;
            n1 = t1 * t1 * dot(gi1, x1, y1);
        }
        x1 = x0 - 1.0 + 2.0 * G2; // Offsets for last corner in (x,y) unskewed coords
        y1 = y0 - 1.0 + 2.0 * G2;
        t1 = 0.5 - x1 * x1 - y1 * y1;
        if (t1 < 0) n2 = 0.0;
        else {
            int gi2 = permMod12[ii + 1 + perm[jj + 1]];
            t1 *= t1;
            n2 = t1 * t1 * dot(gi2, x1, y1);
        }
        // Add contributions from each corner to get the final noise value.
        // The result is scaled to return values in the interval [-1,1].
        return 70.0 * (n0 + n1 + n2);
    }


    // 3D simplex noise
    public static double noise(double xin, double yin, double zin) {
        double n0, n1, n2, n3; // Noise contributions from the four corners
        // Skew the input space to determine which simplex cell we're in
        double s = (xin + yin + zin) * F3; // Very nice and simple skew factor for 3D
        int i = MathUtils.floor(xin + s);
        int j = MathUtils.floor(yin + s);
        int k = MathUtils.floor(zin + s);
        double t = (i + j + k) * G3;
        double X0 = i - t; // Unskew the cell origin back to (x,y,z) space
        double Y0 = j - t;
        double Z0 = k - t;
        double x0 = xin - X0; // The x,y,z distances from the cell origin
        double y0 = yin - Y0;
        double z0 = zin - Z0;
        // For the 3D case, the simplex shape is a slightly irregular tetrahedron.
        // Determine which simplex we are in.
        int i1, j1, k1; // Offsets for second corner of simplex in (i,j,k) coords
        int i2, j2, k2; // Offsets for third corner of simplex in (i,j,k) coords
        if (x0 >= y0) {
            j1 = 0;
            i2 = 1;
            if (y0 >= z0) {
                i1 = 1;
                k1 = 0;
                j2 = 1;
                k2 = 0;
            } // X Y Z order
            else if (x0 >= z0) {
                i1 = 1;
                k1 = 0;
                j2 = 0;
                k2 = 1;
            } // X Z Y order
            else {
                i1 = 0;
                k1 = 1;
                j2 = 0;
                k2 = 1;
            } // Z X Y order
        } else { // x0<y0
            i1 = 0;
            j2 = 1;
            if (y0 < z0) {
                j1 = 0;
                k1 = 1;
                i2 = 0;
                k2 = 1;
            } // Z Y X order
            else if (x0 < z0) {
                j1 = 1;
                k1 = 0;
                i2 = 0;
                k2 = 1;
            } // Y Z X order
            else {
                j1 = 1;
                k1 = 0;
                i2 = 1;
                k2 = 0;
            } // Y X Z order
        }
        // A step of (1,0,0) in (i,j,k) means a step of (1-c,-c,-c) in (x,y,z),
        // a step of (0,1,0) in (i,j,k) means a step of (-c,1-c,-c) in (x,y,z), and
        // a step of (0,0,1) in (i,j,k) means a step of (-c,-c,1-c) in (x,y,z), where
        // c = 1/6.

        // Work out the hashed gradient indices of the four simplex corners
        int ii = i & 255;
        int jj = j & 255;
        int kk = k & 255;
        int gi0 = permMod12[ii + perm[jj + perm[kk]]];
        int gi1 = permMod12[ii + i1 + perm[jj + j1 + perm[kk + k1]]];
        int gi2 = permMod12[ii + i2 + perm[jj + j2 + perm[kk + k2]]];
        int gi3 = permMod12[ii + 1 + perm[jj + 1 + perm[kk + 1]]];
        // Calculate the contribution from the four corners
        double t0 = 0.6 - x0 * x0 - y0 * y0 - z0 * z0;
        if (t0 < 0) n0 = 0.0;
        else {
            t0 *= t0;
            n0 = t0 * t0 * dot(gi0, x0, y0, z0);
        }
        double x1 = x0 - i1 + G3; // Offsets for second corner in (x,y,z) coords
        double y1 = y0 - j1 + G3;
        double z1 = z0 - k1 + G3;
        double t1 = 0.6 - x1 * x1 - y1 * y1 - z1 * z1;
        if (t1 < 0) n1 = 0.0;
        else {
            t1 *= t1;
            n1 = t1 * t1 * dot(gi1, x1, y1, z1);
        }

        x1 = x0 - i2 + 2.0 * G3; // Offsets for third corner in (x,y,z) coords
        y1 = y0 - j2 + 2.0 * G3;
        z1 = z0 - k2 + 2.0 * G3;
        t1 = 0.6 - x1 * x1 - y1 * y1 - z1 * z1;
        if (t1 < 0) n2 = 0.0;
        else {
            t1 *= t1;
            n2 = t1 * t1 * dot(gi2, x1, y1, z1);
        }

        x1 = x0 - 1.0 + 3.0 * G3; // Offsets for last corner in (x,y,z) coords
        y1 = y0 - 1.0 + 3.0 * G3;
        z1 = z0 - 1.0 + 3.0 * G3;
        t1 = 0.6 - x1 * x1 - y1 * y1 - z1 * z1;
        if (t1 < 0) n3 = 0.0;
        else {
            t1 *= t1;
            n3 = t1 * t1 * dot(gi3, x1, y1, z1);
        }
        // Add contributions from each corner to get the final noise value.
        // The result is scaled to stay just inside [-1,1]
        return 32.0 * (n0 + n1 + n2 + n3);
    }

    // 4D simplex noise, better simplex rank ordering method 2012-03-09
    public static double noise(double x, double y, double z, double w) {
        double n0, n1, n2, n3, n4; // Noise contributions from the five corners
        // Skew the (x,y,z,w) space to determine which cell of 24 simplices we're in
        double s = (x + y + z + w) * F4; // Factor for 4D skewing
        int i = MathUtils.floor(x + s);
        int j = MathUtils.floor(y + s);
        int k = MathUtils.floor(z + s);
        int l = MathUtils.floor(w + s);
        double t = (i + j + k + l) * G4; // Factor for 4D unskewing
        // Unskew the cell origin back to (x,y,z,w) space
        double x0 = x - i + t;  // The x,y,z,w distances from the cell origin
        double y0 = y - j + t;
        double z0 = z - k + t;
        double w0 = w - l + t;
        // For the 4D case, the simplex is a 4D shape I won't even try to describe.
        // To find out which of the 24 possible simplices we're in, we need to
        // determine the magnitude ordering of x0, y0, z0 and w0.
        // Six pair-wise comparisons are performed between each possible pair
        // of the four coordinates, and the results are used to rank the numbers.
        int rankx = 0;
        int ranky = 0;
        int rankz = 0;
        int rankw = 0;
        if (x0 > y0) rankx++;
        else ranky++;
        if (x0 > z0) rankx++;
        else rankz++;
        if (x0 > w0) rankx++;
        else rankw++;
        if (y0 > z0) ranky++;
        else rankz++;
        if (y0 > w0) ranky++;
        else rankw++;
        if (z0 > w0) rankz++;
        else rankw++;
        // [rankx, ranky, rankz, rankw] is a 4-vector with the numbers 0, 1, 2 and 3
        // in some order. We use a thresholding to set the coordinates in turn.

        // Work out the hashed gradient indices of the five simplex corners
        int ii = i & 255;
        int jj = j & 255;
        int kk = k & 255;
        int ll = l & 255;

        double[] n = new double[5];

        int i0, j0, k0, l0; // The integer offsets for the [m] simplex corner
        double x1, y1, z1, w1, t1, g4 = 0;
        for (int m = 4; m >= 0; m--) {
            i0 = rankx >= m ? 1 : 0;
            j0 = ranky >= m ? 1 : 0;
            k0 = rankz >= m ? 1 : 0;
            l0 = rankw >= m ? 1 : 0;

            x1 = x0 - i0 + g4;
            y1 = y0 - j0 + g4;
            z1 = z0 - k0 + g4;
            w1 = w0 - l0 + g4;

            g4 += G4;

            t1 = 0.6 - x1 * x1 - y1 * y1 - z1 * z1 - w1 * w1;
            if (t1 < 0) n[4 - m] = 0.0;
            else {
                int gi = perm[ii + i0 + perm[jj + j0 + perm[kk + k0 + perm[ll + l0]]]] % 32;
                t1 *= t1;
                n[4 - m] = t1 * t1 * dot(gi, x1, y1, z1, w1);
            }
        }

        // Sum up and scale the result to cover the range [-1,1]
        return 27.0 * (n[0] + n[1] + n[2] + n[3] + n[4]);
    }

    public static double noise(float x, float y, float frequency) {
        return noise(x / frequency, y / frequency);
    }

    public static double[][] generate2DOctavedSimplexNoise(int width, int height, int octaves, float roughness, float scale) {
        double[][] totalNoise = new double[width][height];
        float layerFrequency = scale;
        float layerWeight = 1;
        float weightSum = 0;

        for (int octave = 0; octave < octaves; octave++) {
            //Calculate single layer/octave of simplex noise, then add it to total noise
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    totalNoise[x][y] += (float) noise(x * layerFrequency, y * layerFrequency) * layerWeight;
                }
            }

            //Increase variables with each incrementing octave
            layerFrequency *= 2;
            weightSum += layerWeight;
            layerWeight *= roughness;

        }
        return totalNoise;
    }

    public static double[][][] generate3DOctavedSimplexNoise(int length, int width, int height, int octaves, float roughness, float scale, double deviation) {
        double[][][] totalNoise = new double[length][height][width];
        float layerFrequency = scale;
        float layerWeight = 1;
        float weightSum = 0;

        for (int octave = 0; octave < octaves; octave++) {
            //Calculate single layer/octave of simplex noise, then add it to total noise
            for (int x = 0; x < length; x++) {
                for (int z = 0; z < width; z++) {
                    for (int y = 0; y < height; y++) {

                        totalNoise[x]
                                [y]
                                [z] += (float) noise((double) x * layerFrequency, y * layerFrequency, z * layerFrequency) * layerWeight * (Math.pow(deviation, Math.max(y * x * z / 8, 1)));
                    }
                }
            }

            //Increase variables with each incrementing octave
            layerFrequency *= 2;
            weightSum += layerWeight;
            layerWeight *= roughness;

        }
        return totalNoise;
    }
}