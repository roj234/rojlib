package roj.kscript.parser;

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
            "=>", ".",
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
            "@", // 按照设计，越靠后的二元运算符优先级越高 add, mul, pow ...
            "$<", ">$",
            "-" // 这东西不存在于lexer中
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
            add = 525, sub = 526, mul = 527, divide = 528, mod = 529, pow = 530,
            lsh = 531, rsh = 532, rsh_unsigned = 533,
            ask = 534, colon = 535, comma = 536, semicolon = 537,
            assign = 538, add_assign = 539, sub_assign = 540, mul_assign = 541, div_assign = 542, mod_assign = 543,
            and_assign = 544, xor_assign = 545, or_assign = 546, lsh_assign = 547, rsh_assign = 548, rsh_unsigned_assign = 549,
            at = 550,
            preprocess_s = 551, preprocess_e = 552;

    public static final short NEGATIVE = 553;

    private static final TrieTree<Short> indexOf = new TrieTree<>();

    private Symbol() {
    }

    static {
        int i = 500;
        for (int j = 0; j < operators.length - 1; j++) {
            String operator = operators[j];
            indexOf.put(operator, (short) ++i);
        }
    }

    public static short indexOf(CharSequence s) {
        return indexOf.getOrDefault(s, WordPresets.ERROR);
    }

    public static boolean isSymbol(Word w) {
        return w.type() > 500 && w.type() <= 1000;
    }

    public static int priorityFor(Word word) {
        return word.type() - 500;
    }

    public static int argumentCount(short type) {
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
