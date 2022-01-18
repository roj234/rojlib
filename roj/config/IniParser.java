/*
 * This file is a part of MoreItems
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2022 Roj234
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
import roj.config.serial.Serializers;
import roj.config.word.AbstLexer;
import roj.config.word.Word;
import roj.config.word.WordPresets;
import roj.io.IOUtil;
import roj.text.CharList;

import java.io.File;
import java.io.IOException;

import static roj.config.JSONParser.*;

/**
 * @author Roj233
 * @since 2022/1/6 13:46
 */
public class IniParser extends Parser {
    public static final int UNESCAPE = 4;
    static final short eq = 14;
    static final IBitSet LB = LongBitSet.from(']'), LN = LongBitSet.from("\r\n");

    public static void main(String[] args) throws ParseException, IOException {
        System.out.print(parse(IOUtil.readUTF(new File(args[0]))).toINI());
    }

    public static CMapping parse(CharSequence string) throws ParseException {
        return parse(new IniLexer().init(string), 0);
    }

    public static CMapping parse(CharSequence string, int flag) throws ParseException {
        return parse(new IniLexer().init(string), flag);
    }

    /**
     * @param flag COMMENT NO_DUPLICATE_KEY
     */
    public static CMapping parse(AbstLexer wr, int flag) throws ParseException {
        IniLexer l = (IniLexer) wr;
        CMapping ce = iniRoot(l, (byte) flag);
        if (l.nextWord().type() != WordPresets.EOF) {
            throw l.err("期待 /EOF");
        }
        return ce;
    }

    static CMapping iniRoot(IniLexer wr, byte flag) throws ParseException {
        CMapping map = iniValue(wr, flag);
        CMapping top = iniValue(wr, flag);
        if (top.size() > 0)
            map.put("<root>", top);

        while (true) {
            Word w = wr.nextWord();
            if (w.type() != left_m_bracket) break;
            String name = wr.readTill(LB);
            if (name == null) break;
            if((flag & NO_DUPLICATE_KEY) != 0 && map.containsKey(name))
                throw wr.err("重复的key: " + name);
            wr.index++;
            map.put(name, iniValue(wr, flag));
        }
        return map;
    }

    static CMapping iniValue(IniLexer wr, byte flag) throws ParseException {
        CMapping map = new CMapping();

        while (true) {
            Word w = wr.nextWord();
            if (w.type() == WordPresets.EOF) break;
            if (w.type() == left_m_bracket) {
                wr.retractWord();
                break;
            }
            String name = w.val();
            if((flag & NO_DUPLICATE_KEY) != 0 && map.containsKey(name))
                throw wr.err("重复的key: " + name);
            w = wr.nextWord();
            if (w.type() != eq) {
                unexpected(wr, w.val());
            }
            try {
                map.put(name, iniRead(wr, flag));
            } catch (ParseException e) {
                throw e.addPath(name + '.');
            }
        }
        return map;
    }

    @SuppressWarnings("fallthrough")
    static CEntry iniRead(IniLexer wr, byte flag) throws ParseException {
        int id = wr.index;
        String v = wr.readTill(LN);
        int id1 = wr.index;
        wr.index = id;
        Word w = wr.nextWord();
        if (wr.index < id1) {
            wr.index = id1;
            if ((flag & UNESCAPE) == 0) {
                v = wr.deSlashes(v);
            }
            return CString.valueOf(v);
        }
        switch (w.type()) {
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
            default:
                unexpected(wr, w.val());
                return null;
        }
    }

    public IniParser() {}

    public IniParser(Serializers ser) {
        this.ser = ser;
    }

    @Override
    public CEntry Parse(CharSequence string, int flag) throws ParseException {
        return parse(new IniLexer().init(string), flag);
    }

    @Override
    public String format() {
        return "INI";
    }

    public static Builder<IniParser> builder() {
        return new Builder<>(new IniParser(new Serializers()));
    }

    public static class IniLexer extends AbstLexer {
        @Override
        @SuppressWarnings("fallthrough")
        public Word readWord() throws ParseException {
            CharSequence in = this.input;
            int i = this.index;

            while (i < in.length()) {
                int c = in.charAt(i++);
                switch (c) {
                    case '"':
                        this.index = i;
                        return readConstString((char) c);
                    case ';':
                        int s = i;
                        while (i < in.length()) { // 单行注释
                            c = in.charAt(i++);
                            if (c == '\r' || c == '\n') {
                                if (c == '\r' && i < in.length() && in.charAt(i) == '\n')
                                    i++;
                                break;
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
            return formClip(WordPresets.STRING, readSlashString(key,  true));
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
                case '[':
                    v = "[";
                    id = left_m_bracket;
                    break;
                case ']':
                    v = "]";
                    id = right_m_bracket;
                    break;
                case '=':
                    v = "=";
                    id = eq;
                    break;
                default:
                    throw err("无效字符 '" + c + '\'');
            }

            return formClip(id, v);
        }

        public String readTill(IBitSet terminate) throws ParseException {
            CharSequence in = this.input;
            int i = this.index;

            if (i >= in.length()) return null;
            char c = in.charAt(i);
            if (c == '"' || c == '\'') {
                return readSlashString(c,  true).toString();
            }
            while (i < in.length()) {
                c = in.charAt(i++);
                if (terminate.contains(c)) {
                    return in.subSequence(index, index = i - 1).toString();
                }
            }
            return null;
        }

        public String deSlashes(String s) throws ParseException {
            CharList v = this.found;
            v.clear();
            deSlashes(s, v);
            return v.toString();
        }
    }
}
