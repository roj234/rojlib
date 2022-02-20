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
import roj.config.serial.Serializer;
import roj.config.serial.Serializers;
import roj.config.word.AbstLexer;
import roj.config.word.Word;
import roj.config.word.WordPresets;
import roj.config.word.Word_L;
import roj.io.IOUtil;
import roj.text.CharList;

import java.io.File;
import java.io.IOException;

import static roj.config.JSONParser.*;

/**
 * Yaml解析器
 * @author Roj234
 * @since 2021/7/7 2:03
 */
public class YAMLParser extends Parser {
    public static final String YAML_SPEC_CHARS    = "~+-[]{},;?<>&*:|!";
    // 是否需要escape (CString)
    public static final String YAML_ESCAPE_MULTI  = "[]{},>&*|!'\"\r\n\t";
    public static final String YAML_ESCAPE_SINGLE = "~-[]{},>&*|!'\"\r\n\t";
    static final short
            delim = 19, // -
            ask   = 20, // ?
            join  = 21, // <<
            anchor = 22,// &
            ref = 23,
            multiline = 24,
            multiline_clump = 25,
            multiline_keep = 26,
            multiline_trim = 27,
            force_cast = 28;

    private static final int TEXT_MODE = 1;

    public static void main(String[] args) throws ParseException, IOException {
        System.out.print(parse(IOUtil.readUTF(new File(args[0]))).toYAML());
    }

    public static CEntry parse(CharSequence string) throws ParseException {
        return parse(new YAMLLexer().init(string), 0);
    }

    /**
     * @param flag COMMENT NO_DUPLICATE_KEY LITERAL_KEY
     */
    public static CEntry parse(YAMLLexer wr, int flag) throws ParseException {
        CEntry ce = yamlRead(wr, (byte) (flag & (NO_DUPLICATE_KEY | LITERAL_KEY)), null);
        if (wr.nextWord().type() != WordPresets.EOF) {
            throw wr.err("期待 /EOF");
        }
        return ce;
    }

