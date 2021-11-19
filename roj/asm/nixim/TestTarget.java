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
package roj.asm.nixim;

/**
 * Your description here
 *
 * @author solo6975
 * @version 0.1
 * @since 2021/10/7 11:54
 */
public class TestTarget {
    private int superField123123;

    private void superMethod23333() {}

    public int test(int myParam) {
        return myParam == 1 ? 0 : 2;
    }

    public TestTarget() {
        superField123123 = 8888;
    }

    public int test2(int superLocal1, int superLocal2) {
        if (superLocal1 == 233) {
            superLocal1 = 255;
        }
        return superLocal1 + superLocal2 & superLocal1 >> 1;
    }

    public int test3(int myParam) {
        return myParam << 3 | myParam >> 20;
    }

    public int test4(int myParam) {
        throw new UnsupportedOperationException();
    }

    public Class<?> test5(int myTest233) {
        return myTest233 == 0 ? int.class : float.class;
    }
}
