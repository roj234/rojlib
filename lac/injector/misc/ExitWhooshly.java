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
package lac.injector.misc;

import roj.reflect.ClassDefiner;

import java.util.concurrent.locks.LockSupport;

/**
 * Only use compiled bytes to exit JVM 'smoothly'
 *
 * @author Roj233
 * @since 2021/10/16 0:22
 */
final class ExitWhooshly extends Thread implements Runnable {
    static {
        new ExitWhooshly();
    }

    private ExitWhooshly() {
        super("Async Chunk Loader#1");
        start();
    }

    public void run() {
        ClassDefiner.INSTANCE.defineClass("W", new byte[] {-54, -2, -70, -66, 0, 0, 0, 52, 0, 8, 1, 0, 1, 87, 7, 0, 1, 1, 0, 29, 115, 117, 110, 47, 114, 101, 102, 108, 101, 99, 116, 47, 77, 97, 103, 105, 99, 65, 99, 99, 101, 115, 115, 111, 114, 73, 109, 112, 108, 7, 0, 3, 1, 0, 8, 60, 99, 108, 105, 110, 105, 116, 62, 1, 0, 3, 40, 41, 86, 1, 0, 4, 67, 111, 100, 101, 0, 33, 0, 2, 0, 4, 0, 0, 0, 0, 0, 1, 0, 8, 0, 5, 0, 6, 0, 1, 0, 7, 0, 0, 0, 13, 0, 0, 0, 0, 0, 0, 0, 1, 87, 0, 0, 0, 0, 0, 0});
        LockSupport.parkNanos(5000);
        try {
            Class.forName("W");
        } catch (Throwable ignored) {}
    }
}
