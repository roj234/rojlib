/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: Color.java
 */
package roj.util;

public class Color {
    public static final Color TRANSPARENT = new Color(0, 0, 0, 0);
    /**
     * The color code that will be displayed
     */
    final byte r, g, b, a;

    public int getRed() {
        return r & 0xFF;
    }

    public int getGreen() {
        return g & 0xFF;
    }

    public int getBlue() {
        return b & 0xFF;
    }

    public int getAlpha() {
        return a & 0xFF;
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
        return ((a & 0xFF) << 24) |
                ((r & 0xFF) << 16) |
                ((g & 0xFF) << 8) |
                ((b & 0xFF));
    }

    public int getRGB() {
        return ((r & 0xFF) << 16) |
                ((g & 0xFF) << 8) |
                ((b & 0xFF));
    }

    public Color(int r, int g, int b, int a) {
        if (r >= 256 || g >= 256 || b >= 256 || a >= 256 || r < 0 || g < 0 || b < 0 || a < 0)
            throw new RuntimeException("Invalid color");
        this.r = (byte) r;
        this.g = (byte) g;
        this.b = (byte) b;
        this.a = (byte) a;
    }
}