    /**
     * 解析流式数组定义
     */
    static CList yamlFlowArray(YAMLLexer wr, byte flag, Serializers ser) throws ParseException {
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
                    if (!more && (flag & LENINT_COMMA) == 0) {
                        unexpected(wr, w.val(), "逗号");
                    }
                    more = false;

                    try {
                        list.add(yamlRead(wr, flag, ser));
                    } catch (ParseException e) {
                        throw e.addPath("[" + list.size() + "]");
                    }
                    break;
            }
        }

        return list;
    }

    /**
     * 解析流式对象定义
     */
    @SuppressWarnings("fallthrough")
    static CMapping yamlFlowObject(YAMLLexer wr, byte flag, Serializers ser) throws ParseException {
        CMapping map = new CMapping();

        boolean more = false;

        o:
        while (true) {
            Word name = wr.nextWord();
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
                    if((flag & LITERAL_KEY) != 0)
                        break;
                default:
                    unexpected(wr, name.val(), "字符串");
            }

            String v = name.val();
            if((flag & NO_DUPLICATE_KEY) != 0 && map.containsKey(v))
                throw wr.err("重复的key: " + v);

            if (!more && (flag & LENINT_COMMA) == 0)
                unexpected(wr, name.val(), "逗号");
            more = false;

            Word w = wr.nextWord();
            if (w.type() != colon)
                unexpected(wr, w.val(), ":");

            try {
                map.put(v, yamlRead(wr, flag, ser));
            } catch (ParseException e) {
                throw e.addPath(v + '.');
            }
        }

        if (ser != null && map.containsKey("==", Type.STRING)) {
            Serializer<?> des = ser.find(map.getString("=="));
            if (des != null) {
                return new CObject<>(map.raw(), des);
            }
        }

        return map;
    }

    /**
     * 解析行式数组定义 <BR>
     *    - xx : yy
     *    - cmw :
     *       - xyz
     */
    static CList yamlLineArray(YAMLLexer wr, byte flag, Serializers ser) throws ParseException {
        CList list = new CList();

        int selfOff = wr.getLineOffset();

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
                wr.retractWord();
                break;
            }

            wr.nextWord();
            int off = wr.getLineOffset(wr.index);
            wr.retractWord();
            if(off < selfOff) {
                list.add(CNull.NULL);
            } else {
                wr.flag |= TEXT_MODE;
                try {
                    list.add(yamlRead(wr, flag, ser));
                } catch (ParseException e) {
                    throw e.addPath("[" + list.size() + "]");
                }
            }

            off = wr.getLineOffset();
            if (off < selfOff) {
                break;
            } else if(off != selfOff) {
                w = wr.nextWord();
                if(w.type() == WordPresets.EOF)
                    return list;
                wr.retractWord();
                throw wr.err("缩进有误: " + w);
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
    static CEntry yamlObject(YAMLLexer wr, byte flag, Serializers ser) throws ParseException {
        CMapping map = new CMapping();

        int selfOff = wr.getLineOffset();

        while (true) {
            Word name = wr.nextWord();
            String v = name.val();
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
                    // <<: *xxx
                    // 固定搭配
                    CEntry entry = wr.anchors.get(name.val());
                    if(entry == null)
                        throw wr.err("不存在的锚点 " + name.val());
                    if(!entry.getType().isSimilar(Type.MAP))
                        throw wr.err("锚点 " + name.val() + " 无法转换为map");
                    map.raw().putAll(entry.asMap().raw());
                    break;
                case WordPresets.LITERAL:
                case WordPresets.STRING:
                case WordPresets.INTEGER:
                case WordPresets.LONG:
                case WordPresets.DECIMAL_D:
                case NULL:
                    if((flag & NO_DUPLICATE_KEY) != 0 && map.containsKey(v))
                        throw wr.err("重复的key: " + v);

                    Word w = wr.nextWord();
                    if (w.type() != colon)
                        unexpected(wr, w.val(), ":");

                    wr.flag |= TEXT_MODE;
                    try {
                        map.put(v, yamlRead(wr, flag, ser));
                    } catch (ParseException e) {
                        throw e.addPath(v + '.');
                    }
                    break;
                case WordPresets.EOF:
                    return map;
                default:
                    unexpected(wr, name.val(), "字符串");
            }

            int off = wr.getLineOffset();
            if (off < selfOff) {
                break;
            } else if(off != selfOff) {
                Word w = wr.nextWord();
                if(w.type() == WordPresets.EOF)
                    return map;
                wr.retractWord();
                throw wr.err("缩进有误: " + w);
            }
        }

        if (ser != null && map.containsKey("==", Type.STRING)) {
            Serializer<?> des = ser.find(map.getString("=="));
            if (des != null) {
                return new CObject<>(map.raw(), des);
            }
        }

        return map;
    }

    private static CEntry yamlRead(YAMLLexer wr, byte flag, Serializers ser) throws ParseException {
        Word w = wr.nextWord();
        String cnt = w.val();
        switch (w.type()) {
            case force_cast: {
                if(cnt == null)
                    throw wr.err("!![null] 怎么会呢?");
                CEntry entry = yamlRead(wr, flag, ser);
                assert entry != null;
                switch (cnt) {
                    case "str":
                        return CString.valueOf(entry.asString());
                    case "float":
                        return CDouble.valueOf(entry.asDouble());
                    case "int":
                        return CInteger.valueOf(entry.asInteger());
                    case "bool":
                        return CBoolean.valueOf(entry.asBool());
                    case "map":
                        return entry.asMap();
                    case "set":
                        CMapping map = entry.asMap();
                        for (CEntry entry1 : map.values()) {
                            if(entry1 != CNull.NULL)
                                throw wr.err("无法转换为set: 值不是null: " + entry1.toShortJSON());
                        }
                        return map;
                    default:
                        throw wr.err("我不知道你要转换成啥, 支持 str float int bool map set: " + cnt);
                }
            }
            case left_m_bracket:
                return yamlFlowArray(wr, flag, ser);
            case left_l_bracket:
                return yamlFlowObject(wr, flag, ser);
            case WordPresets.STRING:
            case WordPresets.LITERAL: {
                boolean k = w.type() == WordPresets.LITERAL;
                int i = wr.lastWord;
                CEntry entry1 = isMappingKey(wr, flag, ser);
                if(entry1 != null) return entry1;
                if(k) {
                    int i1 = cnt.indexOf(':');
                    if (i1 > 0 && i1 < cnt.length() - 1 && wr.checkLine(i)) {
                        throw wr.err("[可能误判] 无效的map: 缺少空格");
                    }
                }

                return CString.valueOf(cnt);
            }
            case WordPresets.DECIMAL_D:
            case WordPresets.DECIMAL_F: {
                double number = w.number().asDouble();
                CEntry entry1 = isMappingKey(wr, flag, ser);
                return entry1 != null ? entry1 : CDouble.valueOf(number);
            }
            case WordPresets.INTEGER: {
                int number = w.number().asInt();
                CEntry entry1 = isMappingKey(wr, flag, ser);
                return entry1 != null ? entry1 : CInteger.valueOf(number);
            }
            case WordPresets.LONG:
                return w.getIndex() == -1 ? new CDate(w.number().asLong()) : w.getIndex() == -2 ? new CDatetime(w.number().asLong()) : CLong.valueOf(w.number().asLong());
            case TRUE:
            case FALSE: {
                boolean b = w.type() == TRUE;
                CEntry entry1 = isMappingKey(wr, flag, ser);
                return entry1 != null ? entry1 : CBoolean.valueOf(b);
            }
            case NULL:{
                CEntry entry1 = isMappingKey(wr, flag, ser);
                return entry1 != null ? entry1 : CNull.NULL;
            }
            case delim: {
                int i = wr.lastWord;
                if(wr.charAt(i - 1) == ':' && wr.lineNonEmpty(i)) {
                    throw wr.err("列表没有换行");
                }
                if(wr.checkLine(i)) {
                    wr.index = i;
                    return CNull.NULL;
                }
                wr.index = i;
                return yamlLineArray(wr, flag, ser);
            }
            case anchor: {
                CEntry val = yamlRead(wr, flag, ser);
                wr.anchors.put(cnt, val);
                return val;
            }
            case ref: {
                CEntry entry = wr.anchors.get(cnt);
                if(entry == null)
                    throw wr.err("不存在的锚点 " + cnt);
                return entry;
            }
            case multiline:      // shorten last \n to one
            case multiline_clump:// not keep \n between content line
            case multiline_keep: // keep all last \n
            case multiline_trim: // remove last \n
                return CString.valueOf(wr.readMultiLine(w.type()));
            default:
                unexpected(wr, cnt);
                return null;
        }
    }

    private static CEntry isMappingKey(YAMLLexer wr, byte flag, Serializers ser) throws ParseException {
        int i = wr.lastWord;
        if(wr.nextWord().type() == colon) {
            if(wr.getLineOffset(i) == wr.getLineOffset(wr.index) && i != 0 && wr.charAt(i - 1) == ':' && wr.lineNonEmpty(i)) {
                throw wr.err("映射没有换行");
            }
            wr.index = i;
            if(wr.checkLine(i)) {
                return CNull.NULL;
            }

            return yamlObject(wr, flag, ser);
        } else {
            wr.retractWord();
            return null;
        }
    }

    public YAMLParser() {}

    public YAMLParser(Serializers ser) {
        this.ser = ser;
    }

    @Override
    public CEntry Parse(CharSequence string, int flag) throws ParseException {
        YAMLLexer l = new YAMLLexer();
        l.init(string);
        l.flag = (byte) flag;

        CEntry ce = yamlRead(l, (byte) flag, ser);

        if ((flag & NO_EOF) == 0 && l.nextWord().type() != WordPresets.EOF) {
            throw l.err("期待 /EOF");
        }
        return ce;
    }

    @Override
    public String format() {
        return "YAML";
    }

    public static Builder<YAMLParser> builder() {
        return new Builder<>(new YAMLParser(new Serializers()));
    }

    public static class YAMLLexer extends AbstLexer {
        static final IBitSet SPECIAL = LongBitSet.from(YAML_SPEC_CHARS),
                             SPECIAL_0 = LongBitSet.from(YAML_ESCAPE_MULTI);

        byte flag;
        protected final MyHashMap<String, CEntry> anchors = new MyHashMap<>();

        @Override
        public YAMLLexer init(CharSequence seq) {
            anchors.clear();
            flag = 0;
            index = 0;
            input = seq;
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
                            break;
                        }
                    case '"':
                        this.index = i;
                        return formClip(WordPresets.STRING, readSlashString((char) c, c == '"'));
                    case '#': {
                        int s = i/*, e = s*/;
                        while (i < in.length()) { // 单行注释
                            c = in.charAt(i++);
                            if (c == '\r' || c == '\n') {
                                //e = i - 3;
                                if (c == '\r' && i < in.length() && in.charAt(i) == '\n')
                                    i++;
                                break;
                            }
                        }
                    }
                    break;
                    default: {
                        if (!WHITESPACE.contains(c)) {
                            this.index = --i;
                            if((flag & TEXT_MODE) != 0) {
                                flag &= ~TEXT_MODE;
                                Word w = null;
                                x:
                                if (SPECIAL.contains(c)) {
                                    switch (c) {
                                        case '~':
                                            if(in.length() > i && !WHITESPACE.contains(in.charAt(i + 1))) {
                                                break x;
                                            }
                                            break;
                                        case '-':
                                            if(in.length() > i && !WHITESPACE.contains(in.charAt(i + 1)) && !NUMBER.contains(in.charAt(i + 1))) {
                                                break x;
                                            }
                                        case '+':
                                            if (in.length() > i && NUMBER.contains(in.charAt(i + 1))) {
                                                w = readDigit(true);
                                                i = this.index;
                                                break x;
                                            }
                                            break;
                                        default:
                                            if(!SPECIAL_0.contains(c))
                                                break x;
                                    }
                                    return readSymbol();
                                } else if (NUMBER.contains(c)) {
                                    w = readDigit(false);
                                    i = this.index;
                                }
                                CharList temp = this.found;
                                temp.clear();

                                while (i < in.length()) {
                                    c = in.charAt(i++);
                                    if (c != '\r' && c != '\n') {
                                        if(c == ':' && i < in.length() && WHITESPACE.contains(in.charAt(i))) {
                                            i--;
                                            break;
                                        }
                                        temp.append((char) c);
                                    } else {
                                        i--;
                                        break;
                                    }
                                }

                                while (temp.length() > 0 && WHITESPACE.contains(temp.charAt(temp.length() - 1))) {
                                    temp.setIndex(temp.length() - 1);
                                    i--;
                                }

                                this.index = i;
                                if (temp.length() == 0) {
                                    if (w == null) {
                                        return c == ':' ? readSymbol() : eof();
                                    }
                                    return w;
                                }
                                if(w != null)
                                    temp.insert(0, w.val());

                                return checkSpec(temp);
                            } else {
                                if (SPECIAL.contains(c)) {
                                    switch (c) {
                                        case '-':
                                        case '+':
                                            if (in.length() > i && NUMBER.contains(in.charAt(i + 1))) {
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
                if ((!SPECIAL.contains(c) || c == '-') && c != '\r' && c != '\n') {
                    temp.append((char) c);
                } else {
                    i--;
                    break;
                }
            }

            while (WHITESPACE.contains(temp.charAt(temp.length() - 1))) {
                temp.setIndex(temp.length() - 1);
                i--;
            }

            this.index = i;

            if (temp.length() == 0) {
                return eof();
            }

            return checkSpec(temp);
        }

        private Word checkSpec(CharList temp) {
            String s = temp.toString();
            short id = WordPresets.LITERAL;
            if(s.length() >= 4 && s.length() <= 5) {
                switch (s) {
                    case "null":
                    case "Null":
                    case "NULL":
                        id = NULL;
                        break;
                    case "true":
                    case "True":
                    case "TRUE":
                        id = TRUE;
                        break;
                    case "false":
                    case "False":
                    case "FALSE":
                        id = FALSE;
                        break;
                }
            }

            return formClip(id, s);
        }

        private String readLiteral1() {
            final CharSequence in = this.input;
            int i = this.index;

            CharList temp = this.found;
            temp.clear();

            while (i < in.length()) {
                int c = in.charAt(i++);
                if (!WHITESPACE.contains(c) && !SPECIAL.contains(c)) {
                    temp.append((char) c);
                } else {
                    i--;
                    break;
                }
            }

            this.index = i;

            if (temp.length() == 0) {
                return null;
            }
            return temp.toString();
        }

        public String readMultiLine(short type) throws ParseException {
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

            int line = 0, j = i;
            while (j < in.length()) {
                c = in.charAt(j++);
                if (c != ' ') {
                    if ((c != '\r' && c != '\n') || line != 0) {
                        break;
                    }
                } else {
                    line++;
                }
            }
            j = 0;
            while (i < in.length()) {
                c = in.charAt(i++);
                if (c == '\n' || (c == '\r' && in.charAt(i++) == '\n')) {
                    // 多行字符串可以使用|(null)保留换行符，也可以使用>(clump)折叠换行
                    v.append(type == multiline_clump ? ' ' : '\n');
                    j = 0;
                } else {
                    if(j++ >= line)
                        v.append(c);
                    else if (c != ' ') {
                        i--;
                        break;
                    }
                }
            }

            if(v.length() == 0)
                throw err("多行表示后没有字符串");
            switch (type) {
                case multiline_clump:
                    v.set(v.length() - 1, '\n');
                    break;
                // +(keep)保留末尾的换行，-(trim)删除字末尾的换行
                case multiline: // 缩成一个
                case multiline_trim: // 全部去掉
                    while (v.length() > 0 && v.charAt(v.length() - 1) == '\n') {
                        v.setIndex(v.length() - 1);
                        i--;
                    }
                    if(v.length() == 0)
                        throw err("多行表示后没有字符串");
                    if(type == multiline)
                        v.append('\n');
                    break;
            }

            this.index = i;

            return v.toString();
        }

        @Override
        protected Word formNumberClip(byte flag, CharList temp, boolean negative) throws ParseException {
            if(temp.length() == 4) { // yyyyx
                Word_L ts = formRFCTime(false);
                if (ts != null) return ts;
            }
            return formClip((short) (WordPresets.INTEGER + flag), temp).number(this, negative);
        }

        @Override
        @SuppressWarnings("fallthrough")
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
                    v = readLiteral1();
                    id = anchor;
                    break;
                case '*':
                    v = readLiteral1();
                    id = ref;
                    break;
                case '?':
                    v = "?";
                    id = ask;
                    break;
                case '~':
                    v = "~";
                    id = NULL;
                    break;
                case '!':
                    // YAML 允许使用两个感叹号强制转换数据类型
                    if(hasNext() && next() == '!') {
                        v = readLiteral1();
                        id = force_cast;
                    } else {
                        throw err("无效字符 '!'");
                    }
                    break;
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

        public boolean checkLine(int prevOff) throws ParseException {
            int og = this.index;
            readWord();
            int len = this.index;
            this.index = og;

            int i = this.lineIdx;
            if(i >= og) {
                return false;
            }

            CharSequence in = this.input;
            while (i < len) {
                char c = in.charAt(i++);
                if (c == '\r' || c == '\n') {
                    this.lineIdx = len;
                    return prevOff == -1 || getLineOffset(len) == getLineOffset(prevOff);
                }
            }

            this.lineIdx = len;
            return false;
        }

        int lineIdx;

        char charAt(int i) {
            return input.charAt(i);
        }

        public int getLineOffset(int i) {
            CharSequence in = this.input;
            boolean flg = true;
            int count = 0;
            while (i > 0) {
                char c = in.charAt(--i);
                switch (c) {
                    case '\r':
                    case '\n':
                        return count;
                    case '-': // 要这行后面有东西才算数
                        if(flg) {
                            flg = false;
                            count = 0;
                        } else {
                            flg = true;
                            count++;
                        }
                        break;
                    case ' ':
                        count++;
                        break;
                    default:
                        flg = false;
                        count = 0;
                        break;
                }
            }
            return 0;
        }

        public int getLineOffset() throws ParseException {
            int off = this.index;
            readWord();
            int lineOffset = getLineOffset(this.index);
            this.index = off;
            return lineOffset;
        }

        public boolean lineNonEmpty(int i) {
            CharSequence in = this.input;
            while (i < in.length()) {
                char c = in.charAt(i++);
                switch (c) {
                    default:
                        return true;
                    case ' ':
                        break;
                    case '\r':
                    case '\n':
                        return false;
                }
            }
            return false;
        }
    }
}
