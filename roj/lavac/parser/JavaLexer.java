package roj.lavac.parser;

import roj.collect.IBitSet;
import roj.collect.LongBitSet;
import roj.collect.SingleBitSet;
import roj.config.ParseException;
import roj.config.word.Word;
import roj.config.word.WordPresets;
import roj.kscript.parser.JSLexer;
import roj.text.CharList;

/**
 * Java词法分析器
 *
 * @author solo6975
 * @since 2020/10/3 19:20
 */
public final class JavaLexer extends JSLexer {
    public static final IBitSet JAVA_SPECIAL = LongBitSet.from("+-\\/*()!~`@#$%^&=,<>.?\"':;|[]{}");

    public final SingleBitSet env = new SingleBitSet();

    public JavaLexer() {}

    /// 读词
    @Override
    public Word readWord() throws ParseException {
        CharSequence input = this.input;
        int i = this.index;

        try {
            while (i < input.length()) {
                int c = input.charAt(i++);
                switch (c) {
                    case '\'':
                        this.index = i;
                        return readConstChar();
                    case '"':
                        this.index = i;
                        return readConstString('"');
                    case '/':
                        this.index = i;
                        Word word = ignoreJavaComment(found);
                        i = this.index;
                        if (word != null) return word;
                        break;
                    default: {
                        if (!WHITESPACE.contains(c)) {
                            this.index = i - 1;
                            if (JAVA_SPECIAL.contains(c)) {
                                return readSymbol();
                            } else if (NUMBER.contains(c)) {
                                return readDigitAdvanced(false);
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
        this.index = i;
        return eof();
    }

    @Override
    protected Word formAlphabetClip(CharSequence s) {
        short kwId = Keyword.indexOf(s);
        if (env.contains(Keyword.type(kwId))) kwId = WordPresets.LITERAL;
        return formClip(kwId, kwId == WordPresets.LITERAL ? s.toString() : Keyword.byId(kwId));
    }

    /// 其他字符
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
            if (JAVA_SPECIAL.contains(c)) {
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
}
