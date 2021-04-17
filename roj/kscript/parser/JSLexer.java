package roj.kscript.parser;

import roj.collect.IBitSet;
import roj.collect.LongBitSet;
import roj.config.ParseException;
import roj.config.word.AbstLexer;
import roj.config.word.Word;
import roj.config.word.WordPresets;
import roj.text.CharList;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 * <p>
 * 词法分析
 *
 * @author solo6975
 * @since 2020/10/3 19:20
 */
public class JSLexer extends AbstLexer {
    public static final IBitSet JS_SPECIAL = LongBitSet.preFilled('+', '-', '*', '/', '(', ')', '!', '~', '`', '@', '#', '%', '^', '&', '=', ',', '<', '>', '.', '?', ':', ';', '|', '[', ']', '{', '}');

    public JSLexer init(String s) {
        init(new CharList(s.toCharArray()));
        return this;
    }

    /// 读词
    @Override
    public Word readWord() throws ParseException {
        while (hasNext()) {
            int c = next();
            switch (c) {
                case '\'':
                    return readConstChar();
                case '"':
                    return readConstString((char) c);
                case '/':
                    Word word = ignoreStdNote();
                    if (word != null)
                        return word;
                    break;
                default: {
                    if (!WHITESPACE.contains(c)) {
                        retract();
                        if (JS_SPECIAL.contains(c)) {
                            return readSpecial();
                        } else if (NUMBER.contains(c)) {
                            return readDigit();
                        } else {
                            return readAlphabet();
                        }
                    }
                }
            }
        }
        return eof();
    }

    @Override
    protected Word readAlphabet() {
        CharList temp = this.found;
        temp.clear();

        while (hasNext()) {
            int c = next();
            if (!JS_SPECIAL.contains(c) && !WHITESPACE.contains(c)) {
                temp.append((char) c);
            } else {
                retract();
                break;
            }
        }
        if (temp.length() == 0) {
            return eof();
        }

        return formAlphabetClip(temp);
    }

    @Override
    protected Word formAlphabetClip(CharList temp) {
        String s = temp.toString();
        return formClip(Keyword.indexOf(s), s);
    }

    /// 其他字符
    @Override
    protected Word readSpecial() throws ParseException {
        CharList temp = this.found;
        temp.clear();

        final int begin = index;

        short wasFound = WordPresets.ERROR;
        int wasFoundLen = 0;

        while (hasNext()) {
            char c = next();
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
                retract(1);
                break;
            }
        }

        if (wasFound != WordPresets.ERROR) {
            temp.setIndex(wasFoundLen);
            retract(index - begin - wasFoundLen);

            return formClip(wasFound, temp.toString());
        }

        if (temp.length() == 0) {
            return eof();
        }
        throw err("未知T_SPECIAL '" + temp.toString() + "'");
    }

    @Override
    protected Word formNumberClip(byte flag, CharList temp) {
        return formClip((short) (WordPresets.INTEGER + flag), temp.toString()).number();
    }
}
