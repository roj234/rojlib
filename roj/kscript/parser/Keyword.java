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

import roj.collect.ToIntMap;
import roj.config.word.Word;
import roj.config.word.WordPresets;
import roj.text.TextUtil;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/9/27 12:31
 */
public final class Keyword {
    public static final String[] keywords = TextUtil.split("for,while,do,continue,break,case,if,else,goto,function," +
            "return,this,new,var,const,switch,delete,true,false,null,undefined,try,catch,finally," +
            "NaN,Infinity,throw,default,let,arguments", ',');

    public static final short FOR = 10, WHILE = 11, DO = 12, CONTINUE = 13, BREAK = 14, CASE = 15,
            IF = 16, ELSE = 17, GOTO = 18,
            FUNCTION = 19, RETURN = 20, THIS = 21, NEW = 22,
            VAR = 23, CONST = 24,
            SWITCH = 25,
            DELETE = 26,
            TRUE = 27, FALSE = 28, NULL = 29, UNDEFINED = 30, TRY = 31, CATCH = 32, FINALLY = 33,
            NAN = 34, INFINITY = 35, THROW = 36, DEFAULT = 37, LET = 38, ARGUMENTS = 39;

    private static final ToIntMap<CharSequence> indexOf = new ToIntMap<>(30, 1);

    private Keyword() {
    }

    static {
        String[] keywords = Keyword.keywords;
        for (int i = 0; i < keywords.length; i++) {
            indexOf.putInt(keywords[i], 10 + i);
        }
    }

    public static boolean is(Word word) {
        return word.type() >= 10 && word.type() <= ARGUMENTS;
    }

    public static short indexOf(CharSequence s) {
        return (short) indexOf.getOrDefault(s, WordPresets.LITERAL);
    }

    public static String byId(short id) {
        return keywords[id - 10];
    }
}
