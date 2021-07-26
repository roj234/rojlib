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
package roj.config;

import roj.collect.IBitSet;
import roj.collect.LongBitSet;
import roj.collect.MyHashMap;
import roj.config.data.*;
import roj.config.word.Lexer;
import roj.config.word.Word;
import roj.config.word.WordPresets;
import roj.config.word.Word_L;
import roj.io.IOUtil;
import roj.math.MathUtils;
import roj.text.ACalendar;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.ByteReader;

import java.io.File;
import java.io.IOException;

import static roj.config.JSONParser.unexpected;

/**
 * Yaml解释器二代 <BR>
 *     !第一代只支持Set,Map和Scalars <BR>
 *     难道这不比SnakeYAML好么?
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/7/7 2:03
 */
public class YAMLParser {
    static final short
            TRUE = 10,
            FALSE = 11,
            NULL = 12,
            left_m_bracket = 13,
            right_m_bracket = 14,
            left_l_bracket = 15,
            right_l_bracket = 16,
            comma = 17, // ,
            colon = 18, // :
            delim = 19, // -
            ask   = 20, // ?
            join  = 21, // <<
            anchor = 22,// &
            ref = 23,
    multiline = 24,
    multiline_clump = 25,
    multiline_keep = 26,
    multiline_trim = 27;

    public static void main(String[] args) throws ParseException, IOException {
        CharList yaml = new CharList();
        ByteReader.decodeUTF(-1, yaml, new ByteList(IOUtil.readFile(new File(args[0]))));

        System.out.print("YML = " + parse(yaml).toYAML());
    }

    public static CMapping parse(CharSequence string) throws ParseException {
        return parse(new YAMLLexer().init(string), 0);
    }

    /**
     * @param flag <BR>
     *             1: 解析注释 <BR>
     *             2: 仁慈模式 <BR>
     *             4: 去除空格 <BR>
     *             8: 复k报错 <BR>
     */
    public static CMapping parse(YAMLLexer wr, int flag) throws ParseException {
        wr.flag = (short) flag;
        CMapping ce = yamlObject(wr, (byte) flag & 8);
        if (wr.hasNext()) {
            throw wr.err("期待 /EOF");
        }
        return ce;
    }

    /**
     * 解析流式数组定义
     */
    static CList yamlFlowArray(YAMLLexer wr, int flag) throws ParseException {
        CList list = new CList();

        boolean more = false;

        o:
        while (true) {
            Word w = wr.nextWord();
            switch (w.type()) {
                case right_m_bracket:
                    break o;
                case comma:
                    if (more) {
                        unexpected(wr, ",");
                    }
                    more = true;
                    break;
                default:
                    wr.retractWord();
                    more = false;
                    list.add(yamlRead(wr, flag));
                    break;
            }
        }

        return list;
    }

    /**
     * 解析流式对象定义
     */
    @SuppressWarnings("fallthrough")
    static CMapping yamlFlowObject(YAMLLexer wr, int flag) throws ParseException {
        CMapping map = new CMapping();

        boolean more = false;

        o:
        while (true) {
            Word name = wr.nextWord().copy();
            switch (name.type()) {
                case right_l_bracket:
                    break o;
                case comma:
                    if (more) {
                        unexpected(wr, ",");
                    }
                    more = true;
                    continue;
                case WordPresets.STRING:
                    break;
                case WordPresets.LITERAL:
                    if((flag & 2) != 0)
                        break;
                default:
                    unexpected(wr, name.val(), "字符串");
            }

            if((flag & 2) != 0 && map.containsKey(name.val()))
                throw wr.err("重复的key: " + name.val());

            more = false;

            Word w = wr.nextWord();
            if (w.type() != colon)
                unexpected(wr, w.val(), ":");

            map.put(name.val(), yamlRead(wr, flag));
        }

        if (map.containsKey("==", Type.STRING)) {
            ObjSerializer<?> deserializer = ObjSerializer.REGISTRY.get(map.getString("=="));
            if (deserializer != null) {
                return new CObject<>(map, deserializer);
            }/* else {
                // warning
            }*/
        }

        return map;
    }

