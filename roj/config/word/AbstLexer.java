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
package roj.config.word;

import roj.collect.IBitSet;
import roj.collect.LongBitSet;
import roj.config.ParseException;
import roj.math.MathUtils;
import roj.text.CharList;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/10/31 14:22
 */
public abstract class AbstLexer {
    public static final IBitSet
            NUMBER = LongBitSet.from("0123456789"),
            WHITESPACE = LongBitSet.from(" \r\n\t"),
            SPECIAL = LongBitSet.from("+-\\/*()!~`@#$%^&_=,<>.?\"':;|[]{}"),
            HEX = LongBitSet.from("ABCDEFabcdef"),
            SPECIAL_CHARS = LongBitSet.from(
                    32, 33,
                    // "
                    35, 36, 37, 38,
                    // '
                    40, 41, 42, 43, 44, 45, 46, 47,
                    58, 59, 60, 61, 62, 63,
                    91, 93, 94, 95, 96,
                    123, 124, 125, 126
                                           );

    /**
     * 暂存
     */
    protected final CharList found = new CharList(32);

    //=========== Data
    public int index, lastWord;
    protected CharSequence input;

    // region 初始化

    public AbstLexer() {}

    public AbstLexer init(CharSequence charList) {
        this.index = 0;
        this.input = charList;
        return this;
    }

    // endregion

    /**
     * String 重转义
     */
    public static String addSlashes(CharSequence key) {
        if(key instanceof CharList) {
            return addSlashes0((CharList) key);
        }

        StringBuilder sb = key instanceof StringBuilder ? (StringBuilder) key : new StringBuilder(key);
        for (int i = 0; i < sb.length(); i++) {
            char c = sb.charAt(i);
            switch (c) {
                case '\\':
                case '"':
                    sb.insert(i++, '\\');
                    break;
                case '\n':
                    sb.setCharAt(i, '\\');
                    sb.insert(++i, 'n');
                    break;
                case '\r':
                    sb.setCharAt(i, '\\');
                    sb.insert(++i, 'r');
                    break;
                case '\t':
                    sb.setCharAt(i, '\\');
                    sb.insert(++i, 't');
                    break;
                case '\b':
                    sb.setCharAt(i, '\\');
                    sb.insert(++i, 'b');
                    break;
                case '\f':
                    sb.setCharAt(i, '\\');
                    sb.insert(++i, 'f');
                    break;
            }
        }
        return sb.toString();
    }

    private static String addSlashes0(CharList sb) {
        for (int i = 0; i < sb.length(); i++) {
            char c = sb.charAt(i);
            switch (c) {
                case '\\':
                case '"':
                    sb.insert(i++, '\\');
                    break;
                case '\n':
                    sb.set(i, '\\');
                    sb.insert(++i, 'n');
                    break;
                case '\r':
                    sb.set(i, '\\');
                    sb.insert(++i, 'r');
                    break;
                case '\t':
                    sb.set(i, '\\');
                    sb.insert(++i, 't');
                    break;
                case '\b':
                    sb.set(i, '\\');
                    sb.insert(++i, 'b');
                    break;
                case '\f':
                    sb.set(i, '\\');
                    sb.insert(++i, 'f');
                    break;
            }
        }
        return sb.toString();
    }

    // section 简单操作

    public final boolean hasNext() {
        return index < input.length();
    }

    public char next() {
        return input.charAt(index++);
    }

    public final char offset(int offset) {
        return input.charAt(offset + index);
    }

    public final int remain() {
        return input.length() - index;
    }

    protected void retract(int i) {
        if (this.index < i) {
            throw new IllegalStateException("Unable to retract");
        }

        this.index -= i;
    }

    protected final void retract() {
        retract(1);
    }

    // endregion
    // region Word

    public final Word nextWord() throws ParseException {
        lastWord = index;
        return readWord();
    }

    /**
     * 终止
     */
    public void interrupt() {
        index = input == null ? 0 : input.length();
    }

    public CharSequence getText() {
        return input;
    }

    public static final class Snapshot {
        public int index;
        final CharSequence hash;

        public Snapshot(int index, CharSequence cvHash) {
            this.index = index;
            this.hash = cvHash;
        }

        @Override
        public String toString() {
            return "Snapshot: " + index + " for " + hash;
        }
    }

    public final void restore(Snapshot ss) {
        if (ss.hash != input) {
            throw new ClassCastException("Input was changed.");
        }
        if(ss.index < 0)
            throw new IllegalArgumentException("Invaild index " + ss.index);

        this.index = ss.index;
    }

