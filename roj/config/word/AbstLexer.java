package roj.config.word;

import roj.collect.IBitSet;
import roj.collect.LongBitSet;
import roj.config.ParseException;
import roj.math.MathUtils;
import roj.text.CharList;
import roj.text.SimpleLineReader;
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
            SPECIAL = LongBitSet.preFilled('+', '-', '\\', '/', '*', '(', ')', '!', '~', '`', '@', '#', '$', '%', '^', '&', '_', '=', ',', '<', '>', '.', '?', '"', '\'', ':', ';', '|', '[', ']', '{', '}'),
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

    protected final CharList found = new CharList(32);
    private LineHandler lh;

    //=========== Data
    public int index;

    protected Snapshot last;
    protected CharSequence input;

    //=========== Exception Helper
    protected int line, lineOffset, prevLineEnd;

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

    // section accessor / mutator

    public final void setLineHandler(LineHandler lh) {
        this.lh = lh;
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
        this.lineOffset = 0;
        this.line = 0;
        this.input = charList;
        return this;
    }

    // section util

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

    // section basic operation

    public final boolean hasNext() {
        return index < input.length();
    }

    public char next() {
        char c = input.charAt(index++);
        lineOffset++;
        if (c == '\r' || c == '\n') {
            if(c == '\r' && index < input.length() && input.charAt(index) == '\n')
                index++;
            line++;
            prevLineEnd = lineOffset;
            lineOffset = 0;
            if (lh != null)
                lh.handleLineNumber(line);
        }
        return c;
    }

    public final char offset(int offset) {
        return input.charAt(offset + index);
    }

    public final int remain() {
        return input.length() - index;
    }

    protected void retract(int i) {
        this.lineOffset -= i;
        this.index -= i;
        if (this.index < 0) {
            throw new IllegalStateException("Unable to retract");
        }
        if (this.lineOffset < 0) {
            if (this.prevLineEnd == -1) {
                throw new IllegalStateException("Unable to retract");
            }
            this.lineOffset = this.prevLineEnd + this.lineOffset;
            this.prevLineEnd = -1;
            this.line--;
        }
    }

    protected final void retract() {
        retract(1);
    }

    // section advanced operation

    public final Word nextWord() throws ParseException {
        last = last == null ? snapshot() : snapshot(last);
        return readWord();
    }

    public static final class Snapshot {
        public int index, line, lineOffset;

        public Snapshot(int index, int line, int lineOffset) {
            this.index = index;
            this.line = line;
            this.lineOffset = lineOffset;
        }

        @Override
        public String toString() {
            return "Snapshot{" + "index=" + index + ", line=" + line + ", lineOffset=" + lineOffset + '}';
        }
    }

    public final void restore(Snapshot snapshot) {
        if (snapshot.index < 0) {
            return;
        }

        this.index = snapshot.index;
        this.line = snapshot.line;
        this.lineOffset = snapshot.lineOffset;
    }

    public final Snapshot snapshot() {
        return new Snapshot(index, line, lineOffset);
    }

    public final Snapshot snapshot(Snapshot snapshot) {
        snapshot.index = index;
        snapshot.line = line;
        snapshot.lineOffset = lineOffset;
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

        CharList temp = this.found;
        temp.clear();

        int index = this.index;

        boolean slash = false;
        boolean quoted = true;

        int lineOffset = this.lineOffset;

        outer:
        for (; index < input.length(); lineOffset++) {
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
                            break outer;
                        } else {
                            temp.append(c);
                        }
                        break;
                    case '\\':
                        slash = true;
                        break;
                    case '\r':
                        if(index < input.length() && input.charAt(index) == '\n') {
                            index++;
                            lineOffset++;
                        }
                    case '\n':
                        line++;
                        prevLineEnd = lineOffset;
                        lineOffset = 0;
                    default:
                        temp.append(c);
                }
            }
        }

        this.index = index;
        this.lineOffset = lineOffset;

        if (slash) {
            throw atCurrentChar("Unterminated T_SLASH (\\)");
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
                throw new ParseException("Unexpected \\" + c, index - 1, line, lineOffset);
        }
        return index;
    }

    // section lexer function

    /**
     * 读词
     */
    public abstract Word readWord() throws ParseException;

    /**
     * @return 标识符 or 变量
     */
    protected Word readAlphabet() throws ParseException {
        CharList temp = this.found;
        temp.clear();

        while (hasNext()) {
            int c = next();
            if (!SPECIAL.contains(c) && !WHITESPACE.contains(c)) {
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

    protected Word formAlphabetClip(CharList temp) throws ParseException {
        return formClip(WordPresets.VARIABLE, temp);
    }

    /**
     * @return 其他字符
     */
    protected abstract Word readSpecial() throws ParseException;

    /**
     * 识别数字
     */
    @SuppressWarnings("fallthrough")
    protected Word readDigit() throws ParseException {
        CharList temp = this.found;
        temp.clear();

        byte flag = 0;
        //boolean neg = false;

        o:
        while (hasNext()) {
            char c = next();
            //if(c == '-') {
            //    if(!neg)
            //        neg = true;
            //    else
            //        unexpected("-");
            //}
            switch (c) {
                case 'E':
                case 'e':
                    if (flag != 2) {
                        if ((flag & 128) == 0 && flag != 3) {
                            flag = (byte) (1 | 128);
                            temp.append(c);
                        } else {
                            unexpected(String.valueOf(c));
                        }
                    } else {
                        temp.append(c);
                    }
                    break;
                case '_':
                    continue;
                case '.':
                    if (flag != 0)
                        unexpected(".");
                    flag = 1; // decimal
                default:
                    if (NUMBER.contains(c) || c == '.' || (flag == 2 && HEX.contains(c))) {
                        temp.append(c);
                        if (flag == 0 && temp.length() == 2) { // 075463754
                            if (temp.charAt(0) == '0' && temp.charAt(1) != '.')
                                flag = 4;
                        }
                    } else {
                        retract();
                        break o;
                    }
                    break;
                case 'X':
                case 'x':
                    if (flag == 0 && temp.length() == 1) {
                        temp.delete(0);
                        flag = 2; // hex
                    } else {
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
                            unexpected(String.valueOf(c));
                            break;

                    }
            }
        }

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
     * 封装合成"词"
     *
     * @param id 词类型
     */
    protected Word formClip(short id, CharSequence string) {
        return new Word(id, line, lineOffset, string.toString());
    }

    protected final Word eof() {
        return new Word(line, lineOffset);
    }

    @Override
    public String toString() {
        return "Lexer{" + "index=" + index + ", chars=" + this.input;
    }

    /**
     * 忽略 // 或 /* ... *\/ 注释
     */
    protected final Word ignoreStdNote() throws ParseException {
        if (hasNext()) {
            int c = next();
            switch (c) {
                case '/': {
                    int li = line;
                    while (hasNext()) {
                        next();
                        if(line != li)
                            break;
                    }
                }
                break;
                case '*': {
                    while (hasNext()) {
                        if (next() == '*') {
                            if (!hasNext()) {
                                return eof();
                            }

                            if (next() == '/') {
                                break;
                            }
                        }
                    }
                }
                break;
                default:
                    retract(2);
                    return readSpecial();
            }
        }

        if (!hasNext()) {
            return eof();
        }
        return null;
    }

    // section exception

    public final ParseException getExceptionDetails(ParseException e) {
        if (e.getCause() instanceof ParseException) return e;

        String line;
        if (this.input.length() != 0) {
            SimpleLineReader slr = new SimpleLineReader(this.input);

            try {
                line = slr.get(e.getLine());
            } catch (IndexOutOfBoundsException e2) { // how could it happen ?
                //return new ParseException("Near offset " + e.getTextOff(), e);
                e2.printStackTrace();
                line = "错误：行号超限！ LST: " + slr.get(slr.size() - 1);
            }
        } else {
            line = "";
        }
        return new ParseException(e.getTextOff(), e.getLine() + 1, e.getLineOffset(), line, e);
    }

    protected final void unexpected(String val) throws ParseException {
        throw err("未预料的'" + val + "'");
    }

    public final ParseException getDetails(String detail, Word word) {
        return getExceptionDetails(new ParseException(detail + " 在 " + word.val(), -2, word.getLine(), word.getLineOffset()));
    }

    public final ParseException atCurrentChar(String reason) {
        return getExceptionDetails(new ParseException(reason, this.index, this.line, this.lineOffset));
    }

    public final ParseException err(String reason) {
        return err(reason, null);
    }

    public final ParseException err(String reason, Throwable cause) {
        return getExceptionDetails(new ParseException(reason, this.index - 1, this.line, this.lineOffset - 1, cause));
    }
}