    /**
     * 解析行式数组定义 <BR>
     *    - xx : yy
     *    - cmw :
     *       - xyz
     */
    static CList yamlLineArray(YAMLLexer wr, int flag) throws ParseException {
        CList list = new CList();

        wr.checkLine();
        int selfOff = wr.off;

        while (true) {
            Word w = wr.nextWord();
            if(w.type() != delim) {
                /**
                 * abc:
                 * - XXX
                 * - YYY
                 *
                 * def: ...
                 */
                //if(wr.line == 0) {
                    wr.retractWord();
                    break;
                //}
                //unexpected(wr, w.val(), "-");
            }

            list.add(yamlRead(wr, flag | 4));

            String v = wr.checkLine().val();
            if (wr.off < selfOff) {
                break;
            } else if(wr.off != selfOff || wr.line == wr.lastLine) {
                unexpected(wr, v, "\\n");
            }
        }

        return list;
    }

    /**
     * 解析对象定义 <BR>
     * a : r \r\n
     * c : x
     */
    @SuppressWarnings("fallthrough")
    static CMapping yamlObject(YAMLLexer wr, int flag) throws ParseException {
        CMapping map = new CMapping();

        wr.checkLine();
        int selfOff = wr.off;

        while (true) {
            Word name = wr.nextWord().copy();
            switch (name.type()) {
                case ask:
                    throw wr.err("并不支持非字符串的key, 也不打算支持");
                case join:
                    name = wr.nextWord();
                    if (name.type() != colon)
                        unexpected(wr, name.val(), ":");
                    name = wr.nextWord();
                    if (name.type() != ref)
                        unexpected(wr, name.val(), "*");
                    name = wr.nextWord();
                    if (name.type() != WordPresets.LITERAL && name.type() != WordPresets.STRING)
                        unexpected(wr, name.val(), "字符串");
                    // <<: *xxx
                    // 固定搭配
                    CEntry entry = wr.anchors.get(name.val());
                    if(entry == null)
                        throw wr.err("不存在的锚点 " + name.val());
                    map.raw().putAll(entry.asMap().raw());
                    //wr.checkLine();

                    break;
                case WordPresets.LITERAL:
                case WordPresets.STRING:
                    if((flag & 8) != 0 && map.containsKey(name.val()))
                        throw wr.err("重复的key: " + name.val());

                    Word w = wr.nextWord();
                    if (w.type() != colon)
                        unexpected(wr, w.val(), ":");

                    map.put(name.val(), yamlRead(wr, flag | 3));
                    break;
                case WordPresets.EOF:
                    if((flag & 1) == 0)
                        return map;
                default:
                    unexpected(wr, name.val(), "字符串");
            }

            String v = wr.checkLine().val();
            if (wr.off < selfOff) {
                break;
            } else if(wr.off != selfOff || wr.line == wr.lastLine) {
                if(!wr.hasNext() && (flag & 1) == 0)
                    return map;
                System.out.println(wr.off);
                System.out.println(selfOff);
                System.out.println(wr.line);
                System.out.println(wr.lastLine);
                unexpected(wr, v, "\\n");
            }
        }

        if (map.containsKey("==", Type.STRING)) {
            ObjSerializer<?> ser = ObjSerializer.REGISTRY.get(map.getString("=="));
            if (ser != null) {
                return new CObject<>(map, ser);
            }/* else {
                // warning
            }*/
        }

        return map;
    }

