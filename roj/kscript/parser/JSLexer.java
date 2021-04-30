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

        lineIndexes.add(Integer.MAX_VALUE);
        this.lineIndexes = lineIndexes.getRawArray();


        super.init(keys);
        return this;
    }

    int lastLine;
    int[] lineIndexes;
    LineHandler lh;

    public final void setLineHandler(LineHandler lh) {
        this.lh = lh;
        lh.handleLineNumber(lastLine + 1);
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
                        index = this.index;
                        if (word != null) return word;
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

    /* Like public version, but without range checks.
    private static int binarySearch0(int[] a, int fromIndex, int toIndex,
                                     int key) {
        int low = fromIndex;
        int high = toIndex - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            int midVal = a[mid];

            if (midVal < key)
                low = mid + 1;
            else if (midVal > key) {
                high = mid - 1;
                if(mid < toIndex && a[mid + 1] < key) { // my finder
                    return mid + 1;
                }
            } else
                return mid; // key found
        }
        return -(low + 1);  // key not found.
    }*/

    private void applyLineHandler() {
        if(lh != null) {
            int index = this.index;
            int line = lastLine;
            int prevIndex = lineIndexes[line];
            while (index > prevIndex) {
                prevIndex = lineIndexes[line++];
            }

            if(line != lastLine) {
                lh.handleLineNumber((lastLine = line) + 1);
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
    protected Word formAlphabetClip(CharList temp) {
        String s = temp.toString();
        return formClip(Keyword.indexOf(s), s);
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
            temp.setIndex(wasFoundLen);

            return formClip(wasFound, temp.toString());
        }
        this.index = index;

        if (temp.length() == 0) {
            return eof();
        }

        throw err("未知T_SPECIAL '" + temp + "'");
    }

    @Override
    protected Word formNumberClip(byte flag, CharList temp) {
        return formClip((short) (WordPresets.INTEGER + flag), temp.toString()).number();
    }
}
