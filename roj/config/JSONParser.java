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

import roj.collect.MyHashSet;
import roj.config.data.*;
import roj.config.serial.Serializer;
import roj.config.serial.Serializers;
import roj.config.word.AbstLexer;
import roj.config.word.Word;
import roj.config.word.WordPresets;
import roj.io.IOUtil;

import java.io.File;
import java.io.IOException;

/**
 * JSON解析器
 * @author Roj234
 */
public final class JSONParser implements Parser {
    static final short
            TRUE = 10,
            FALSE = 11,
            NULL = 12,
            left_l_bracket = 13,
            right_l_bracket = 14,
            left_m_bracket = 15,
            right_m_bracket = 16,
            comma = 17,
            colon = 18;

    public static final int
            NO_DUPLICATE_KEY       = 1,
            LITERAL_KEY            = 2,
            UNESCAPED_SINGLE_QUOTE = 4,
            NO_EOF                 = 8,
            INTERN                 = 16;

    public static void main(String[] args) throws ParseException, IOException {
        System.out.println(parse(IOUtil.readUTF(new File(args[0]))));
    }

    public static CEntry parse(CharSequence string) throws ParseException {
        return parse(new JSONLexer().init(string), 0);
    }

    public static CEntry parse(CharSequence string, int flag) throws ParseException {
        return parse(new JSONLexer().init(string), flag);
    }

    @Override
    public CEntry Parse(CharSequence string, int flag) throws ParseException {
        return parse(string, flag);
    }

    @Override
    public String format() {
        return "JSON";
    }

    public static CEntry parse(AbstLexer wr, int flag) throws ParseException {
        JSONLexer l = (JSONLexer) wr;
        l.flag = (byte) flag;

        CEntry ce = jsonRead(l, (byte) flag);

        if ((flag & NO_EOF) == 0 && wr.nextWord().type() != WordPresets.EOF) {
            throw wr.err("期待 /EOF");
        }
        return ce;
    }

    /**
     * 解析数组定义 <BR>
     * [xxx, yyy, zzz] or []
     */
    private static CList jsonArray(AbstLexer wr, byte flag) throws ParseException {
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
                    list.add(jsonRead(wr, flag));
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
    private static CMapping jsonObject(AbstLexer wr, byte flag) throws ParseException {
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

            more = false;

            Word w = wr.nextWord();
            if (w.type() != colon)
                unexpected(wr, w.val(), ":");

            map.put(v, jsonRead(wr, flag));
        }

        if (map.containsKey("==", Type.STRING)) {
            Serializer<?> des = Serializers.find(map.getString("=="));
            if (des != null) {
                return new CObject<>(map.raw(), des);
            }
        }

        return map;
    }

    @SuppressWarnings("fallthrough")
    private static CEntry jsonRead(AbstLexer wr, byte flag) throws ParseException {
        Word w = wr.nextWord();
        switch (w.type()) {
            case left_m_bracket:
                return jsonArray(wr, flag);
            case WordPresets.STRING:
                return CString.valueOf(w.val());
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
            case left_l_bracket:
                return jsonObject(wr, flag);
            case WordPresets.LITERAL:
                if ((flag & LITERAL_KEY) != 0)
                    return CString.valueOf(w.val());
            default:
                unexpected(wr, w.val());
                return null;
        }
    }

    static void unexpected(AbstLexer wr, String got, String expect) throws ParseException {
        throw wr.err("未预料的: " + got + ", 期待: " + expect);
    }

    static void unexpected(AbstLexer wr, String got) throws ParseException {
        throw wr.err("未预料的: " + got);
    }

    public static class JSONLexer extends AbstLexer {
        final MyHashSet<String> ipool = new MyHashSet<>();

        public byte flag;

        @Override
        @SuppressWarnings("fallthrough")
        public Word readWord() throws ParseException {
            CharSequence in = this.input;
            int i = this.index;

            while (i < in.length()) {
                int c = in.charAt(i++);
                switch (c) {
                    case '\'':
                    case '"':
                        this.index = i;
                        return readConstString((char) c);
                    case '/':
                        this.index = i;
                        Word word = ignoreStdNote();
                        if (word != null)
                            return word;
                        i = this.index;
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

        @Override
        protected final Word readConstString(char key) throws ParseException {
            return formClip(WordPresets.STRING, readSlashString(key, key != '\'' || (flag & UNESCAPED_SINGLE_QUOTE) == 0));
        }

        @Override
        protected final Word formAlphabetClip(CharSequence temp) {
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
            }

            return formClip(id, s);
        }

        /**
         * 回收利用Word对象
         */
        protected Word formClip(short id, CharSequence s) {
            if(cached == null) {
                cached = new Word();
            }
            String word = s.toString();
            if ((flag & INTERN) != 0) word = ipool.intern(word);
            return cached.reset(id, index, word);
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
                default:
                    throw err("无效字符 '" + c + '\'');
            }

            return formClip(id, v);
        }
    }
}
