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

/**
 * @author Roj234
 * @version 0.1
 * @since 2020/12/5 14:45
 */
public class Color {
    public static final Color TRANSPARENT = new Color(0, 0, 0, 0);

    private final int argb;

    public int getAlpha() {
        return (argb >>> 24) & 0xFF;
    }

    public int getRed() {
        return (argb >>> 16) & 0xFF;
    }

    public int getGreen() {
        return (argb >>> 8) & 0xFF;
    }

    public int getBlue() {
        return argb & 0xFF;
    }

    public float getRedP() {
        return ((float) getRed() / 255f);
    }

    public float getGreenP() {
        return ((float) getGreen() / 255f);
    }

    public float getBlueP() {
        return ((float) getBlue() / 255f);
    }

    public float getAlphaP() {
        return ((float) getAlpha() / 255f);
    }

    public int getARGB() {
        return argb;
    }

    public int getRGB() {
        return argb & 0xFFFFFF;
    }

    public Color(int r, int g, int b, int a) {
        if (r >= 256 || g >= 256 || b >= 256 || a >= 256 || r < 0 || g < 0 || b < 0 || a < 0)
            throw new RuntimeException("Invalid color");
        this.argb = (a << 24) | (r << 16) | (g << 8) | b;
    }
}