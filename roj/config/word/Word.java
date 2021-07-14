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

import roj.math.MathUtils;
import roj.text.TextUtil;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/10/3 19:20
 */
public class Word {
    private short type;
    private String val;
    private int index;

    public Word() {}

    /**
     * 复用对象
     */
    public Word reset(int type, int index, String word) {
        this.type = (short) type;
        this.index = index;
        this.val = word;
        return this;
    }

    public Word(int index) {
        this.type = WordPresets.EOF;
        this.index = index;
        this.val = "/EOF";
    }

    @Override
    public String toString() {
        return "Token{#" + type + "@'" + val + '\'' + '}';
    }

    public String val() {
        return val;
    }

    public int getIndex() {
        return index;
    }

    public NumberWord number(boolean negative) {
        int v;
        switch (type) {
            case WordPresets.HEX:
                v = MathUtils.parseIntChecked(val, 16, negative);
                break;
            case WordPresets.BINARY:
                v = MathUtils.parseIntChecked(val, 2, negative);
                break;
            case WordPresets.OCTAL:
                v = MathUtils.parseIntChecked(val, 8, negative);
                break;
            case WordPresets.INTEGER:
                if(!TextUtil.checkInt(TextUtil.INT_MAXS, val, 0, negative)) {
                    // too large
                    return new Word_D(WordPresets.DECIMAL_D, index, val, Double.parseDouble(val));
                }

                v = MathUtils.parseIntChecked(val, 10, negative);
                break;
            case WordPresets.DECIMAL_D:
            case WordPresets.DECIMAL_F:
                return new Word_D(type, index, val, Double.parseDouble(val));
            case WordPresets.LONG:
                return new Word_L(index, val, Long.parseLong(val));
            default:
                return null;
        }
        return new Word_I(index, val, v);
    }

    public NumberWord number() {
        return number(false);
    }

    public short type() {
        return type;
    }

    public Word copy() {
        return new Word().reset(type, index, val);
    }
}
