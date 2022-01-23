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

import roj.config.ParseException;
import roj.text.CharList;

/**
 * @author Roj234
 * @since  2021/6/15 20:43
 */
public final class Tokenizer extends AbstLexer {
    public static final short SYMBOL = 15;

    public Tokenizer init(CharSequence seq) {
        super.init(seq);
        return this;
    }

    public Word readStringToken() throws ParseException {
        CharSequence input = this.input;
        int index = this.index;

        while (index < input.length()) {
            int c = input.charAt(index++);
            switch (c) {
                case '=':
                    this.index = index;
                    return formClip(SYMBOL, "=");
                case '\'':
                case '"':
                    this.index = index;
                    return readConstString((char) c);
                default: {
                    if (!WHITESPACE.contains(c)) {
                        this.index = index - 1;
                        return readArg();
                    }
                }
            }
        }
        this.index = index;
        return eof();
    }

    @Override
    @SuppressWarnings("fallthrough")
    public Word readWord() throws ParseException {
        CharSequence input = this.input;
        int index = this.index;

        while (index < input.length()) {
            int c = input.charAt(index++);
            switch (c) {
                case '\'':
                case '"':
                    this.index = index;
                    return readConstString((char) c);
                case '+':
                case '-':
                    if(index < input.length() && NUMBER.contains(input.charAt(index))) {
                        this.index = index - 1;
                        return readDigit(true);
                    }
                default:
                    if (!WHITESPACE.contains(c)) {
                        this.index = index - 1;
                        if(NUMBER.contains(c)) {
                            return readDigit(false);
                        }
                        return readLiteral();
                    }
            }
        }
        this.index = index;
        return eof();
    }

    private Word readArg() {
        CharSequence input = this.input;
        int index = this.index;

        CharList temp = this.found;
        temp.clear();

        while (index < input.length()) {
            int c = input.charAt(index++);
            if (!WHITESPACE.contains(c) && c != '=') {
                temp.append((char) c);
            } else {
                index--;
                break;
            }
        }
        this.index = index;

        if (temp.length() == 0) {
            return eof();
        }

        return formAlphabetClip(temp);
    }
}
