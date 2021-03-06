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

import roj.collect.MyBitSet;
import roj.config.ParseException;
import roj.math.MathUtils;
import roj.text.ACalendar;
import roj.text.CharList;

/**
 * @author Roj234
 * @since  2020/10/31 14:22
 */
public abstract class AbstLexer {
    public static final MyBitSet
            NUMBER = MyBitSet.from("0123456789"),
            WHITESPACE = MyBitSet.from(" \r\n\t"),
            SPECIAL = MyBitSet.from("+-\\/*()!~`@#$%^&_=,<>.?\"':;|[]{}"),
            SPECIAL_CHARS = MyBitSet.from(
                    32, 33,
                    // "
                    35, 36, 37, 38,
                    // '
                    40, 41, 42, 43, 44, 45, 46, 47,
                    58, 59, 60, 61, 62, 63,
                    91, 93, 94, 95, 96,
                    123, 124, 125, 126);
    public static final MyBitSet HEX_Alphabet = MyBitSet.from("ABCDEFabcdef");

    /**
     * ??????
     */
    protected final CharList found = new CharList(32);

    //=========== Data
    public int index, lastWord;
    protected CharSequence input;

    // region ?????????

    public AbstLexer() {}

    public AbstLexer init(CharSequence seq) {
        this.index = 0;
        this.input = seq;
        return this;
    }

    // endregion

    /**
     * String ?????????
     */
    public static String addSlashes(CharSequence key) {
        return addSlashes(key, new StringBuilder()).toString();
    }