    // flag 2: is map
    // flag 4: is list
    private static CEntry yamlRead(YAMLLexer wr, int flag) throws ParseException {
        Word w = wr.nextWord();
        String cnt = w.val();
        switch (w.type()) {
            case WordPresets.COMMENT:
                return new CComment(cnt);
            case left_m_bracket:
                return yamlFlowArray(wr, flag);
            case left_l_bracket:
                return yamlFlowObject(wr, flag);
            case WordPresets.STRING:
            case WordPresets.LITERAL: {
                int i = wr.lastWord;
                if(wr.nextWord().type() == colon) {
                    w = wr.checkLineOnce();
                    if(w.getIndex() == wr.off) {
                        System.out.println("SameLevelInterrupt");
                        return null;
                    }
                    wr.index = i;
                    // todo
                    System.out.println("NextLevelOfMapCheck");
                    return yamlObject(wr, flag);
                } else {
                    wr.retractWord();
                    return CString.valueOf(cnt);
                }
            }
            case WordPresets.DECIMAL_D:
            case WordPresets.DECIMAL_F:
                return CDouble.valueOf(w.number().asDouble());
            case WordPresets.INTEGER:
                return CInteger.valueOf(w.number().asInt());
            case WordPresets.LONG:
                return w.getIndex() == -1 ? new CYamlDate(w.number().asLong()) : w.getIndex() == -2 ? new CYamlTimestamp(w.number().asLong()) : CLong.valueOf(w.number().asLong());
            case TRUE:
            case FALSE:
                return CBoolean.valueOf(w.type() == TRUE);
            case NULL:
                return CNull.NULL;
            case delim: {
                int i = wr.lastWord;

                wr.checkLine();

                wr.index = i;
                if (wr.line > wr.lastLine) {
                    return yamlLineArray(wr, flag);
                }
                unexpected(wr, cnt);
                return null;
            }
            // only for YamlList
            case anchor: {
                int i = wr.lastWord;
                w = wr.nextWord();
                if (w.type() != WordPresets.LITERAL && w.type() != WordPresets.STRING)
                    unexpected(wr, w.val(), "字符串");
                switch (wr.charAt(i - 1)) {
                    case '-':
                        // list entry
                        String v = w.val();
                        CEntry val = yamlRead(wr, flag);
                        wr.anchors.put(v, val);
                        //System.out.println("CreateListAnchor '" + v + "' : " + val);
                        return val;
                    case ':':
                        // list or map itself
                        // todo
                        System.out.println("ListOrMapItself " + w.val());
                        wr.state = w.val();
                        return null;
                    default:
                        throw wr.err("未预料的情况! 如果你知道这符合语法, 请报告给作者 c=" + wr.charAt(i - 1));
                }
            }
            case ref: {
                w = wr.nextWord();
                if (w.type() != WordPresets.LITERAL && w.type() != WordPresets.STRING)
                    unexpected(wr, w.val(), "字符串");
                // <<: *xxx
                // 固定搭配
                CEntry entry = wr.anchors.get(w.val());
                if(entry == null)
                    throw wr.err("不存在的锚点 " + w.val());
                return entry;
            }
            case multiline:      // shorten last \n to one
            case multiline_clump:// not keep \n between content line
            case multiline_keep: // keep all last \n
            case multiline_trim: // remove last \n
                return CString.valueOf(wr.readMultiLine(w.type()).val());
            default:
                unexpected(wr, cnt);
                return null;
        }
    }

    private static final class YAMLLexer extends Lexer {
        static final IBitSet SPECIAL = LongBitSet.from("+-\\/*()!~`#^&,<>.?\"':;|[]"),
                                DATE1 = LongBitSet.from("-Tt\r\n"),
                                DATE2 = LongBitSet.from(":.\r\n");

        static final int COMMENT = 1, LENIENT = 2, TRIM_BEGIN_SPACE = 4;

        short flag;
        Object state;
        int[] dateBuffer = new int[3];
        MyHashMap<String, CEntry> anchors = new MyHashMap<>();

        @Override
        public YAMLLexer init(CharSequence s) {
            anchors.clear();
            flag = 0;
            index = 0;
            input = s;
            return this;
        }

