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
package roj.net.tcp.util;

import roj.collect.ToIntMap;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/11/28 20:17
 */
public final class Action {
    public static final int
            GET = 1,
            POST = 2,
            PUT = 3,
            HEAD = 4,
            DELETE = 5,
            OPTIONS = 6,
            TRACE = 7,
            CONNECT = 8;

    static final ToIntMap<String> available = new ToIntMap<>(7, 2);

    static {
        available.putInt("GET", 1);
        available.putInt("POST", 2);
        available.putInt("PUT", 3);
        available.putInt("HEAD", 4);
        available.putInt("DELETE", 5);
        available.putInt("OPTIONS", 6);
        available.putInt("TRACE", 7);
        available.putInt("CONNECT", 8);
    }

    public static int valueOf(String name) {
        return available.getOrDefault(name, -1);
    }
}
