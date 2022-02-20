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
package roj.config.data;

/**
 * Config Type
 *
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public enum Type {
    LIST, MAP, STRING, NULL, BOOL,
    INTEGER, LONG, DOUBLE, OBJECT,
    DATE, UNESCAPED_STRING;
    // todo byte short float and primitive array....

    public static final Type[] VALUES = values();

    public boolean isNumber() {
        return ordinal() >= INTEGER.ordinal() && ordinal() <= DOUBLE.ordinal();
    }

    public boolean isSimilar(Type type) {
        switch (this) {
            case STRING:
            case BOOL:
            case INTEGER:
            case LONG:
            case DOUBLE:
            case DATE:
                return type == DOUBLE || type == INTEGER || type == BOOL || type == STRING || type == LONG;
            case NULL:
                return false;
            case MAP:
                return type == MAP || type == OBJECT;
            default:
                return type == this;
        }
    }
}