    public static CharList addSlashes(CharSequence key, CharList to) {
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            switch (c) {
                case '\\':
                case '"':
                    to.append('\\').append(c);
                    break;
                case '\n':
                    to.append("\\n");
                    break;
                case '\r':
                    to.append("\\r");
                    break;
                case '\t':
                    to.append("\\t");
                    break;
                case '\b':
                    to.append("\\b");
                    break;
                case '\f':
                    to.append("\\f");
                    break;
                default:
                    to.append(c);
            }
        }
        return to;
    }

    public static StringBuilder addSlashes(CharSequence key, StringBuilder to) {
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            switch (c) {
                case '\\':
                case '"':
                    to.append('\\').append(c);
                    break;
                case '\n':
                    to.append("\\n");
                    break;
                case '\r':
                    to.append("\\r");
                    break;
                case '\t':
                    to.append("\\t");
                    break;
                case '\b':
                    to.append("\\b");
                    break;
                case '\f':
                    to.append("\\f");
                    break;
                default:
                    to.append(c);
            }
        }
        return to;
    }

    // section ????????????

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
     * ??????
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
     * ?????????????????????
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
            throw err("???????????? SLASH (\\)", orig);
        }
        if (quoted) {
            throw err("???????????? QUOTE (" + end + ")", orig);
        }

        return v;
    }

    /**
     * ???????????????
     */
    @SuppressWarnings("fallthrough")
    protected static int slashHandler(CharSequence input, char c, CharList temp, int index, char end) throws ParseException {
        switch (c) {
            case '\'':
            case '"':
                if (end != 0 && end != c)
                    throw new ParseException(input, "??????????????? \\" + c, index - 1, null);
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
                throw new ParseException(input, "??????????????? " + c, index - 1, null);
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
            throw new ParseException(in, "???????????? SLASH (\\)", i, null);
        }
        return output;
    }

    // region lexer function

    /**
     * ??????
     */
    public abstract Word readWord() throws ParseException;

    /**
     * @return ????????? or ??????, ??????????????????, literal
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
     * ????????????(??????/literal)???
     */
    protected Word formAlphabetClip(CharSequence temp) {
        return formClip(WordPresets.LITERAL, temp);
    }

    /**
     * @return ????????????
     */
    protected Word readSymbol() throws ParseException {
        throw err(getClass().getName() + " ????????????readSymbol()????????????");
    }

    /**
     * int double
     * @return [??????] ???
     * @param sign ????????????, ?????????1????????????,?????????????????????????????????
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
                    if (exp != 0) {
                        return onInvalidNumber('.', i);
                    }
                    flag = 1; // decimal
                    exp |= 1;
                default:
                    if (NUMBER.contains(c) || c == '.' || (flag == 2 && HEX_Alphabet.contains(c))) {
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
                        return onInvalidNumber(c, i);
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
                            return onInvalidNumber(c, i);

                    }
                    break;
                case '+':
                case '-':
                    if((exp & 2) == 0) {
                        return onInvalidNumber(c, i);
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
                    if (temp.length() <= 0 || temp.charAt(temp.length() - 1) != '.') {
                        if ((flag == 0 || flag == 1) && temp.length() > 0 && (exp & 2) == 0) {
                            flag = 1;
                            exp |= 2;
                            temp.append(c);
                            break;
                        }
                    }

                    return onInvalidNumber(c, i);
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
            unexpected(".");
        }

        if ((exp & 2) != 0 && (last == 'e' || last == 'E'))
            throw err("???????????????????????????");

        if (i < input.length() && !WHITESPACE.contains(input.charAt(i)) && !SPECIAL.contains(input.charAt(i))) {
            Word word = onInvalidNumber(input.charAt(i), i);
            if (word != null) return word;
        }

        try {
            return formNumberClip(flag, temp, negative);
        } catch (NumberFormatException e) {
            throw err("??????????????? '" + temp.toString() + "': " + e.getMessage());
        }
    }

    /**
     * 6 ????????????????????????
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
                    if (exp != 0) return onInvalidNumber(c, i);
                    flag = 1; // decimal
                    exp |= 1;
                default:
                    if (NUMBER.contains(c) || c == '.' || (flag == 2 && HEX_Alphabet.contains(c))) {
                        temp.append(c);
                        if (flag == 0 && temp.length() == 2 // 075463754
                            && temp.charAt(0) == '0' && temp.charAt(1) != '.') flag = 4;
                    } else {
                        i--;
                        break o;
                    }
                    break;
                case '+':
                case '-':
                    if((exp & 2) == 0) {
                        return onInvalidNumber(c, i);
                    } else {
                        temp.append(c);
                    }
                    break;
                case 'X':
                case 'x':
                    if (flag == 0 && temp.length() == 1) {
                        temp.delete(0);
                        flag = 2; // hex
                    } else {
                        return onInvalidNumber(c, i);
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
                            return onInvalidNumber(c, i);

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
                    if (temp.length() <= 0 || temp.charAt(temp.length() - 1) != '.') {
                        if ((flag == 0 || flag == 1) && temp.length() > 0 && (!ex || (exp & 2) == 0)) {
                            flag = (byte) (c == 'F' || c == 'f' ? 5 : 1);

                            if (ex) {
                                exp |= 2;
                                break;
                            } else {
                                break o;
                            }
                        }
                    }

                    return onInvalidNumber(c, i);
                case 'L':
                case 'l':
                    if (flag == 0 && temp.length() > 0) {
                        flag = 6;
                        break o;
                    }

                    return onInvalidNumber(c, i);
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
            unexpected(".");
        }

        if ((exp & 2) != 0 && (last == 'e' || last == 'E'))
            throw err("???????????????????????????");

        if (i < input.length() && !WHITESPACE.contains(input.charAt(i)) && !SPECIAL.contains(input.charAt(i))) {
            Word word = onInvalidNumber(input.charAt(i), i);
            if (word != null) return word;
        }

        try {
            return formNumberClip(flag, temp, negative);
        } catch (NumberFormatException e) {
            throw err("??????????????? '" + temp.toString() + "': " + e.getMessage());
        }
    }

    protected Word formNumberClip(byte flag, CharList temp, boolean negative) throws ParseException {
        return formClip((short) (WordPresets.INTEGER + flag), temp).number(this, negative);
    }

    protected Word onInvalidNumber(char value, int i) throws ParseException {
        this.index = i;
        throw err("?????????????????????");
    }

    protected void onNumberFlow(String value, short fromLevel, short toLevel) throws ParseException {}

    /**
     * ??????????????????
     */
    protected final Word readConstChar() throws ParseException {
        CharList list = readSlashString('\'', true);
        if (list.length() != 1)
            throw err("????????????????????????");
        return formClip(WordPresets.CHARACTER, list);
    }

    /**
     * ?????????????????????
     */
    protected Word readConstString(char key) throws ParseException {
        return formClip(WordPresets.STRING, readSlashString(key, true));
    }

    protected Word_L formRFCTime(boolean must) throws ParseException {
        CharSequence val = getText();
        int j = this.index, i = j;

        int end = Math.min(j + 30, val.length()); // "0000-00-00Z\t00:00:00.000+00:00".length()
        char c;
        long ts = 0;
        int y, m, d;
        do {
            if (j >= end) return null;
            c = val.charAt(j);
            if (!NUMBER.contains(c)) break;
            j++;
        } while (true);
        if (c != '-' && c != '/' && c != ':') if (!must) return null; else throw err("???????????????", j);
        if (c != ':') {
            y = dateNum(val, i, j, must);
            i = ++j;

            do {
                if (j >= end) return null;
                c = val.charAt(j);
                if (!NUMBER.contains(c)) break;
                j++;
            } while (true);
            if (c != '-' && c != '/') if (!must) return null; else throw err("???????????????", j);
            m = dateNum(val, i, j, must);
            if (m == 0 || m > 12) if (!must) return null; else throw err("??????????????????????????????" + m + "??????", i);
            i = ++j;

            do {
                if (j >= end) break;
                c = val.charAt(j);
                if (!NUMBER.contains(c)) break;
                j++;
            } while (true);
            d = dateNum(val, i, j, must);
            if (d == 0 || d > 31) if (!must) return null; else throw err("?????????????????????????????????" + d + "???", i);

            ts = (ACalendar.daySinceAD(y, m, d, null) - ACalendar.GREGORIAN_OFFSET_DAY) * 86400000L;
            if (c != 'T' && c != 't' && c != ' ') {
                Word_L w = new Word_L(WordPresets.RFCDATE_DATE, ts, val.subSequence(index, j-1).toString());
                this.index = j;
                return w;
            }

            do {
                if (j >= end) return null;
                c = val.charAt(j);
                if (!NUMBER.contains(c)) break;
                j++;
            } while (true);
            i = ++j;

            do {
                if (j >= end) return null;
                c = val.charAt(j);
                if (!NUMBER.contains(c)) break;
                j++;
            } while (true);
            if (c != ':') if (!must) return null; else throw err("???????????????", j);
        }
        y = dateNum(val, i, j, must);
        i = ++j;

        do {
            if (j >= end) return null;
            c = val.charAt(j);
            if (!NUMBER.contains(c)) break;
            j++;
        } while (true);
        if (c != ':') if (!must) return null; else throw err("???????????????", j);
        m = dateNum(val, i, j, must);
        i = ++j;

        do {
            if (j >= end) return null;
            c = val.charAt(j);
            if (!NUMBER.contains(c)) break;
            j++;
        } while (true);
        d = dateNum(val, i, j, must);
        if (y > 23) if (!must) return null; else throw err("?????????" + y + "??????", i);
        if (m > 59) if (!must) return null; else throw err("????????????" + m + "??????", i);
        if (d > 59) if (!must) return null; else throw err("????????????" + d + "???", i);
        i = ++j;

        ts += y * 3600000 + m * 60000 + d * 1000;
        if (c == '.') {
            do {
                if (j >= end) break;
                c = val.charAt(j);
                if (!NUMBER.contains(c)) break;
                j++;
            } while (true);
            y = dateNum(val, i, j, must);
            if (y < 0 || y > 1000) if (!must) return null; else throw err("????????????", i);
            i = ++j;
            ts += y;
        }

        if (c != '+' && c != '-') {
            Word_L w = new Word_L(c == 'Z' || c == 'z' ? WordPresets.RFCDATE_DATETIME_TZ : WordPresets.RFCDATE_DATETIME, ts, val.subSequence(index, j-1).toString());
            this.index = i;
            return w;
        }
        d = c == '+' ? 1 : -1;

        do {
            if (j >= end) return null;
            c = val.charAt(j);
            if (!NUMBER.contains(c)) break;
            j++;
        } while (true);
        if (c != ':') if (!must) return null; else throw err("???????????????", j);
        y = dateNum(val, i, j, must);
        if (y > 23) if (!must) return null; else throw err("?????????" + y + "??????", i);
        i = ++j;

        do {
            if (j >= end) break;
            c = val.charAt(j);
            if (!NUMBER.contains(c)) break;
            j++;
        } while (true);
        m = dateNum(val, i, j, must);
        if (m > 59) if (!must) return null; else throw err("????????????" + m + "??????", i);

        long timezoneOffset = y * 3600000 + m * 60000;
        Word_L w = new Word_L(WordPresets.RFCDATE_DATETIME_TZ, ts + d * timezoneOffset, val.subSequence(index, j-1).toString());
        this.index = j;
        return w;
    }

    private int dateNum(CharSequence val, int f, int t, boolean must) throws ParseException {
        try {
            return MathUtils.parseInt(val, f, t, 10);
        } catch (NumberFormatException e) {
            if (must) return Integer.MAX_VALUE; else throw err("???????????????", f);
        }
    }

    /**
     * ????????? // ??????
     *
     * @param id ?????????
     */
    protected Word formClip(short id, CharSequence s) {
        if(cached == null) {
            return new Word().reset(id, index, s.toString());
        }
        Word w = cached.reset(id, index, s.toString());
        //cached = null;
        return w;
    }

    protected Word cached;

    /**
     * ????????????
     */
    public void recycle(Word word) {
        cached = word;
    }

    /**
     * ????????????
     */
    protected final Word eof() {
        return new Word(index);
    }

    @Override
    public String toString() {
        return "Lexer{" + "index=" + index + ", chars=" + this.input;
    }

    /**
     * ?????? // ??? /* ... *\/ ??????
     * @param col add comment to
     */
    @SuppressWarnings("fallthrough")
    protected final Word ignoreJavaComment(CharList col) throws ParseException {
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
                    if (col != null)
                        col.append(input, this.index + 1, index);
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
                    if (col != null)
                        col.append(input, this.index + 1, index - 1);
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

    public final void unexpected(String val) throws ParseException {
        throw err("????????????'" + val + "'");
    }

    public final ParseException err(String reason, int index) {
        return new ParseException(input, reason, index, null);
    }

    public ParseException err(String reason, Word word) {
        return new ParseException(input, reason + " ??? " + word.val(), word.getIndex(), null);
    }

    public final ParseException err(String reason) {
        return err(reason, (Throwable) null);
    }

    public ParseException err(String reason, Throwable cause) {
        return new ParseException(input, reason, this.index - 1, cause);
    }

    // endregion
}
