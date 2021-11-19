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
import roj.config.word.AbstLexer;
import roj.config.word.Word;
import roj.config.word.WordPresets;
import roj.util.ByteList;
import roj.util.ByteReader;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * JSON - JavaScript 对象表示法（JavaScript Object Notation）
 * <BR>
 * 它是JavaScript的子集
 * 可以直接复制到JavaScript中
 * <p>
 * @author Roj234
 */
public final class JSONParser {
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
    public static final int NO_DUPLICATE_KEY = 1, LITERAL_KEY = 2, UNESCAPED_SINGLE_YH = 4, NO_EOF = 8;

    public static void main(String[] args) throws ParseException, IOException {
        String s = ByteReader.readUTF(new ByteList().readStreamFully(new FileInputStream(args[0])));
        System.out.println(parse(s));
    }

    public static CEntry parse(CharSequence string) throws ParseException {
        return parse(new JSONLexer().init(string), 0);
    }

    public static CEntry parse(CharSequence string, int flag) throws ParseException {
        return parse(new JSONLexer().init(string), flag);
    }

    public static CEntry parseIntern(CharSequence string) throws ParseException {
        return parse(new InternLexer().init(string), 0);
    }

    public static List<CEntry> parseMulti(CharSequence string) throws ParseException {
        return parseMulti(new InternLexer().init(string), 0);
    }

    public static List<CEntry> parseMulti(AbstLexer wr, int flag) throws ParseException {
        JSONLexer l = (JSONLexer) wr;
        l.flag = (byte) flag;

        List<CEntry> list = new ArrayList<>();
        while (wr.hasNext()) {
            list.add(jsonRead(l, (byte) flag));
        }
        return list;
    }

    /**
     * @param flag <BR>
     *             1: 对重复的key报错 <BR>
     *             2: 仁慈模式 (忽略key中的literal string) <BR>
     *             4: 不对单引号转义
     */
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
    static CList jsonArray(JSONLexer wr, byte flag) throws ParseException {
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
    static CMapping jsonObject(JSONLexer wr, byte flag) throws ParseException {
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

            map.put(name.val(), jsonRead(wr, flag));
        }

        if (map.containsKey("==", Type.STRING)) {
            ObjSerializer<?> deserializer = ObjSerializer.REGISTRY.get(map.getString("=="));
            if (deserializer != null) {
                return new CObject<>(map, deserializer);
            }
        }

        return map;
    }

    @SuppressWarnings("fallthrough")
    private static CEntry jsonRead(JSONLexer wr, byte flag) throws ParseException {
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

    private static class JSONLexer extends AbstLexer {
        public byte flag;

        @Override
        @SuppressWarnings("fallthrough")
        public Word readWord() throws ParseException {
            CharSequence input = this.input;
            int index = this.index;

            while (index < input.length()) {
                int c = input.charAt(index++);
                switch (c) {
                    case '\'':
                    case '"':
                        this.index = index;
                        return readConstString((char) c);
                    case '/':
                        this.index = index;
                        Word word = ignoreStdNote();
                        if (word != null)
                            return word;
                        index = this.index;
                        break;
                    default: {
                        if (!WHITESPACE.contains(c)) {
                            this.index = index - 1;
                            if (SPECIAL.contains(c)) {
                                switch (c) {
                                    case '-':
                                    case '+':
                                        if(input.length() > index && NUMBER.contains(input.charAt(index))) {
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
            this.index = index;
            return eof();
        }

        @Override
        protected final Word readConstString(char key) throws ParseException {
            return formClip(WordPresets.STRING, readSlashString(key, key != '\'' || (flag & 4) == 0));
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
            return cached.reset(id, index, s.toString());
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

    private static class InternLexer extends JSONLexer {
        final MyHashSet<String> set = new MyHashSet<>();

        @Override
        protected Word formClip(short id, CharSequence s) {
            return super.formClip(id, set.intern(s.toString()));
        }

    }
}
