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

import roj.asm.type.NativeType;
import roj.asm.type.Type;
import roj.concurrent.OperationDone;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/18 9:51java
 */
public final class VarType {
    public static final byte TOP = 0,
            INT = 1,
            FLOAT = 2,
            DOUBLE = 3,
            LONG = 4,
            NULL = 5,
            UNINITIAL_THIS = 6,
            REFERENCE = 7,
            UNINITIAL = 8;

    public static int ofType(Type type) {
        if(type.array > 0)
            return -3;
        switch (type.type) {
            case NativeType.VOID:
                return -1;
            case NativeType.BOOLEAN:
            case NativeType.BYTE:
            case NativeType.CHAR:
            case NativeType.SHORT:
            case NativeType.INT:
                return 1;
            case NativeType.FLOAT:
                return 2;
            case NativeType.DOUBLE:
                return 3;
            case NativeType.LONG:
                return 4;
            case NativeType.CLASS:
                return -2;
            case NativeType.ARRAY:
                return -3;
        }
        throw OperationDone.NEVER;
    }

    static final String[] toString = {
            "top", "int", "float", "double", "long", "null", "uninitial_this", "object", "uninitial"
    };

    public static String toString(byte type) {
        return toString[type];
    }

    public static int validate(byte b) {
        if ((b & 0xFF) > 8) {
            throw new IllegalArgumentException("Unsupported verification type " + b);
        }
        return (b & 0xFF);
    }
}
