package roj.config.word;

import roj.config.ParseException;
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
public class Lexer extends AbstLexer {
    public Lexer init(String s) {
        super.init(s);
        return this;
    }

    public Word readStringToken() throws ParseException {
        while (hasNext()) {
            int c = next();
            switch (c) {
                case '=':
                    return formClip(WordPresets.INTEGER, "=");
                case '\'':
                case '"':
                    return readConstString((char) c);
                default: {
                    if (!WHITESPACE.contains(c)) {
                        retract();
                        return readArg();
                    }
                }
            }
        }
        return eof();
    }

    @Override
    public Word readWord() throws ParseException {
        while (hasNext()) {
            int c = next();
            switch (c) {
                case '\'':
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
                        if (SPECIAL.contains(c)) {
                            if(hasNext() && NUMBER.contains(offset(0))) { // ... may need fix
                                switch (c) {
                                    case '+':
                                        next();
                                    case '-':
                                        return readDigit().negative();
                                }
                            }
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

    private Word readArg() throws ParseException {
        CharList temp = this.found;
        temp.clear();

        while (hasNext()) {
            int c = next();
            if (!WHITESPACE.contains(c) && c != '=') {
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

    /**
     * 其他字符
     */
    @Override
    protected Word readSpecial() throws ParseException {
        return formClip(WordPresets.ERROR, String.valueOf(next()));
    }

    @Override
    protected Word formNumberClip(byte flag, CharList temp) throws ParseException {
        //retract();
        return formClip((short) (WordPresets.INTEGER + flag), temp.toString()).number();
    }
}
