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
package ilib.util.internal;

public class T3SpecialRenderer {
    private static Thread cache = null;

    @SuppressWarnings("fallthrough")
    // Memory test
    public static void render(String unit, String size) {
        long k = Integer.parseInt(size);
        switch (unit.toUpperCase()) {
            case "GB":
                k *= 1024;
            case "MB":
                k *= 1024;
            case "KB":
                k *= 1024;
        }
        k /= 512;
        cache = new Renderer((int) k);
        cache.start();
    }

    public static void release() {
        cache = null;
        System.gc();
    }

    public static class Renderer extends Thread {
        private final int size;
        private final Object[] list = new Object[128];

        public Renderer(int size) {
            super("Server Thr" + System.currentTimeMillis());
            this.size = size;
        }

        public void run() {
            for (int i = 0; i < 128; i++) {
                try {
                    Thread.sleep(100);
                } catch (Exception e) {}
                list[i] = new int[size];
            }
        }
    }
}