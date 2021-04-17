package roj.lavac.parser;

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
 * @since 2021/5/10 23:49
 */
public final class Keyword {
    public static final String[] keywords = TextUtil.split("for,while,do,continue,break,case,if,else,goto,return,switch," +
            "this,new,true,false,null," +
            "void,int,long,double,float,short,byte,char,boolean," +
            "try,catch,finally,throw," +
            "public,protected,private,static,final,abstract," +
            "strictfp,native,volatile,transient,synchronized," +
            "class,interface,enum," +
            "implements,extends," +
            "package,import," +
            "default,throws", ',');

    public static final short
            FOR = 0, WHILE = 1, DO = 2, CONTINUE = 3, BREAK = 4, CASE = 5, IF = 6, ELSE = 7, GOTO = 8, RETURN = 9, SWITCH = 10,
            THIS = 11, NEW = 12,
            TRUE = 13, FALSE = 14, NULL = 15,
            VOID = 16, INT = 17, LONG = 18, DOUBLE = 19, FLOAT = 20, SHORT = 21, BYTE = 22, CHAR = 23, BOOLEAN = 24,
            TRY = 25, CATCH = 26, FINALLY = 27, THROW = 27,
            PUBLIC = 29, PROTECTED = 30, PRIVATE = 31,
            STATIC = 32, FINAL = 33, ABSTRACT = 34,
            STRICTFP = 35, NATIVE = 36, VOLATILE = 37, TRANSIENT = 38, SYNCHRONIZED = 39,
            CLASS = 40, INTERFACE = 41, ENUM = 42,
            IMPLEMENTS = 43, EXTENDS = 44,
            PACKAGE = 45, IMPORT = 46,
            DEFAULT = 47, THROWS = 48;

    private static final ToIntMap<CharSequence> indexOf = new ToIntMap<>(keywords.length, 1);

    private Keyword() {
    }

    static {
        String[] keywords = Keyword.keywords;
        for (int i = 0; i < keywords.length; i++) {
            indexOf.putInt(keywords[i], i);
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
