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

import roj.collect.Int2IntMap;
import roj.collect.TrieTree;
import roj.config.word.Word;
import roj.config.word.WordPresets;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/10/3 20:03
 */
public final class Symbol {
    public static final String[] operators = {
            "{", "}",
            "[", "]",
            "(", ")",
            "=>", ".", // 如果你想让js像java...  ->
            "++", "--",
            "!", "&&", "||",
            "<", ">", ">=", "<=",
            "===", "==", "!=",
            "~", "&", "|", "^",
            "+", "-", "*", "/", "%", "**",
            "<<", ">>", ">>>",
            "?", ":", ",", ";",
            "=", "+=", "-=", "*=", "/=", "%=",
            "&=", "^=", "|=", "<<=", ">>=", ">>>=",
            "$<", ">$", // 预处理器 (such as minecraft:xxx)
            "..." // spread
    };

    public static final short
            left_l_bracket = 41 /*Keyword.ARGUMENTS + 2*/, right_l_bracket = 42,
            left_m_bracket = 43, right_m_bracket = 44,
            left_s_bracket = 45, right_s_bracket = 46,
            lambda = 47, dot = 48,
            inc = 49, dec = 50,
            logic_not = 51, logic_and = 52, logic_or = 53,
            lss = 54, gtr = 55, geq = 56, leq = 57,
            feq = 58, equ = 59, neq = 60,
            rev = 61, and = 62, or = 63, xor = 64,
            add = 65, sub = 66, mul = 67, div = 68, mod = 69, pow = 70,
            lsh = 71, rsh = 72, rsh_unsigned = 73,
            ask = 74, colon = 75, comma = 76, semicolon = 77,
            assign = 78, add_assign = 79, sub_assign = 80, mul_assign = 81, div_assign = 82, mod_assign = 83,
            and_assign = 84, xor_assign = 85, or_assign = 86, lsh_assign = 87, rsh_assign = 88, rsh_unsigned_assign = 89,
            preprocess_s = 90, preprocess_e = 91,
            spread = 92, rest = spread;

    private static final TrieTree<Short> indexOf = new TrieTree<>();
    private static final Int2IntMap priorities = new Int2IntMap();

    private Symbol() {
    }

    static {
        int i = 40;
        for (String operator : operators) {
            indexOf.put(operator, (short) ++i);
        }

        final Int2IntMap p1 = priorities;

        // 操作符优先级表

        p1.putInt(and, 100);
        p1.putInt(or,  100);
        p1.putInt(xor, 100);

        p1.putInt(lsh, 99);
        p1.putInt(rsh, 99);
        p1.putInt(rsh_unsigned, 99);

        p1.putInt(pow, 98);

        p1.putInt(mul, 97);
        p1.putInt(div, 97);
        p1.putInt(mod, 97);

        p1.putInt(add, 96);
        p1.putInt(sub, 96);

        p1.putInt(lss, 95);
        p1.putInt(gtr, 95);
        p1.putInt(geq, 95);
        p1.putInt(leq, 95);
        p1.putInt(feq, 95);
        p1.putInt(equ, 95);
        p1.putInt(neq, 95);

        p1.putInt(logic_and, 94);
        p1.putInt(logic_or, 94);
    }

    public static short indexOf(CharSequence s) {
        return indexOf.getOrDefault(s, WordPresets.ERROR);
    }

    public static boolean is(Word w) {
        return w.type() > 40 && w.type() <= 100;
    }

    public static int priorityFor(Word word) {
        int prio = priorities.get(word.type());
        if(prio == -1)
            throw new IllegalArgumentException(word.val() + " is not a binary operator");
        return prio;
    }

    public static int argc(short type) {
        switch (type) {
            case logic_not:
            case inc:
            case dec:
            case rev:
                return 1;
            case ask:
                return 3;
            default:
                return 2;
            case left_l_bracket:
            case right_l_bracket:
            case left_m_bracket:
            case right_m_bracket:
            case left_s_bracket:
            case right_s_bracket:
            case lambda:
            case dot:
            case colon:
            case comma:
            case semicolon:
                return 0;
        }
    }

    public static String byId(short operator) {
        return operators[operator - 41];
    }

    public static boolean hasMore(CharSequence cs) {
        return indexOf.startsWith(cs);
    }
}
