package roj.kscript.parser;

import roj.collect.IBitSet;
import roj.collect.IntList;
import roj.collect.LongBitSet;
import roj.config.ParseException;
import roj.config.word.AbstLexer;
import roj.config.word.LineHandler;
import roj.config.word.Word;
import roj.config.word.WordPresets;
import roj.text.CharList;

import java.util.Arrays;

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
    public static final IBitSet JS_SPECIAL = LongBitSet.preFilled("+-*/()!~`@#%^&=,<>.?:;|[]{}");

    @SuppressWarnings("fallthrough")
    public JSLexer init(CharSequence keys) {
        lastLine = 0;
        IntList lineIndexes = new IntList(100);
        lineIndexes.add(0);

        for (int i = 0; i < keys.length(); i++) {
            char c1 = keys.charAt(i);
            switch (c1) {
                case '\r':
                    if (i + 1 < keys.length() && keys.charAt(i + 1) == '\n') // \r\n
                        i++;
                case '\n':
                    lineIndexes.add(i);
                    break;
            }
        }

        this.lineIndexes = lineIndexes.getRawArray();
        this.lineLen = lineIndexes.size();

        super.init(keys);
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
                        Word word = ignoreStdNote();
                        if (word != null) return word;
                        index = this.index;
                        break;
                    default: {
                        if (!WHITESPACE.contains(c)) {
                            this.index = index - 1;
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
    protected Word readAlphabet() {
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
    protected Word readSpecial() throws ParseException {
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
    protected Word formNumberClip(byte flag, CharList temp) {
        return formClip((short) (WordPresets.INTEGER + flag), temp).number();
    }
}