        @Override
        @SuppressWarnings("fallthrough")
        public Word readWord() throws ParseException {
            int i = this.index;
            CharSequence in = this.input;
            while (i < in.length()) {
                int c = in.charAt(i++);
                switch (c) {
                    case '\'':
                        if(i + 1 < in.length() && in.charAt(i) == '\'' && in.charAt(i + 1) == '\'') {
                            // 多行注释
                            i += 2;
                            int s = i;
                            while (i + 2 < in.length()) {
                                if(in.charAt(i++) == '\'' && in.charAt(i++) == '\'' && in.charAt(i++) == '\'') {
                                    break;
                                }
                            }

                            if((flag & COMMENT) != 0) {
                                this.index = i;
                                return formClip(WordPresets.COMMENT, in.subSequence(s, i - 3));
                            }

                            break;
                        }
                    case '"':
                        this.index = i;
                        return formClip(WordPresets.STRING, readSlashString('"', false));
                    case '#': {
                        int s = i, e = s;
                        while (i < in.length()) { // 单行注释
                            c = in.charAt(i++);
                            if (c == '\r' || c == '\n') {
                                e = i - 3;
                                if (c == '\r' && i < in.length() && in.charAt(i) == '\n')
                                    i++;
                                break;
                            }
                        }

                        if ((flag & COMMENT) != 0) {
                            this.index = i;
                            return formClip(WordPresets.COMMENT, in.subSequence(s, e));
                        }
                    }
                    break;
                    default: {
                        if (!WHITESPACE.contains(c)) {
                            this.index = --i;
                            if (SPECIAL.contains(c)) {
                                switch (c) {
                                    case '-':
                                    case '+':
                                        if(in.length() > i && NUMBER.contains(in.charAt(i))) {
                                            return readDigit(true);
                                        }
                                        break;
                                }
                                return readSymbol();
                            } else if (NUMBER.contains(c)) {
                                return readDigit(false);
                            } else {
                                return readLiteral();
                            }
                        }
                    }
                }
            }
            this.index = i;
            return eof();
        }

        /**
         * @return 标识符 or 变量
         */
        protected Word readLiteral() {
            final CharSequence in = this.input;
            int i = this.index;

            CharList temp = this.found;
            temp.clear();

            while (i < in.length()) {
                int c = in.charAt(i++);
                if (/*(!SPECIAL.contains(c) || c == '-') && */!WHITESPACE.contains(c)) {
                    temp.append((char) c);
                } else {
                    i--;
                    break;
                }
            }

            while (SPECIAL.contains(temp.charAt(temp.length() - 1))) {
                temp.setIndex(temp.length() - 1);
                i--;
            }

            this.index = i;

            if (temp.length() == 0) {
                return eof();
            }

            String s = temp.toString();
            short id = WordPresets.LITERAL;
            if(s.length() > 1 && s.length() <= 5) {
                if((flag & LENIENT) != 0) {
                    switch (s) {
                        case "null":
                        case "Null":
                        case "NULL":
                            id = NULL;
                            break;
                        case "true":
                        case "yes":
                        case "on":
                        case "True":
                        case "Yes":
                        case "On":
                        case "TRUE":
                        case "YES":
                        case "ON":
                            id = TRUE;
                            break;
                        case "false":
                        case "no":
                        case "off":
                        case "False":
                        case "No":
                        case "Off":
                        case "FALSE":
                        case "NO":
                        case "OFF":
                            id = FALSE;
                            break;
                    }
                } else {
                    switch (s) {
                        case "true":
                        case "yes":
                        case "on":
                            id = TRUE;
                            break;
                        case "false":
                        case "no":
                        case "off":
                            id = FALSE;
                            break;
                    }
                }
            }

            return formClip(id, s);
        }

