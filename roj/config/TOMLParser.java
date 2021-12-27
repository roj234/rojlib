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
import roj.config.data.*;
import roj.config.word.AbstLexer;
import roj.config.word.Word;
import roj.config.word.WordPresets;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.util.ByteList;

import java.io.File;
import java.io.IOException;

import static roj.config.JSONParser.unexpected;

/**
 *
 *
 * @author Roj234
 * @version 0.1
 * @since /
 */
public class TOMLParser {
    static final short
            TRUE = 10,
            FALSE = 11,
            NULL = 12,
            left_m_bracket = 13,
            right_m_bracket = 14,
            left_l_bracket = 19,
            right_l_bracket = 20,
            comma = 15, // ,
            colon = 16, // :
            delim = 17, // -
            _DATE_ = 18;

    public static void main(String[] args) throws ParseException, IOException {
        CharList yaml = new CharList();
        ByteList.decodeUTF(-1, yaml, new ByteList(IOUtil.read(new File(args[0]))));

        System.out.print("YML = " + parse(yaml).toYAML());
    }

    public static CMapping parse(CharSequence string) throws ParseException {
        return parse((TOMLLexer) new TOMLLexer().init(string), 0);
    }

    /**
     * @param flag <BR>
     *             2: 对重复的key报错 <BR>
     *             4: 解析注释
     */
    public static CMapping parse(TOMLLexer wr, int flag) throws ParseException {
        wr.comment = (flag & 4) != 0;
        CMapping ce = tomlObject(wr, (byte) flag & ~1);
        if (wr.hasNext()) {
            throw wr.err("期待 /EOF");
        }
        return ce;
    }


    /**
     * 解析数组定义 <BR>
     * [xxx, yyy, zzz] or []
     */
    static CList tomlFlowArray(TOMLLexer wr, byte flag) throws ParseException {
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
                    list.add(tomlRead(wr, flag));
                    break;
            }
        }

        return list;
    }

    /**
     * 解析对象定义 <BR>
     * {xxx: yyy, zzz: uuu}
     */
    @SuppressWarnings("fallthrough")
    static CMapping tomlFlowObject(TOMLLexer wr, byte flag) throws ParseException {
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

            if((flag & 1) != 0 && map.containsKey(name.val()))
                throw wr.err("重复的key: " + name.val());

            more = false;

            Word w = wr.nextWord();
            if (w.type() != colon)
                unexpected(wr, w.val(), ":");

            map.put(name.val(), tomlRead(wr, flag));
        }

        if (map.containsKey("==", Type.STRING)) {
            ObjSerializer<?> deserializer = ObjSerializer.REGISTRY.get(map.getString("=="));
            if (deserializer != null) {
                return new CObject<>(map, deserializer);
            }
        }

        return map;
    }

    /**
     * 解析对象定义 <BR>
     * a : r \r\n
     * c : x
     */
    @SuppressWarnings("fallthrough")
    static CMapping tomlObject(TOMLLexer wr, int flag) throws ParseException {
        CMapping map = new CMapping();

        return map;
    }

    private static CEntry tomlRead(TOMLLexer wr, int flag) throws ParseException {
        Word w = wr.nextWord();
        switch (w.type()) {
            case WordPresets.COMMENT:
                return new CComment(w.val());
            case left_m_bracket:
                //return yamlFlowArray(wr, flag);
            case WordPresets.STRING:
            case WordPresets.LITERAL: {
                int i = wr.lastWord;
                if(wr.nextWord().type() == colon) {
                    wr.index = i;
                    return tomlObject(wr, flag);
                } else {
                    wr.retractWord();
                    return CString.valueOf(w.val());
                }
            }
            case WordPresets.DECIMAL_D:
            case WordPresets.DECIMAL_F:
                return CDouble.valueOf(w.val());
            case WordPresets.INTEGER:
                return CInteger.valueOf(w.val());
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

    private static final class TOMLLexer extends AbstLexer {
        static final IBitSet SPECIAL = LongBitSet.from("+-()#.=?\"'[]");

        boolean comment;

        @Override
        public Word readWord() throws ParseException {
            int i = this.index;
            CharSequence in = this.input;
            while (i < in.length()) {
                int c = in.charAt(i++);
                switch (c) {
                    case '"':
                        this.index = i;
                        return readConstString((char) c);
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

                        if (comment) {
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
                if ((!SPECIAL.contains(c) || c == '-') && !WHITESPACE.contains(c)) {
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
                case "yes":
                case "on":
                    id = TRUE;
                    break;
                case "false":
                case "no":
                case "off":
                    id = FALSE;
                    break;
                case "null":
                    id = NULL;
                    break;
            }

            return formClip(id, s);
        }

        @Override
        protected Word readSymbol() throws ParseException {
            char c = next();

            short id;
            switch (c) {
                case '[':
                    id = left_m_bracket;
                    break;
                case ']':
                    id = right_m_bracket;
                    break;
                case ':':
                    id = colon;
                    break;
                case ',':
                    id = comma;
                    break;
                case '-':
                    id = delim;
                    break;
                default:
                    throw err("无效字符 '" + c + '\'');
            }

            return formClip(id, String.valueOf(c));
        }
    }
}
