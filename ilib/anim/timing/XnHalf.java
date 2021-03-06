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
package ilib.anim.timing;

/**
 * @author Roj234
 * @since  2021/5/27 22:53
 */
public class XnHalf extends Simple {
    byte n;

    public XnHalf(int m) {
        this.n = (byte) m;
    }

    @Override
    public Timing setConfig(int key, double value) {
        if(key != TFRegistry.CONFIG_EASE)
            return this;
        if(value == TFRegistry.EASE_IN)
            return n == 1 ? TFRegistry.X2_IN : TFRegistry.X3_IN;
        if(value == TFRegistry.EASE_OUT)
            return n == 1 ? TFRegistry.X2_OUT : TFRegistry.X3_OUT;
        return this;
    }

    public double getConfig(int key) {
        return TFRegistry.EASE_IN_OUT;
    }

    @Override
    public double interpolate(double x) {
        x *= 2;

        if (x < 1F) {
            return (n == 1 ? 1 : x) * x * x / 2;
        }

        x -= n;

        if(n == 1)
            return (x * (x - 2) - 1) / 2;
        return (x * x * x + 2) / 2;
    }

    @Override
    public String name() {
        return "ilib:x_io_" + (n + 1);
    }
}