        public Word readMultiLine(short type) throws ParseException {
            CharSequence in = this.input;
            int i = this.index;

            if(remain() < 3)
                throw err("EOF");

            char c = in.charAt(i++);
            if (c != '\n' && (c != '\r' || in.charAt(i++) != '\n')) {
                throw err("标识符后没有找到换行符");
            }

            CharList v = this.found;
            v.clear();

            while (i < in.length()) {
                c = in.charAt(i++);
                if(c != ' ') {
                    break;
                }
                // trim begin ' '
                if((flag & TRIM_BEGIN_SPACE) != 0)
                    while (i < in.length() && in.charAt(i) == ' ')
                        i++;
                while (i < in.length()) {
                    c = in.charAt(i++);
                    if (c == '\n' || (c == '\r' && in.charAt(i++) == '\n')) {
                        // 多行字符串可以使用|(null)保留换行符，也可以使用>(clump)折叠换行
                        v.append(type == multiline_clump ? ' ' : '\n');
                        break;
                    }
                    v.append(c);
                }
            }

            if(v.length() == 0)
                throw err("多行表示后没有字符串");
            switch (type) {
                case multiline_clump:
                    // todo 这里空行的空格要去了么
                    v.set(v.length() - 1, '\n');
                    break;
                // +(keep)保留末尾的换行，-(trim)删除字末尾的换行
                case multiline: // 缩成一个
                case multiline_trim: // 全部去掉
                    while (v.length() > 0 && v.charAt(v.length() - 1) == '\n')
                        v.setIndex(v.length() - 1);
                    if(v.length() == 0)
                        throw err("多行表示后没有字符串");
                    if(type == multiline)
                        v.append('\n');
                    break;
            }

            this.index = i - 1;

            return formClip(WordPresets.STRING, v);
        }

