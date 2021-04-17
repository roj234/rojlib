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

package roj.asm.cst;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/29 17:16
 */
public class CstType {
    public static final byte
            UTF = 1,
            INT = 3,
            FLOAT = 4,
            LONG = 5,
            DOUBLE = 6,
            CLASS = 7,
            STRING = 8,
            FIELD = 9,
            METHOD = 10,
            INTERFACE = 11,
            NAME_AND_TYPE = 12,
            METHOD_HANDLE = 15,
            METHOD_TYPE = 16,
            DYNAMIC = 17,
            INVOKE_DYNAMIC = 18,
            MODULE = 19,
            PACKAGE = 20;
    public static final byte _TOP_ = 127;

    public static final String[] indexes = {
            "UTF", null, "INT", "FLOAT", "LONG", "DOUBLE", "CLASS", "STRING",
            "FIELD", "METHOD", "INTERFACE", "NAME_AND_TYPE", null, null,
            "METHOD_HANDLE", "METHOD_TYPE", "DYNAMIC", "INVOKE_DYNAMIC",
            "MODULE", "PACKAGE"
    };

    public static String toString(int id) {
        return id < 1 || id > 20 ? null : indexes[id - 1];
    }
}