    public final Snapshot snapshot() {
        return new Snapshot(index, input);
    }

    public final Snapshot snapshot(Snapshot snapshot) {
        snapshot.index = index;
        return snapshot;
    }

    public final void retractWord() {
        if(lastWord == -1)
            throw new IllegalArgumentException("Unable retract");
        index = lastWord;
        lastWord = -1;
    }

    /**
     * 获取转义字符串
     */
    @SuppressWarnings("fallthrough")
    public final CharList readSlashString(char end, boolean zhuanyi) throws ParseException {
        CharSequence in = this.input;
        int i = this.index;

        CharList v = this.found;
        v.clear();

        boolean slash = false;
        boolean quoted = true;

        while (i < in.length()) {
            char c = in.charAt(i++);
            if (slash) {
                i = slashHandler(input, c, v, i, end);
                slash = false;
            } else {
                if(end == c) {
                    quoted = false;
                    break;
                } else {
                    if(zhuanyi && c == '\\') {
                        slash = true;
                    } else {
                        v.append(c);
                    }
                }
            }
        }

        int orig = this.index;
        this.index = i;

        if (slash) {
            throw err("未终止的 SLASH (\\)", orig);
        }
        if (quoted) {
            throw err("未终止的 QUOTE (" + end + ")", orig);
        }

        return v;
    }

    /**
     * 转义符处理
     */
    @SuppressWarnings("fallthrough")
    protected static int slashHandler(CharSequence input, char c, CharList temp, int index, char end) throws ParseException {
        switch (c) {
            case '\'':
            case '"':
                if (end != 0 && end != c)
                    throw new ParseException(input, "无效的转义 \\" + c, index - 1, null);
            case '\\':
                temp.append(c);
                break;
            case 'n':
                temp.append('\n');
                break;
            case 'r':
                temp.append('\r');
                break;
            case 't':
                temp.append('\t');
                break;
            case 'b':
                temp.append('\b');
                break;
            case 'f':
                temp.append('\f');
                break;
            case 'U': // UXXXXXXXX
                int UIndex = MathUtils.parseInt(input, index, index + 8, 16);

                index += 8;

                temp.append((char) UIndex);
                break;
            case 'u': // uXXXX
                int uIndex = MathUtils.parseInt(input, index, index + 4, 16);

                index += 4;

                temp.append((char) uIndex);
                break;
            default:
                throw new ParseException(input, "无效的转义 " + c, index - 1, null);
        }
        return index;
    }

    public static CharList deSlashes(CharSequence in, CharList output) throws ParseException {
        int i = 0;

        boolean slash = false;

        while (i < in.length()) {
            char c = in.charAt(i++);
            if (slash) {
                i = slashHandler(in, c, output, i, '\0');
                slash = false;
            } else {
                if(c == '\\') {
                    slash = true;
                } else {
                    output.append(c);
                }
            }
        }

        if (slash) {
            throw new ParseException(in, "未终止的 SLASH (\\)", i, null);
        }
        return output;
    }

    // region lexer function

    /**
     * 读词
     */
    public abstract Word readWord() throws ParseException;

