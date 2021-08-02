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
package roj.asm.frame;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/2 23:28
 */
public class FrameType {
    public static final int same = (0),
    same_local_1_stack = (64),
    same_local_1_stack_ex = (247),
    chop = (248),
    same_ex = (251),
    append = (252),
    full = (255);

    public static int byId(int b) {
        final int b1 = b & 0xFF;
        if((b1 & 128) == 0) {
                                            // 64 - 127
            return (b1 & 64) == 0 ? same : same_local_1_stack;
                                 // 0 - 63
        }
        switch (b1) {
            case 247:
                return same_local_1_stack_ex;
            case 248:
            case 249:
            case 250:
                return chop;
            case 251:
                return same_ex;
            case 252:
            case 253:
            case 254:
                return append;
            case 255:
                return full;
        }
        throw new IllegalArgumentException("Undefined frame type" + b1);
    }

    public static String toString(int type) {
        if((type & 128) == 0) {
            // 64 - 127
            return (type & 64) == 0 ? "same" : "same_local_1_stack";
            // 0 - 63
        }
        switch (type) {
            case 247:
                return "same_local_1_stack_ex";
            case 248:
            case 249:
            case 250:
                return "chop " + (type - 247);
            case 251:
                return "same_ex";
            case 252:
            case 253:
            case 254:
                return "append " + (type - 251);
            case 255:
                return "full";
        }
        throw new IllegalArgumentException("Undefined frame type" + type);
    }
}
