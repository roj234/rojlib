package roj.kscript.parser;

import roj.collect.ToIntMap;
import roj.config.word.Word;
import roj.config.word.WordPresets;
import roj.text.TextUtil;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 * <p>
 * 关键词
 *
 * @author Roj233
 * @since 2020/9/27 12:31
 */
public final class Keyword {
    public static final String[] keywords = TextUtil.splitString("for,while,do,continue,break,case,if,else,goto,function," +
            "return,this,new,var,const,switch,delete,true,false,null,undefined,try,catch,finally," +
            "NaN,Infinity,throw,default,let,arguments", ',');

    public static final short FOR = 0, WHILE = 1, DO = 2, CONTINUE = 3, BREAK = 4, CASE = 5,
            IF = 6, ELSE = 7, GOTO = 8,
            FUNCTION = 9, RETURN = 10, THIS = 11, NEW = 12,
            VAR = 13, CONST = 14,
            SWITCH = 15,
            DELETE = 16,
            TRUE = 17, FALSE = 18, NULL = 19, UNDEFINED = 20, TRY = 21, CATCH = 22, FINALLY = 23,
            NAN = 24, INFINITY = 25, THROW = 26, DEFAULT = 27, LET = 28, ARGUMENTS = 29;

    private static final ToIntMap<CharSequence> indexOf = new ToIntMap<>(30, 1);

    private Keyword() {
    }

    static {
        String[] keywords = Keyword.keywords;
        for (int i = 0; i < keywords.length; i++) {
            indexOf.put(keywords[i], i);
        }
    }

    public static boolean is(Word word) {
        return word.type() >= 0 && word.type() <= 500;
    }

    public static short indexOf(CharSequence s) {
        return (short) indexOf.getOrDefault(s, WordPresets.LITERAL);
    }

    public static String byId(short id) {
        return keywords[id];
    }
}
