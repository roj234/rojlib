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
package roj.kscript.parser;

import roj.collect.IntList;
import roj.collect.MyBitSet;
import roj.config.ParseException;
import roj.config.word.AbstLexer;
import roj.config.word.LineHandler;
import roj.config.word.Word;
import roj.config.word.WordPresets;
import roj.kscript.api.I18n;
import roj.text.CharList;

import java.util.Arrays;

/**
 * JavaScript Lexer : string => tokens
 *
 * @author Roj234
 * @since  2020/10/3 19:20
 */
public class JSLexer extends AbstLexer {
    public static final MyBitSet JS_SPECIAL = MyBitSet.from("+-*/()!~`@#%^&=,<>.?:;|[]{}");

    public boolean acceptsNumber;

    @SuppressWarnings("fallthrough")
    public JSLexer init(CharSequence seq) {
        lastLine = 0;
        IntList lineIndexes = new IntList(100);
        lineIndexes.add(0);

        for (int i = 0; i < seq.length(); i++) {
            char c1 = seq.charAt(i);
            switch (c1) {
                case '\r':
                    if (i + 1 < seq.length() && seq.charAt(i + 1) == '\n') // \r\n
                        i++;
                case '\n':
                    lineIndexes.add(i);
                    break;
            }
        }

        this.lineIndexes = lineIndexes.getRawArray();
        this.lineLen = lineIndexes.size();

        super.init(seq);
        return this;
    }

    int lastLine, lineLen;
    int[] lineIndexes;
    LineHandler lh;

    public final void setLineHandler(LineHandler lh) {
        this.lh = lh;
        lh.handleLineNumber(lastLine);
    }

    /// 读词
    @Override
    public Word readWord() throws ParseException {
        CharSequence input = this.input;
        int index = this.index;

        try {
            while (index < input.length()) {
                int c = input.charAt(index++);
                switch (c) {
                    case '\'':
                        this.index = index;
                        return readConstChar();
                    case '"':
                        this.index = index;
                        return readConstString((char) c);
                    case '/':
                        this.index = index;
                        Word word = ignoreJavaComment(found);
                        if (word != null) return word;
                        index = this.index;
                        break;
                    default: {
                        if (!WHITESPACE.contains(c)) {
                            this.index = index - 1;
                            if (JS_SPECIAL.contains(c)) {
                                switch (c) {
                                    case '-':
                                    case '+':
                                        if(acceptsNumber && input.length() > index && NUMBER.contains(input.charAt(index))) {
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
        } finally {
            applyLineHandler();
        }
        this.index = index;
        return eof();
    }

    protected void applyLineHandler() {
        if(lh != null) {
            int index = Arrays.binarySearch(lineIndexes, 0, lineLen - 1, this.index);
            int line = index >= 0 ? index : -index - 1;

            if(line != lastLine) {
                lh.handleLineNumber(lastLine = line);
            }
        }
    }

    @Override
    protected Word readLiteral() {
        CharSequence input = this.input;
        int index = this.index;

        CharList temp = this.found;
        temp.clear();

        while (index < input.length()) {
            int c = input.charAt(index++);
            if (!JS_SPECIAL.contains(c) && !WHITESPACE.contains(c)) {
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

    @Override
    protected Word formAlphabetClip(CharSequence s) {
        short kwId = Keyword.indexOf(s);
        return formClip(kwId, kwId == WordPresets.LITERAL ? s.toString() : Keyword.byId(kwId));
    }

    /// 其他字符
    @Override
    protected Word readSymbol() throws ParseException {
        CharSequence input = this.input;
        int index = this.index;

        CharList temp = this.found;
        temp.clear();

        final int begin = index;

        short wasFound = WordPresets.ERROR;
        int wasFoundLen = 0;

        while (index < input.length()) {
            char c = input.charAt(index++);
            if (JS_SPECIAL.contains(c)) {
                temp.append(c);

                short id = Symbol.indexOf(temp);
                if (id != WordPresets.ERROR) {
                    wasFound = id;
                    wasFoundLen = temp.length();
                } else if (!Symbol.hasMore(temp)) {
                    break;
                }
            } else {
                index--;
                break;
            }
        }

        if (wasFound != WordPresets.ERROR) {
            this.index = index - (temp.length() - wasFoundLen);
            //temp.setIndex(wasFoundLen);
            temp.clear();
            // intern instead of new String()

            return formClip(wasFound, Symbol.byId(wasFound));
        }
        this.index = index;

        if (temp.length() == 0) {
            return eof();
        }

        throw err("未知T_SPECIAL '" + temp + "'");
    }

    @Override
    protected Word formNumberClip(byte flag, CharList temp, boolean negative) throws ParseException {
        return formClip((short) (WordPresets.INTEGER + flag), temp).number(this, negative);
    }

    public ParseException err(String reason, Word word) {
        return new ParseException(input, I18n.translate(reason) + I18n.at + word.val(), word.getIndex(), null);
    }

    public ParseException err(String reason, Throwable cause) {
        return new ParseException(input, I18n.translate(reason), this.index, cause);
    }
}
