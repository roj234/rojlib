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
import roj.config.word.*;
import roj.io.IOUtil;
import roj.text.CharList;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static roj.config.JSONParser.*;

/**
 * 汤小明的小巧明晰语言 <br>
 *     注释，八进制需要使用java的表示法: 0[oct...]
 * @author Roj234
 * @since 2022/1/6 19:49
 */
public class TOMLParser implements Parser {
    public static final int LENIENT = 1, INLINE = 2;
    static final short eq = 18, dot = 19, dlmb = 20, drmb = 21;

    public static void main(String[] args) throws ParseException, IOException {
        System.out.print(parse(IOUtil.readUTF(new File(args[0]))).toTOML());
    }

    public static CMapping parse(CharSequence string) throws ParseException {
        return parse((TOMLLexer) new TOMLLexer().init(string), 0);
    }

    public static CMapping parse(CharSequence string, int flag) throws ParseException {
        return parse((TOMLLexer) new TOMLLexer().init(string), flag);
    }

    @Override
    public CEntry Parse(CharSequence string, int flag) throws ParseException {
        return parse(string, flag);
    }

    @Override
    public String format() {
        return "TOML";
    }

    /**
     * @param flag LENIENT 允许修改行内表
     */
    public static CMapping parse(TOMLLexer wr, int flag) throws ParseException {
        CMapping map = new CMapping(new MyHashMap<>());
        CMapping root = tomlObject(wr, flag);
        if (root.size() > 0) map.put("<root>", root);

        o:
        while (wr.hasNext()) {
            Word w = wr.nextWord();
            switch (w.type()) {
                case left_m_bracket:
                    w = wr.nextWord();
                    if (w.type() != WordPresets.LITERAL && w.type() != WordPresets.STRING)
                        unexpected(wr, w.val(), "键");
                    wr.dotName(w.val(), map, flag);
                    w = wr.nextWord();
                    if (w.type() != right_m_bracket)
                        unexpected(wr, w.val(), "]");
                    put(wr, flag | INLINE);
                    break;
                case dlmb:
                    w = wr.nextWord();
                    if (w.type() != WordPresets.LITERAL && w.type() != WordPresets.STRING)
                        unexpected(wr, w.val(), "键");
                    wr.dotName(w.val(), map, flag);
                    w = wr.nextWord();
                    if (w.type() != drmb)
                        unexpected(wr, w.val(), "]");
                    wr.m.getOrCreateList(wr.k).add(tomlObject(wr, flag));
                    break;
                case WordPresets.EOF:
                    break o;
                default:
                    unexpected(wr, w.val(), "[ 或 [[");
            }
        }

        if (map.containsKey("==", Type.STRING)) {
            Serializer<?> des = Serializers.find(map.getString("=="));
            if (des != null) {
                return new CObject<>(map.raw(), des);
            }
        }

        return map;
    }

