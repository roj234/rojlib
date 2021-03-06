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
package roj.config.word;

/**
 * @author Roj234
 * @since  2020/10/31 14:17
 */
public final class WordPresets {
    public static final short
            LITERAL = 0,   // 变量名
            CHARACTER = 1, // 字符
            STRING = 2,    // 字符串

            INTEGER = 3,   // int32
            DECIMAL_D = 4, // float64
            HEX = 5,       // 十六进制
            BINARY = 6,    // 二进制
            OCTAL = 7,     // 八进制
            DECIMAL_F = 8, // float32
            LONG = 9,      // int64

            EOF = -1,      // 文件结束
            ERROR = -2,    // 出错
            RFCDATE_DATE = 5,
            RFCDATE_DATETIME = 6,
            RFCDATE_DATETIME_TZ = 7;
}
