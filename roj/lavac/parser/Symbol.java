package roj.lavac.parser;

import roj.collect.Int2IntMap;
import roj.collect.TrieTree;
import roj.config.word.Word;
import roj.config.word.WordPresets;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 * <p>
 * 符号
 *
 * @author solo6975
 * @since 2020/10/3 20:03
 */
public final class Symbol {
    public static final String[] operators = {
            "{", "}",
            "[", "]",
            "(", ")",
            "->", ".", // java.lambda
            "++", "--",
            "!", "&&", "||",
            "~", "&", "|", "^",
            "+", "-", "*", "/", "%", "**",
            "<<", ">>", ">>>",
            "<", ">", ">=", "<=",
            "===", "==", "!=", "?", ":", ",", ";",
            "=", "+=", "-=", "*=", "/=", "%=",
            "&=", "^=", "|=", "<<=", ">>=", ">>>=",
            "$<", ">$", "@", "..."
    };

    public static final short
            left_l_bracket = 501, right_l_bracket = 502,
            left_m_bracket = 503, right_m_bracket = 504,
            left_s_bracket = 505, right_s_bracket = 506,
            lambda = 507, dot = 508,
            inc = 509, dec = 510,
            logic_not = 511, logic_and = 512, logic_or = 513,
            lss = 514, gtr = 515, geq = 516, leq = 517,
            feq = 518, equ = 519, neq = 520,
            rev = 521, and = 522, or = 523, xor = 524,
            add = 525, sub = 526, mul = 527, div = 528, mod = 529, pow = 530,
            lsh = 531, rsh = 532, rsh_unsigned = 533,
            ask = 534, colon = 535, comma = 536, semicolon = 537,
            assign = 538, add_assign = 539, sub_assign = 540, mul_assign = 541, div_assign = 542, mod_assign = 543,
            and_assign = 544, xor_assign = 545, or_assign = 546, lsh_assign = 547, rsh_assign = 548, rsh_unsigned_assign = 549,
            preprocess_s = 550, preprocess_e = 551, at = 552, varargs = 553;

    private static final TrieTree<Short> indexOf = new TrieTree<>();
    private static final Int2IntMap priorities = new Int2IntMap();

    private Symbol() {
    }

    static {
        int i = 500;
        final String[] operators1 = operators;
        for (int j = 0; j < operators1.length - 1; j++) {
            String operator = operators1[j];
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
        return w.type() > 500 && w.type() <= 1000;
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
        return operators[operator - 501];
    }

    public static boolean hasMore(CharSequence cs) {
        return indexOf.startsWith(cs);
    }
}