    /**
     * 解析数组定义 <BR>
     * [xxx, yyy, zzz] or []
     */
    private static CList tomlFlowArray(TOMLLexer wr, int flag) throws ParseException {
        CTOMLFxList list = new CTOMLFxList();
        list.fixed = true;

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
                    list.add(tomlRead(wr, flag));
                    break;
            }
        }

        return list;
    }

    /**
     * 解析对象定义 <BR>
     * {xxx = yyy, zzz = uuu}
     */
    @SuppressWarnings("fallthrough")
    private static CMapping tomlObject(TOMLLexer wr, int flag) throws ParseException {
        CMapping map = (flag & INLINE) != 0 ? new CTOMLFxMap() : new CMapping(new MyHashMap<>());

        boolean more = false;

        o:
        while (true) {
            wr.noNumberCheck = true;
            Word w = wr.nextWord();
            wr.noNumberCheck = false;
            switch (w.type()) {
                case right_l_bracket:
                    break o;
                case comma:
                    if ((flag & INLINE) == 0) {
                        unexpected(wr, w.val(), "键");
                    }
                    if (more) {
                        unexpected(wr, ",");
                    }
                    more = true;
                    continue;
                case WordPresets.DECIMAL_D:
                case WordPresets.DECIMAL_F:
                    throw wr.err("别闹");
                case WordPresets.INTEGER:
                case WordPresets.LITERAL:
                case WordPresets.STRING:
                    break;
                default:
                    if ((flag & INLINE) == 0) {
                        wr.retractWord();
                        break o;
                    }
                    unexpected(wr, w.val(), "字符串");
            }

            wr.dotName(w.val(), map, flag);

            more = false;

            w = wr.nextWord();
            if (w.type() != eq) unexpected(wr, w.val(), "=");

            put(wr, flag & ~INLINE);
            if ((flag & (INLINE | LENIENT)) == 0) wr.ensureLineEnd();
        }

        if (map.containsKey("==", Type.STRING)) {
            Serializer<?> des = Serializers.find(map.getString("=="));
            if (des != null) {
                return new CObject<>(map.raw(), des);
            }
        }

        return map;
    }

    private static void put(TOMLLexer wr, int flag) throws ParseException {
        CEntry entry;
        CEntry me = wr.m.raw().putIfAbsent(wr.k, entry = ((flag & INLINE) != 0) ? tomlObject(wr, flag & ~INLINE) : tomlRead(wr, flag));
        if (me != null) {
            if (entry.getType() != me.getType())
                throw wr.err("覆盖已存在的 " + me.toShortJSONb());
            if (me.getType() != Type.MAP)
                throw wr.err("这不是表");
            me.asMap().merge(entry.asMap(), false, true);
        }
    }

    private static CEntry tomlRead(TOMLLexer wr, int flag) throws ParseException {
        Word w = wr.nextWord();
        switch (w.type()) {
            case left_l_bracket:
                return tomlObject(wr, flag | INLINE);
            case left_m_bracket:
                return tomlFlowArray(wr, flag);
            case WordPresets.STRING:
            case WordPresets.LITERAL: {
                int i = wr.lastWord;
                if(wr.nextWord().type() == eq) {
                    wr.index = i;
                    return tomlObject(wr, flag);
                } else {
                    wr.retractWord();
                    return CString.valueOf(w.val());
                }
            }
            case WordPresets.RFCDATE_DATE:
                return new CDate(w.number().asLong());
            case WordPresets.RFCDATE_DATETIME:
            case WordPresets.RFCDATE_DATETIME_TZ:
                return new CDatetime(w.number().asLong());
            case WordPresets.DECIMAL_D:
            case WordPresets.DECIMAL_F:
                return CDouble.valueOf(w.number().asDouble());
            case WordPresets.INTEGER:
                return CInteger.valueOf(w.number().asInt());
            case WordPresets.LONG:
                return CLong.valueOf(w.number().asLong());
            case TRUE:
            case FALSE:
                return CBoolean.valueOf(w.type() == TRUE);
            case NULL:
                return CNull.NULL;
            default:
                unexpected(wr, w.val());
                return null;
        }
    }

    static class TOMLLexer extends AbstLexer {
        static final IBitSet LITERAL_SPECIAL = LongBitSet.from("+-#.=\"'[]{} \n\r\t");

        boolean noNumberCheck;

        @Override
        public Word readWord() throws ParseException {
            int i = this.index;
            CharSequence in = this.input;
            while (i < in.length()) {
                char c = in.charAt(i++);
                switch (c) {
                    case '\'':
                    case '"':
                        if (in.length() - i > 2 && in.charAt(i) == c && in.charAt(i+1) == c) {
                            this.index = i + 2;
                            return formClip(WordPresets.STRING, readMultiLine(c));
                        } else {
                            this.index = i;
                            return formClip(WordPresets.STRING, readSlashString(c, c == '\"'));
                        }
                    case '#': {
                        int s = i;
                        while (i < in.length()) {
                            c = in.charAt(i++);
                            if (c == '\r' || c == '\n') {
                                if (c == '\r' && i < in.length() && in.charAt(i) == '\n')
                                    i++;
                                break;
                            }
                        }
                    }
                    break;
                    default: {
                        if (!WHITESPACE.contains(c)) {
                            this.index = i - 1;
                            if (SPECIAL.contains(c)) {
                                switch (c) {
                                    case '-':
                                    case '+':
                                        if(in.length() > i && NUMBER.contains(in.charAt(i))) {
                                            return readDigit(true);
                                        }
                                        if(in.length() > i + 2) {
                                            char c1 = in.charAt(i);
                                            if (c1 == 'n' && in.charAt(i+1) == 'a' && in.charAt(i+2) == 'n') {
                                                this.index = i+3;
                                                // java中NAN无符号
                                                return new Word_D(WordPresets.DECIMAL_D, i, Double.NaN, "nan");
                                            }
                                            if (c1 == 'i' && in.charAt(i+1) == 'n' && in.charAt(i+2) == 'f') {
                                                this.index = i+3;
                                                return new Word_D(WordPresets.DECIMAL_D, i, c == '+' ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY, "inf");
                                            }
                                        }
                                        break;
                                }
                                return readSymbol();
                            } else if (!noNumberCheck && NUMBER.contains(c)) {
                                if (in.length() - i > 5 && in.charAt(i+3) == '-') {
                                    Word_L w = formRFCTime();
                                    if (w != null) return w;
                                } else if (in.length() - i > 3 && in.charAt(i+1) == ':') {
                                    Word_L w = formRFCTime();
                                    if (w != null) return w;
                                }
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

        public String readMultiLine(char end3) throws ParseException {
            CharSequence in = this.input;
            int i = this.index;

            if(remain() < 3) throw err("EOF");

            CharList v = this.found;
            v.clear();

            int end3Amount = 0;
            boolean slash = false;
            boolean quoted = true;
            boolean skip = false;

            while (i < in.length()) {
                char c = in.charAt(i);
                if (c != '\r' && c != '\n') break;
                i++;
            }
            while (i < in.length()) {
                char c = in.charAt(i++);
                if (slash) {
                    i = slashHandler(input, c, v, i, end3);
                    slash = false;
                    end3Amount = 0;
                } else {
                    if(end3 == c) {
                        ++end3Amount;
                        v.append(c);
                    } else {
                        if (end3Amount >= 3) {
                            quoted = false;
                            v.setIndex(v.length() - 3);
                            break;
                        }
                        end3Amount = 0;
                        if(c == '\\' && end3 == '"') {
                            if (in.charAt(i) == '\r' || in.charAt(i) == '\n') {
                                skip = true;
                            } else {
                                slash = true;
                            }
                        } else if (!skip || !WHITESPACE.contains(c)) {
                            skip = false;
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
                throw err("未终止的 QUOTE (" + end3 + end3 + end3 + ")", orig);
            }

            return v.toString();
        }

        @Override
        protected Word formNumberClip(byte flag, CharList temp, boolean negative) throws ParseException {
            return super.formNumberClip(flag, temp, negative);
        }

        @Override
        protected void onNumberFlow(String value, short fromLevel, short toLevel) throws ParseException {
            if (toLevel == WordPresets.DECIMAL_D) throw err("数之大,一个long放不下!");
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
                if (!LITERAL_SPECIAL.contains(c)) {
                    temp.append((char) c);
                } else {
                    i--;
                    break;
                }
            }
            this.index = i;

            if (temp.length() == 0) {
                return eof();
            }

            String s = temp.toString();

            short id = WordPresets.LITERAL;
            switch (s) {
                case "true":
                    id = TRUE;
                    break;
                case "false":
                    id = FALSE;
                    break;
                case "null":
                    id = NULL;
                    break;
                case "nan":
                    return new Word_D(WordPresets.DECIMAL_D, i, Double.NaN , "nan");
                case "inf":
                    return new Word_D(WordPresets.DECIMAL_D, i, Double.POSITIVE_INFINITY, "nan");
            }

            return formClip(id, s);
        }

        @Override
        protected final Word readSymbol() throws ParseException {
            char c = next();

            String v;
            short id;
            switch (c) {
                case '{':
                    v = "{";
                    id = left_l_bracket;
                    break;
                case '}':
                    v = "}";
                    id = right_l_bracket;
                    break;
                case '[':
                    if (offset(0) == '[') {
                        v = "[[";
                        id = dlmb;
                        next();
                    } else {
                        v = "[";
                        id = left_m_bracket;
                    }
                    break;
                case ']':
                    if (remain() > 0 && offset(0) == ']') {
                        v = "]]";
                        id = drmb;
                        next();
                    } else {
                        v = "]";
                        id = right_m_bracket;
                    }
                    break;
                case '=':
                    v = "=";
                    id = eq;
                    break;
                case ',':
                    v = ",";
                    id = comma;
                    break;
                case '.':
                    v = ".";
                    id = dot;
                    break;
                default:
                    throw err("无效字符 '" + c + '\'');
            }

            return formClip(id, v);
        }

        String   k;
        CMapping m;
        public void dotName(String v, CMapping map, int flag) throws ParseException {
            boolean prevNNC = noNumberCheck;
            noNumberCheck = true;
            CMapping lv = map;
            loop:
            do {
                Word w = nextWord();
                if (w.type() == dot) {
                    w = nextWord();
                    switch (w.type()) {
                        case WordPresets.DECIMAL_D:
                        case WordPresets.DECIMAL_F:
                            throw err("别闹");
                        case WordPresets.INTEGER:
                        case WordPresets.LITERAL:
                        case WordPresets.STRING:
                            Map<String, CEntry> raw = lv.raw();
                            CEntry entry = raw.get(v);
                            if (entry == null) {
                                raw.put(v, entry = new CMapping(new MyHashMap<>()));
                            } else if (entry.getType() == Type.LIST) {
                                CList list = entry.asList();
                                if (list.size() == 0) throw err("空的行内数组");
                                entry = list.get(list.size() - 1);
                            }
                            if (entry.getType() == Type.MAP) {
                                lv = entry.asMap();
                                if ((flag & LENIENT) == 0 && lv.getClass() == CTOMLFxMap.class) {
                                    throw err("不能修改行内表");
                                }
                            } else {
                                throw err("不能覆写已存在的非表");
                            }
                            v = w.val();
                            break;
                        default:
                            retractWord();
                            break loop;
                    }
                } else {
                    retractWord();
                    break;
                }
            } while (true);
            noNumberCheck = prevNNC;
            this.k = v;
            this.m = lv;
        }

        public void ensureLineEnd() {
            // todo why require this?
        }
    }
}
