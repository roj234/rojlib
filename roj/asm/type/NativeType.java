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
package roj.asm.type;

import javax.annotation.Nonnull;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/18 9:51
 */
public final class NativeType {
    public static final char
            ARRAY = '[', CLASS = 'L', VOID = 'V', BOOLEAN = 'Z', BYTE = 'B', CHAR = 'C', SHORT = 'S', INT = 'I', FLOAT = 'F', DOUBLE = 'D', LONG = 'J';

    @Nonnull
    public static String toDesc(byte type) {
        switch (type) {
            case ARRAY:
                return "[";
            case CLASS:
                return "L";
            case VOID:
                return "V";
            case BOOLEAN:
                return "Z";
            case BYTE:
                return "B";
            case CHAR:
                return "C";
            case SHORT:
                return "S";
            case INT:
                return "I";
            case FLOAT:
                return "F";
            case DOUBLE:
                return "D";
            case LONG:
                return "J";
        }
        // noinspection all
        return null;
    }

    @Nonnull
    public static String toString(byte type) {
        switch (type) {
            case ARRAY:
                return "array";
            case CLASS:
                return "class";
            case VOID:
                return "void";
            case BOOLEAN:
                return "boolean";
            case BYTE:
                return "byte";
            case CHAR:
                return "char";
            case SHORT:
                return "short";
            case INT:
                return "int";
            case FLOAT:
                return "float";
            case DOUBLE:
                return "double";
            case LONG:
                return "long";
        }
        // noinspection all
        return null;
    }

    public static boolean isValid(byte c) {
        // noinspection all
        return toDesc(c) != null;
    }

    public static byte validate(char c) {
        // noinspection all
        if (toDesc((byte) c) == null)
            throw new IllegalArgumentException("Illegal native type desc " + c);
        return (byte) c;
    }
}
