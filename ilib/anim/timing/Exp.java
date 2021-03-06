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
public class Exp extends Simple {
    int mode;

    public Exp(int m) {
        this.mode = m;
    }

    @Override
    public double interpolate(double x) {
        switch (mode) {
            case 1:
                return Math.pow(2, 10 * (x - 1));
            case 2:
                return (-Math.pow(2, -10 * x) + 1);
            case 3: {
                if (x == 0 || x == 1) return x;

                x *= 2;

                if (x < 1F) return Math.pow(2, 10 * (x - 1));

                return (-Math.pow(2, -10 * (x - 1)) + 2);
            }
        }
        return 0;
    }

    @Override
    public String name() {
        return "ilib:exp_io_" + mode;
    }
}
