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
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/10/3 19:20
 */
public class Lexer extends AbstLexer {
    public Lexer init(CharSequence s) {
        this.index = 0;
        this.input = s;
        return this;
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
                case '/':
                    this.index = index;
                    Word word = ignoreStdNote();
                    if (word != null)
                        return word;
                    index = this.index;
                    break;
                default: {
                    if (!WHITESPACE.contains(c)) {
                        this.index = index - 1;
                        if (SPECIAL.contains(c)) {
                            switch (c) {
                                case '-':
                                case '+':
                                    if(input.length() > index && NUMBER.contains(input.charAt(index))) {
                                        return readDigit(true);
                                    }
                                    break;
                            }
                            return readSymbol();
                        } else if (NUMBER.contains(c)) {
                            return readDigit(false);
                        } else {
                            return readLiteral();
                        }
                    }
                }
            }
        }
        this.index = index;
        return eof();
    }

    /**
     * 其他字符
     */
    @Override
    protected Word readSymbol() throws ParseException {
        return formClip(WordPresets.ERROR, String.valueOf(next()));
    }

    @Override
    protected Word formNumberClip(byte flag, CharList temp, boolean negative) throws ParseException {
        return formClip((short) (WordPresets.INTEGER + flag), temp).number(negative);
    }
}