        @Override
        protected Word formNumberClip(byte flag, CharList temp, boolean negative) throws ParseException {
            if(temp.length() == 4) { // yyyy
                // check timestamp
                CharSequence val = input;
                int[] buf = dateBuffer;
                int k = 0, j = index - 4, off = j;
                int end = j + 30; // "0000-00-00Z\t00:00:00.000+00:00".length()
                while (j < end) {
                    char c0 = val.charAt(j++);
                    if(DATE1.contains(c0)) {
                        try {
                            buf[k++] = MathUtils.parseInt(val, off, j - 1, 10);
                        } catch (NumberFormatException e) {
                            throw err("[YMLTS] not a number: " + val.subSequence(off, j - 1), index = j);
                        }
                        if(k == 3) {
                            if(buf[1] > 12)
                                throw err("[YMLTS] month > 12: " + buf[1], index = j);
                            if(buf[2] > 31)
                                throw err("[YMLTS] day > 31: " + buf[2], index = j);

                            long ts = ACalendar.daySinceAD(buf[0] - 1969, buf[1], buf[2], null) * 86400000;
                            if (c0 == 'T' || c0 == 't') {
                                if(val.charAt(++j) != '\t')
                                    j--;
                                off = j;
                                k = 0;
                                while (j < end) {
                                    char c1 = val.charAt(j++);
                                    if(DATE2.contains(c1)) {
                                        try {
                                            buf[k++] = MathUtils.parseInt(val, off, j - 1, 10);
                                        } catch (NumberFormatException e) {
                                            throw err("[YMLTS] not a number: " + val.subSequence(off, j - 1), index = j);
                                        }
                                        if(k == 3) {
                                            if(buf[0] > 23)
                                                throw err("[YMLTS] hour > 23: " + buf[0], index = j);
                                            if(buf[1] > 59)
                                                throw err("[YMLTS] minute > 59: " + buf[1], index = j);
                                            if(buf[2] > 59)
                                                throw err("[YMLTS] second > 59: " + buf[2], index = j);
                                            ts += buf[0] * 3600000 + buf[1] * 60000 + buf[2] * 1000;

                                            if (c1 == '.') {
                                                off = j;
                                                while (NUMBER.contains(val.charAt(j))) {
                                                    j++;
                                                }

                                                int ms;
                                                try {
                                                    ms = MathUtils.parseInt(val, off, j, 10);
                                                } catch (NumberFormatException e) {
                                                    throw err("[YMLTS] not a number: " + val.subSequence(off, j - 1), index = j);
                                                }
                                                if(ms > 999)
                                                    throw err("[YMLTS] millisecond > 999", index = j);
                                                ts += ms;

                                                c1 = val.charAt(j);
                                                if(c1 == 'Z' || c1 == 'z') {
                                                    index = ++j;
                                                    // 2021-7-8[Tt](\t)1:49:23.111Z
                                                    return new Word_L(-2, ts);
                                                } else if(c1 == '+' || c1 == '-') {
                                                    off = ++j;
                                                    k = 0;
                                                    while (j < end) {
                                                        char c2 = val.charAt(j++);
                                                        if(c2 == ':') {
                                                            try {
                                                                buf[k++] = MathUtils.parseInt(val, off, j - 1, 10);
                                                            } catch (NumberFormatException e) {
                                                                throw err("[YMLTS] not a number: " + val.subSequence(off, j - 1), index = j);
                                                            }
                                                            if(k == 2) {
                                                                // 2021-7-8[Tt](\t)1:49:23.111[+-]xx
                                                                index = j;
                                                                if(buf[0] > 23)
                                                                    throw err("[YMLTS] dHour > 23: " + buf[0], j);
                                                                if(buf[1] > 59)
                                                                    throw err("[YMLTS] dMinute > 59: " + buf[1], j);
                                                                j = buf[0] * 3600000 + buf[1] * 60000;
                                                                return new Word_L(-2,  c1 == '+' ? ts + j : ts - j);
                                                            }
                                                            off = j;
                                                        } else if(WHITESPACE.contains(c2)) {
                                                            index = j;
                                                            if(k == 1) {
                                                                if(buf[0] > 23)
                                                                    throw err("[YMLTS] dHour > 23: " + buf[0], j);
                                                                // 2021-7-8[Tt](\t)1:49:23.111[+-]xx:xx
                                                                return new Word_L(-2, c1 == '+' ? ts + buf[0] * 3600000 : ts - buf[0] * 3600000);
                                                            } else {
                                                                throw err("[YMLTS] unexpected end of YMLTimestamp", j);
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    throw err("[YMLTS] missing last 'Z' in YMLTimestamp", index = j);
                                                }
                                            } else {
                                                // 2021-7-8[Tt](\t)1:49:23
                                                index = j;
                                                return new Word_L(-2, ts);
                                            }
                                        }
                                        off = j;
                                    }
                                }
                            } else {
                                // 2021-7-8
                                index = j;
                                return new Word_L(-1, ts);
                            }
                        }
                        off = j;
                    }
                }

                if(val.length() <= j) {
                    // Unexpected EOF
                    index = j;
                    throw err("[YMLTS] 在日期解析结束前遇到了EOF");
                }
            }
            return formClip((short) (WordPresets.INTEGER + flag), temp).number(negative);
        }

        @Override
        protected Word readSymbol() throws ParseException {
            String v;
            short id;
            switch (next()) {
                case '[':
                    v = "[";
                    id = left_m_bracket;
                    break;
                case ']':
                    v = "]";
                    id = right_m_bracket;
                    break;
                case ':':
                    v = ":";
                    id = colon;
                    break;
                case ',':
                    v = ",";
                    id = comma;
                    break;
                case '-':
                    v = "-";
                    id = delim;
                    break;
                case '{':
                    v = "{";
                    id = left_l_bracket;
                    break;
                case '}':
                    v = "}";
                    id = right_l_bracket;
                    break;
                case '&':
                    v = "&";
                    id = anchor;
                    break;
                case '*':
                    v = "*";
                    id = ref;
                    break;
                case '?':
                    v = "?";
                    id = ask;
                    break;
                case '~':
                    //if((flag & LENIENT) == 0) {
                    //    throw err("无效字符 '~' (开启lenientNull模式)");
                    //}
                    v = "~";
                    id = NULL;
                    break;
                case '!':
                    // YAML 允许使用两个感叹号强制转换数据类型
                    if(hasNext() && next() == '!') {
                        if((flag & LENIENT) == 0) {
                            throw err("嗯？这不是人写的么？机器写的？ \n" +
                                    "请使用那个啥来着，长的像两个短竖线的...\n" +
                                    "哦，双引号啊.. 或者啊，你也可以打开LENIENT模式");
                        } else {
                            String type = readLiteral().val();
                            Word w = readWord();
                            switch (type) {
                                case "str":
                                    w.reset(WordPresets.LITERAL, w.getIndex(), w.val());
                                    return w;
                            }
                        }
                    }
                    throw err("无效字符 '!'");
                case '<':
                    if(hasNext() && next() == '<') {
                        v = "<<";
                        id = join;
                        break;
                    }
                    throw err("无效字符 '<'");
                case '>':
                    v = ">";
                    id = multiline_clump;
                    break;
                    /**
                     * 字符串可以写成多行，从第二行开始，必须有一个单空格缩进。换行符会被转为空格。
                     * 多行字符串可以使用|保留换行符，也可以使用>折叠换行。
                     * +表示保留文字块末尾的换行，-表示删除字符串末尾的换行。*/
                case '|':
                    v = "|";
                    id = multiline;
                    if(hasNext()) {
                        switch (next()) {
                            case '+':
                                v = "|+";
                                id = multiline_keep;
                                break;
                            case '-':
                                v = "|-";
                                id = multiline_trim;
                                break;
                            default:
                                retract();
                                break;
                        }
                        break;
                    }
                default:
                    throw err("无效字符 '" + offset(-1) + '\'');
            }

            return formClip(id, v);
        }

        /**
         * 回收利用Word对象
         */
        protected Word formClip(short id, CharSequence s) {
            if(cached == null) {
                cached = new Word();
            }
            return cached.reset(id, index, s.toString());
        }

        public Word checkLine() throws ParseException {
            int og = this.index;
            cached = nextWord();
            int len = this.index;
            this.index = og;

            int i = this.lineIdx;
            if(i >= len) return cached;

            CharSequence in = this.input;

            this.lastLine = this.line;
            int off = this.off;
            boolean space = true;
            while (i < len) {
                char c = in.charAt(i++);
                if (c == '\r' || c == '\n') {
                    if (c == '\r' && i < len && in.charAt(i) == '\n')
                        i++;
                    line++;
                    off = 0;
                    space = true;
                } else {
                    if(c == ' ') {
                        if(space)
                            off++;
                    } else {
                        space = false;
                    }
                }
            }

            this.off = off;

            this.lineIdx = this.index;

            return cached;
        }

        public Word checkLineOnce() throws ParseException {
            int og = this.index;
            cached = nextWord();
            int len = this.index;
            this.index = og;

            int i = this.lineIdx;
            if(i >= len) return cached;

            CharSequence in = this.input;

            int off = this.off;
            int line = this.line;

            boolean space = true;
            while (i < len) {
                char c = in.charAt(i++);
                if (c == '\r' || c == '\n') {
                    if (c == '\r' && i < len && in.charAt(i) == '\n')
                        i++;
                    line++;
                    off = 0;
                    space = true;
                } else {
                    if(c == ' ') {
                        if(space)
                            off++;
                    } else {
                        space = false;
                    }
                }
            }

            return cached.reset(line, off, cached.val());
        }

        int lineIdx;
        int line = 1, lastLine;
        int off;

        char charAt(int i) {
            return input.charAt(i);
        }
    }
}