    /**
     * @return 标识符 or 变量, 或者换句话说, literal
     */
    protected Word readLiteral() throws ParseException {
        CharSequence input = this.input;
        int index = this.index;

        CharList temp = this.found;
        temp.clear();

        while (index < input.length()) {
            int c = input.charAt(index++);
            if (!SPECIAL.contains(c) && !WHITESPACE.contains(c)) {
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

    /**
     * 生成字母(变量/literal)块
     */
    protected Word formAlphabetClip(CharSequence temp) {
        return formClip(WordPresets.LITERAL, temp);
    }

    /**
     * @return 其他字符
     */
    protected Word readSymbol() throws ParseException {
        throw err(getClass().getName() + " 没有覆写readSymbol()并调用了");
    }

    /**
     * int double
     * @return [数字] 块
     * @param sign 检测符号, 必须有1字符空间,因为当作你已知有个符号
     */
    @SuppressWarnings("fallthrough")
    protected Word readDigit(boolean sign) throws ParseException {
        CharSequence input = this.input;
        int i = this.index;

        CharList temp = this.found;
        temp.clear();

        boolean negative = false;
        if(sign) {
            char c = input.charAt(i++);
            //case '+':
            if (c == '-') {
                negative = true;
            }
        }

        /**
         * 0: int
         * 1: double
         * 2: hex
         * 3: bin
         * 4: oct
         */
        byte flag = 0;
        byte exp = 0;

        o:
        while (i < input.length()) {
            char c = input.charAt(i++);
            switch (c) {
                case '_':
                    continue;
                case '.':
                    // 1.xE3
                    if (exp != 0) unexpected(".");
                    flag = 1; // decimal
                    exp |= 1;
                default:
                    if (NUMBER.contains(c) || c == '.' || (flag == 2 && HEX.contains(c))) {
                        temp.append(c);
                        if (flag == 0 && temp.length() == 2 // 075463754
                                && temp.charAt(0) == '0' && temp.charAt(1) != '.') flag = 4;
                    } else {
                        i--;
                        break o;
                    }
                    break;
                case 'X':
                case 'x':
                    if (flag == 0 && temp.length() == 1) {
                        temp.delete(0);
                        flag = 2; // hex
                    } else {
                        this.index = i;
                        unexpected(String.valueOf(c));
                    }
                    break;
                case 'B':
                case 'b':
                    switch (flag) {
                        case 2:
                            temp.append(c);
                            break;
                        case 0:
                            if (temp.length() == 1) {
                                flag = 3;
                                temp.delete(0);
                                break;
                            }
                        default:
                            this.index = i;
                            unexpected(String.valueOf(c));
                            break;

                    }
                    break;
                case '-':
                    if((exp & 2) == 0) {
                        this.index = i;
                        unexpected(String.valueOf(c));
                    } else {
                        temp.append(c);
                    }
                    break;
                case 'E':
                case 'e':
                    if(flag == 2) {
                        temp.append(c);
                        break;
                    }

                    if ((flag == 0 || flag == 1) && temp.length() > 0 && (exp & 2) == 0) {
                        flag = 1;
                        exp |= 2;
                        temp.append(c);
                        break;
                    }

                    this.index = i;
                    unexpected(String.valueOf(c));
                    break;
            }
        }

        this.index = i;

        if (temp.length() == 0) {
            if(i == input.length())
                return eof();
            unexpected(String.valueOf(input.charAt(i)));
        }

        char last = temp.charAt(temp.length() - 1);

        if (last == '.') {
            unexpected(String.valueOf(last));
        }

        if ((exp & 2) != 0 && (last == 'e' || last == 'E'))
            unexpected("exp后的终止");

        try {
            return formNumberClip(flag, temp, negative);
        } catch (NumberFormatException e) {
            throw err("非法的数字 '" + temp.toString() + "': " + e.getMessage());
        }
    }

    /**
     * 6 种数字，尽在掌握
     */
    @SuppressWarnings("fallthrough")
    protected Word readDigitAdvanced(boolean sign) throws ParseException {
        CharSequence input = this.input;
        int i = this.index;

        CharList temp = this.found;
        temp.clear();

        boolean negative = false;
        if(sign) {
            char c = input.charAt(i++);
            //case '+':
            if (c == '-') {
                negative = true;
            }
        }

        /**
         * 0: int
         * 1: double
         * 2: hex
         * 3: bin
         * 4: oct
         * 5: float
         * 6: long
         */
        byte flag = 0;
        byte exp = 0;

        o:
        while (i < input.length()) {
            char c = input.charAt(i++);
            switch (c) {
                case '_':
                    continue;
                case '.':
                    // 1.xE3
                    if (exp != 0) unexpected(".");
                    flag = 1; // decimal
                    exp |= 1;
                default:
                    if (NUMBER.contains(c) || c == '.' || (flag == 2 && HEX.contains(c))) {
                        temp.append(c);
                        if (flag == 0 && temp.length() == 2 // 075463754
                            && temp.charAt(0) == '0' && temp.charAt(1) != '.') flag = 4;
                    } else {
                        i--;
                        break o;
                    }
                    break;
                case 'X':
                case 'x':
                    if (flag == 0 && temp.length() == 1) {
                        temp.delete(0);
                        flag = 2; // hex
                    } else {
                        this.index = i;
                        unexpected(String.valueOf(c));
                    }
                    break;
                case 'B':
                case 'b':
                    switch (flag) {
                        case 2:
                            temp.append(c);
                            break;
                        case 0:
                            if (temp.length() == 1) {
                                flag = 3;
                                temp.delete(0);
                                break;
                            }
                        default:
                            this.index = i;
                            unexpected(String.valueOf(c));
                            break;

                    }
                    break;
                case 'D':
                case 'd':
                case 'E':
                case 'e':
                case 'F':
                case 'f':
                    if(flag == 2) {
                        temp.append(c);
                        break;
                    }

                    boolean ex = c == 'e' || c == 'E';
                    // if and only if => !(exp && ex) throw error
                    // => if(!ex || !exp)

                    if ((flag == 0 || flag == 1) && temp.length() > 0 && (!ex || (exp & 2) == 0)) {
                        flag = (byte) (c == 'F' || c == 'f' ? 5 : 1);

                        if(ex) {
                            exp |= 2;
                            break;
                        } else {
                            break o;
                        }
                    }

                    this.index = i;
                    unexpected(String.valueOf(c));
                    break;
                case 'L':
                case 'l':
                    if (flag == 0 && temp.length() > 0) {
                        flag = 6;
                        break o;
                    }

                    this.index = i;
                    unexpected(String.valueOf(c));
                    break;
            }
        }

        this.index = i;

        if (temp.length() == 0) {
            if(i == input.length())
                return eof();
            unexpected(String.valueOf(input.charAt(i)));
        }

        char last = temp.charAt(temp.length() - 1);

        if (last == '.') {
            unexpected(String.valueOf(last));
        }

        if ((exp & 2) != 0 && (last == 'e' || last == 'E'))
            unexpected("exp后的终止");

        try {
            return formNumberClip(flag, temp, negative);
        } catch (NumberFormatException e) {
            throw err("非法的数字 '" + temp.toString() + "': " + e.getMessage());
        }
    }

    protected Word formNumberClip(byte flag, CharList temp, boolean negative) throws ParseException {
        return formClip((short) (WordPresets.INTEGER + flag), temp).number(negative);
    }

    /**
     * 获取常量字符
     */
    protected final Word readConstChar() throws ParseException {
        CharList list = readSlashString('\'', true);
        if (list.length() != 1)
            throw err("未结束的字符常量");
        return formClip(WordPresets.CHARACTER, list);
    }

    /**
     * 获取常量字符串
     */
    protected Word readConstString(char key) throws ParseException {
        return formClip(WordPresets.STRING, readSlashString(key, true));
    }

    /**
     * 封装词 // 缓存
     *
     * @param id 词类型
     */
    protected Word formClip(short id, CharSequence s) {
        if(cached == null) {
            return new Word().reset(id, index, s.toString());
        }
        Word w = cached.reset(id, index, s.toString());
        cached = null;
        return w;
    }

    protected Word cached;

    /**
     * 回收利用
     */
    public void recycle(Word word) {
        cached = word;
    }

    /**
     * 文件结束
     */
    protected final Word eof() {
        return new Word(index);
    }

    @Override
    public String toString() {
        return "Lexer{" + "index=" + index + ", chars=" + this.input;
    }

    /**
     * 忽略 // 或 /* ... *\/ 注释
     */
    @SuppressWarnings("fallthrough")
    protected final Word ignoreStdNote() throws ParseException {
        CharSequence input = this.input;
        int index = this.index;
        if (index < input.length()) {
            int c = input.charAt(index++);
            switch (c) {
                case '/': {
                    o:
                    while (index < input.length()) {
                        switch (input.charAt(index++)) {
                            case '\r':
                                if(index < input.length() && input.charAt(index) == '\n')
                                    index++;
                            case '\n':
                                break o;
                        }
                    }
                }
                break;
                case '*': {
                    while (index < input.length()) {
                        if (input.charAt(index++) == '*') {
                            if (index >= input.length()) {
                                this.index = index;
                                return eof();
                            }

                            if (input.charAt(index++) == '/') {
                                break;
                            }
                        }
                    }
                }
                break;
                default:
                    this.index--;
                    return readSymbol();
            }
        }
        this.index = index;

        if (index >= input.length()) {
            return eof();
        }
        return null;
    }

    // endregion
    // region exception

    protected final void unexpected(String val) throws ParseException {
        throw err("未预料的'" + val + "'");
    }

    protected final ParseException err(String reason, int index) {
        return new ParseException(input, reason, index, null);
    }

    public ParseException err(String reason, Word word) {
        return new ParseException(input, reason + " 在 " + word.val(), word.getIndex(), null);
    }

    public final ParseException err(String reason) {
        return err(reason, (Throwable) null);
    }

    public ParseException err(String reason, Throwable cause) {
        return new ParseException(input, reason, this.index - 1, cause);
    }

    // endregion
}
