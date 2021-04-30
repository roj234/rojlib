package roj.config.word;

import roj.collect.IBitSet;
import roj.collect.LongBitSet;
import roj.config.ParseException;
import roj.math.MathUtils;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.ByteReader;

import java.io.UTFDataFormatException;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/10/31 14:22
 */
public abstract class AbstLexer {
    public static final IBitSet
            NUMBER = LongBitSet.preFilled("0123456789"),
            WHITESPACE = LongBitSet.preFilled(" \r\n\t"),
            SPECIAL = LongBitSet.preFilled("+-\\/*()!~`@#$%^&_=,<>.?\"':;|[]{}"),
            HEX = LongBitSet.preFilled("ABCDEFabcdef"),
            SPECIAL_CHARS = LongBitSet.preFilled(
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
    public int index;

    protected Snapshot last;
    protected CharSequence input;

    // region 初始化

    public AbstLexer() {}

    public AbstLexer(CharSequence str) {
        this.input = str;
    }

    public AbstLexer(byte[] bytes) {
        init(bytes);
    }

    public AbstLexer(char[] str) {
        this.input = new CharList(str);
    }

    public AbstLexer init(byte[] bytes) {
        try {
            return init(ByteReader.readUTF(new ByteList(bytes)));
        } catch (UTFDataFormatException e) {
            throw new RuntimeException(e);
        }
    }

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
        if(last == null) {
            last = snapshot();
            last.hash = 0;
        } else {
            snapshot(last);
        }

        return readWord();
    }

    /**
     * 终止
     */
    public void interrupt() {
        index = input == null ? 0 : input.length();
    }

    public static final class Snapshot {
        public int index;
        private int hash;

        public Snapshot(int index, CharSequence cvHash) {
            this.index = index;
            this.hash = System.identityHashCode(cvHash);
        }

        @Override
        public String toString() {
            return "Snapshot: " + index + " for " + hash;
        }
    }

    public final void restore(Snapshot ss) {
        if (ss.hash != 0 && ss.hash != System.identityHashCode(input)) {
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
        restore(last);
        last.index = -1;
    }

    /**
     * 获取转义字符串
     */
    @SuppressWarnings("fallthrough")
    public final CharList readSlashString(char end) throws ParseException {
        CharSequence input = this.input;
        int index = this.index;

        CharList temp = this.found;
        temp.clear();

        boolean slash = false;
        boolean quoted = true;

        o:
        while (index < input.length()) {
            char c = input.charAt(index++);
            if (slash) {
                index = slashHandler(c, temp, index);
                slash = false;
            } else {
                switch (c) {
                    case '\'':
                    case '"':
                        if(end == c) {
                            quoted = false;
                            break o;
                        } else {
                            temp.append(c);
                        }
                        break;
                    case '\\':
                        slash = true;
                        break;
                    default:
                        temp.append(c);
                }
            }
        }

        this.index = index;

        if (slash) {
            throw err("Unterminated T_SLASH (\\)");
        }
        if (quoted) {
            throw err("Unterminated T_QUOTE (" + end + ")");
        }

        return temp;
    }

    /**
     * 转义符处理
     */
    protected final int slashHandler(char c, CharList temp, int index) throws ParseException {
        switch (c) {
            case '\'':
            case '"':
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
                int UIndex = MathUtils.parseInt(false, input.subSequence(index, index + 8), 16);

                index += 8;

                temp.append((char) UIndex);
                break;
            case 'u': // uXXXX
                int uIndex = MathUtils.parseInt(false, input.subSequence(index, index + 4), 16);

                index += 4;

                temp.append((char) uIndex);
                break;
            default:
                throw new ParseException(input, "Unexpected \\" + c, index - 1, null);
        }
        return index;
    }

    // region lexer function

    /**
     * 读词
     */
    public abstract Word readWord() throws ParseException;

    /**
     * @return 标识符 or 变量, 或者换句话说, literal
     */
    protected Word readAlphabet() throws ParseException {
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
    protected Word formAlphabetClip(CharList temp) throws ParseException {
        return formClip(WordPresets.LITERAL, temp);
    }

    /**
     * @return 其他字符
     */
    protected abstract Word readSpecial() throws ParseException;

    /**
     * @return [数字] 块
     */
    @SuppressWarnings("fallthrough")
    protected Word readDigit() throws ParseException {
        CharSequence input = this.input;
        int index = this.index;

        CharList temp = this.found;
        temp.clear();

        byte flag = 0;

        o:
        while (index < input.length()) {
            char c = input.charAt(index++);
            switch (c) {
                case 'E':
                case 'e':
                    if (flag != 2) {
                        if ((flag & 128) == 0 && flag != 3) {
                            flag = (byte) (1 | 128);
                            temp.append(c);
                        } else {
                            this.index = index;
                            unexpected(String.valueOf(c));
                        }
                    } else {
                        temp.append(c);
                    }
                    break;
                case '_':
                    continue;
                case '.':
                    if (flag != 0) unexpected(".");
                    flag = 1; // decimal
                default:
                    if (NUMBER.contains(c) || c == '.' || (flag == 2 && HEX.contains(c))) {
                        temp.append(c);
                        if (flag == 0 && temp.length() == 2) { // 075463754
                            if (temp.charAt(0) == '0' && temp.charAt(1) != '.') flag = 4;
                        }
                    } else {
                        index--;
                        break o;
                    }
                    break;
                case 'X':
                case 'x':
                    if (flag == 0 && temp.length() == 1) {
                        temp.delete(0);
                        flag = 2; // hex
                    } else {
                        this.index = index;
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
                            this.index = index;
                            unexpected(String.valueOf(c));
                            break;

                    }
            }
        }

        this.index = index;

        if (temp.length() == 0) {
            return eof();
        }

        flag &= 127;

        char last = temp.charAt(temp.length() - 1);

        if (!NUMBER.contains(last) && (flag != 2 || !HEX.contains(last))) {
            unexpected(String.valueOf(last));
        }

        return formNumberClip(flag, temp);
    }

    protected abstract Word formNumberClip(byte flag, CharList temp) throws ParseException;

    /**
     * 获取常量字符
     */
    protected final Word readConstChar() throws ParseException {
        CharList list = readSlashString('\'');
        if (list.length() != 1)
            throw err("未结束的字符常量");
        return formClip(WordPresets.CHARACTER, list);
    }

    /**
     * 获取常量字符串
     */
    protected final Word readConstString(char key) throws ParseException {
        return formClip(WordPresets.STRING, readSlashString(key));
    }

    /**
     * 封装词 // 缓存
     *
     * @param id 词类型
     */
    protected Word formClip(short id, CharSequence string) {
        if(cached == null) {
            return new Word().reset(id, index, string.toString());
        }
        Word w = cached.reset(id, index, string.toString());
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
                    return readSpecial();
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

    public final ParseException err(String reason, Word word) {
        return new ParseException(input, reason + " 在 " + word.val(), word.getIndex(), null);
    }

    public final ParseException err(String reason) {
        return err(reason, (Throwable) null);
    }

    public final ParseException err(String reason, Throwable cause) {
        return new ParseException(input, reason, this.index, cause);
    }

    // endregion
}
