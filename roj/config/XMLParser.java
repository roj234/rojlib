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

import roj.collect.LongBitSet;
import roj.collect.MyHashMap;
import roj.concurrent.OperationDone;
import roj.config.data.*;
import roj.config.serial.Serializers;
import roj.config.word.AbstLexer;
import roj.config.word.Word;
import roj.config.word.WordPresets;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.function.Predicate;

import static roj.config.JSONParser.NO_EOF;
import static roj.config.JSONParser.unexpected;

/**
 * XML - 可扩展标记语言（EXtensible Markup Language）
 * @author Roj234
 */
public class XMLParser extends Parser {
    static final short
            left_curly_bracket = 10,
            right_curly_bracket = 11,
            slash = 12, equ = 13, ask = 14,
            semicolon = 15, not = 16, colon = 17,
            namespace = 18, unknown = 19;

    public static final int KEEP_ENTITY = 1, LENIENT = 2;
    private static final int HEADER = 128;

    public static void main(String[] args) throws ParseException, IOException {
        System.out.print("XML = " + parse(IOUtil.readUTF(new File(args[0]))).toString());
    }

    public static XHeader parse(CharSequence string) throws ParseException {
        return parse((XMLexer) new XMLexer().init(string), 0);
    }

    public static XHeader parse(CharSequence string, int flag) throws ParseException {
        return parse((XMLexer) new XMLexer().init(string), flag);
    }

    public static XHeader parse(XMLexer wr, int flag) throws ParseException {
        XHeader ce = xmlHeader(wr, flag | HEADER);
        if ((flag & NO_EOF) == 0 && wr.nextWord().type() != WordPresets.EOF) {
            throw wr.err("期待 /EOF");
        }
        return ce;
    }

    public static XHeader xmlHeader(XMLexer wr, int flag) throws ParseException {
        int begin = wr.index;
        Word w = wr.nextWord();
        if (w.type() == left_curly_bracket) {
            w = wr.nextWord();
            if (w.type() != ask) {
                if ((flag & LENIENT) == 0)
                    unexpected(wr, w.val(), "?");
                wr.index = begin;
                XHeader h = new XHeader();
                h.appendChild(xmlElement(wr, flag));
                return h;
            }
            w = wr.nextWord();
            if (w.type() != WordPresets.LITERAL || !w.val().equals("xml")) {
                unexpected(wr, w.val(), "xml");
            }

            MyHashMap<String, CEntry> attributes = new MyHashMap<>();
            ArrayList<AbstXML> children = new ArrayList<>();

            o:
            while (true) {
                w = wr.nextWord();
                switch (w.type()) {
                    case ask:
                        except(wr, right_curly_bracket, ">");
                        break o;
                    case namespace:
                    case WordPresets.LITERAL: {
                        String aName = w.val();
                        except(wr, equ, "=");
                        attributes.put(aName, of(wr.nextWord()));
                    }
                    break;
                    case right_curly_bracket:
                        break o;
                    default:
                        unexpected(wr, w.val(), "属性 / '>'");
                }
            }

            o:
            while (true) {
                w = wr.nextWord();
                switch (w.type()) {
                    case left_curly_bracket:
                        wr.retractWord();
                        children.add(xmlElement(wr, flag));
                        break;
                    case WordPresets.EOF:
                        break o;
                    default:
                        unexpected(wr, w.val(), "<");
                        break;
                }
            }

            return new XHeader(attributes.isEmpty() ? Collections.emptyMap() : attributes, children.isEmpty() ? Collections.emptyList() : children);

        } else {
            unexpected(wr, w.val());
            return Helpers.nonnull();
        }
    }

    @SuppressWarnings("fallthrough")
    public static XElement xmlElement(XMLexer wr, int flag) throws ParseException {
        Word w = wr.nextWord();
        if (w.type() == left_curly_bracket) {
            w = wr.nextWord();

            String name;
            switch (w.type()) {
                case namespace:
                case WordPresets.LITERAL:
                    name = w.val();
                    break;
                default:
                    unexpected(wr, w.val(), "标签名");
                    return null;
            }

            String value = null;
            MyHashMap<String, CEntry> attributes = new MyHashMap<>();
            ArrayList<AbstXML> children = new ArrayList<>();

            boolean needCloseTag = true;

            o:
            while (true) {
                w = wr.nextWord();
                switch (w.type()) {
                    case slash:
                        needCloseTag = false;
                        except(wr, right_curly_bracket, ">");
                    case right_curly_bracket:
                        break o;
                    case namespace:
                    case WordPresets.LITERAL: {
                        String aName = w.val();
                        if(wr.nextWord().type() == equ) {
                            // 这里可以拿到xmlns:xxx if aName.startsWith("xmlns:")
                            attributes.put(aName, of(wr.nextWord()));
                        } else {
                            wr.retractWord();
                            attributes.put(aName, CString.valueOf(""));
                        }
                    }
                    break;
                    default:
                        unexpected(wr, w.val(), "属性 / '>'");
                }
            }

            if (needCloseTag && wr.hasCloseTags.test(name)) {
                AbstLexer.Snapshot lcb = wr.snapshot();
                w = wr.nextWord();

                if (!wr.checkCDATATag(w)) {
                    o:
                    while (w.type() != WordPresets.EOF) {
                        if (w.type() == left_curly_bracket) {
                            w = wr.nextWord();
                            if (w.type() == slash) {
                                w = wr.nextWord();

                                switch (w.type()) {
                                    case WordPresets.LITERAL:
                                    case namespace:
                                        if (!w.val().equals(name)) {
                                            if((flag & LENIENT) != 0) {
                                                wr.errorTag = w.val();
                                                break o;
                                            } else {
                                                throw wr.err("结束标签不匹配! 需要 " + name + " 找到 " + w.val());
                                            }
                                        }

                                        break o;
                                    default:
                                        unexpected(wr, w.val(), "标签名");
                                        break;
                                }

                                break;
                            } else {
                                wr.restore(lcb);
                                XElement xe = xmlElement(wr, flag);
                                if(xe != null) {
                                    children.add(xe);
                                }
                                wr.snapshot(lcb);
                                w = wr.nextWord();
                            }
                        } else {
                            wr.retractWord();
                            // 这里可以跳过元素里面的内容
                            final XText str = wr.readString(0, null);
                            if(str != null)
                                children.add(str);
                            wr.snapshot(lcb);
                            w = wr.nextWord();
                        }
                    }
                } else {
                    wr.retractWord();
                    final XText str = wr.readString(1, null);
                    if(str != null)
                        children.add(str);

                    //if(CDATA == 1) {
                        except(wr, left_curly_bracket, "<");
                        except(wr, slash, "/");
                        w = wr.nextWord();
                        switch (w.type()) {
                            case WordPresets.LITERAL:
                            case namespace:
                                if (!w.val().equals(name)) {
                                    throw wr.err("结束标签不匹配! 需要 " + name + " 找到 " + w.val());
                                }

                                break;
                            default:
                                unexpected(wr, w.val(), "标签名");
                                break;
                        }
                    //}
                }

                if((flag & LENIENT) == 0 || wr.hasNext())
                    except(wr, right_curly_bracket, ">");
            }

            return new XElement(name, attributes.isEmpty() ? Collections.emptyMap() : attributes, children.isEmpty() ? Collections.emptyList() : children, !needCloseTag);
        } else {
            unexpected(wr, w.val());
        }
        throw OperationDone.NEVER;
    }

    static void except(AbstLexer wr, short id, String s) throws ParseException {
        Word w = wr.nextWord();
        if (w.type() != id) {
            unexpected(wr, w.val(), s);
        }
    }

    public static CEntry of(Word word) {
        switch (word.type()) {
            case WordPresets.DECIMAL_D:
            case WordPresets.DECIMAL_F:
                return CDouble.valueOf(word.number().asDouble());
            case WordPresets.INTEGER:
                return CInteger.valueOf(word.number().asInt());
            case WordPresets.LONG:
                return CLong.valueOf(word.number().asLong());
            case WordPresets.STRING:
                return CString.valueOf(word.val());
            case WordPresets.LITERAL:
                switch (word.val()) {
                    case "true":
                        return CBoolean.valueOf(true);
                    case "false":
                        return CBoolean.valueOf(false);
                    default:
                        return CString.valueOf(word.val());
                }
        }
        throw new IllegalArgumentException(String.valueOf(word));
    }

    public XMLParser() {}

    public XMLParser(Serializers ser) {
        this.ser = ser;
    }

    @Override
    public CEntry Parse(CharSequence string, int flag) throws ParseException {
        return parse((XMLexer) new XMLexer().init(string), flag).toJSON();
    }

    @Override
    public String format() {
        return "XML";
    }

    public static Builder<XMLParser> builder() {
        return new Builder<>(new XMLParser(new Serializers()));
    }

    public static final class XMLexer extends AbstLexer {
        static final LongBitSet XML_SPECIAL = LongBitSet.from("+-<>/=?;!:");
        static final LongBitSet XML_LITERAL = LongBitSet.from("+<>/=?;!\r\n \t");

        public Predicate<String> hasCloseTags = Helpers.alwaysTrue();
        String errorTag;

        public XMLexer hasCloseTags(Predicate<String> p) {
            this.hasCloseTags = p == null ? Helpers.alwaysTrue() : p;
            return this;
        }

        @Override
        protected Word readLiteral() {
            CharSequence input = this.input;
            int index = this.index;

            CharList temp = this.found;
            temp.clear();

            boolean ns = false;

            while (index < input.length()) {
                char c = input.charAt(index++);
                if (!XML_LITERAL.contains(c)) {
                    if (c == ':') {
                        ns = true;
                    }
                    temp.append(c);
                } else {
                    index--;
                    break;
                }
            }

            this.index = index;

            if (temp.length() == 0) {
                return eof();
            }

            return formClip(ns ? namespace : WordPresets.LITERAL, temp.toString());
        }

        @Override
        @SuppressWarnings("fallthrough")
        public Word readWord() throws ParseException {
            CharSequence input = this.input;
            int index = this.index;

            int rem;
            while ((rem = input.length() - index) > 0) {
                int c = input.charAt(index++);
                switch (c) {
                    case '\'':
                    case '"':
                        this.index = index;
                        return readConstString((char) c);
                    case '<':
                        if (rem > 4 && input.charAt(index) == '!' && input.charAt(index + 1) == '-' && input.charAt(index + 2) == '-') { // <!--
                            index += 3;
                            while (index < input.length()) {
                                while (index < input.length() && input.charAt(index++) != '-') ; // -->
                                if (input.charAt(index) == '-' && input.charAt(index + 1) == '>') {
                                    index += 2;
                                    break;
                                }
                            }
                            break;
                        }
                    default: {
                        if (!WHITESPACE.contains(c)) {
                            this.index = index - 1;
                            if (XML_SPECIAL.contains(c)) {
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

        @SuppressWarnings("fallthrough")
        public XText readString(int CDATA, String name) throws ParseException {
            CharSequence input = this.input;
            int index = this.index;

            CharList temp = found;
            temp.clear();

            switch (CDATA) {
                case 0:
                    o:
                    while (index < input.length()) {
                        char c = input.charAt(index++);
                        switch (c) {
                            case '<':
                                index--;
                                break o;
                            /*case '&':
                                if((flag & KEEP_ENTITY) != 0) {
                                    temp.append(decodeEntity());
                                    break;
                                }*/
                            default:
                                temp.append(c);
                                break;
                        }
                    }
                    break;
                case 1:
                    index += 9;
                    while (index < input.length()) { //]]>
                        char c = input.charAt(index++);
                        if (c == ']' && input.charAt(index) == ']' && input.charAt(index + 1) == '>') {
                            index += 2;
                            break;
                        }
                        temp.append(c);
                    }
                    break;
                case 2:
                    int xc = 0;
                    this.index = index;
                    o:
                    while (hasNext()) {
                        Word c = nextWord();
                        if(c.type() == WordPresets.EOF) break;
                        if(c.val().length() != 1) {
                            temp.append(c.val());
                        } else {
                            switch (c.type()) {
                                case right_curly_bracket:
                                    if (xc > 0) {
                                        xc--;
                                    }
                                    temp.append(c.val());
                                    break;
                                case left_curly_bracket:
                                    if(hasNext() && !WHITESPACE.contains(offset(0)) && xc++ == 0) {
                                        Word c2 = nextWord();
                                        if(c2.type() == slash) {
                                            c2 = nextWord();
                                            if(c2.type() == WordPresets.LITERAL) {
                                                if(c2.val().equals(name)) {
                                                    break o;
                                                }
                                            }
                                            temp.append("</").append(c2.val());
                                        } else {
                                            temp.append('<').append(c2.val());
                                        }
                                        break;
                                    }
                                default:
                                    temp.append(c.val());
                                    break;
                            }
                        }
                    }
                    break;
            }

            this.index = index;

            //if (temp.length() == 0 && CDATA != 2)
            //    throw err("未预料的 EOF");

            return temp.length() > 0 ? new XText(temp.toString()) : null;
        }

        @Override
        protected Word onInvalidNumber(char value, int i) {
            return readLiteral();
        }

//        private char decodeEntity() throws ParseException {
//            String v = readLiteral().val();
//
//            char c;
//            switch (v) {
//                case "lt":
//                    c = '<';
//                    break;
//                case "gt":
//                    c = '>';
//                    break;
//                case "amp":
//                    c = '&';
//                    break;
//                case "apos":
//                    c = '\'';
//                    break;
//                case "quot":
//                    c = '"';
//                    break;
//                default:
//                    throw err("无效转义符/作者不知道的转义符");
//            }
//
//            if (!readSymbol().val().equals(";")) {
//                throw err("转义需要以;结束");
//            }
//
//            return c;
//        }

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
        protected Word readSymbol() throws ParseException {
            String v;
            short id;
            switch (next()) {
                case '<':
                    v = "<";
                    id = left_curly_bracket;
                    break;
                case '/':
                    v = "/";
                    id = slash;
                    break;
                case '>':
                    v = ">";
                    id = right_curly_bracket;
                    break;
                case '=':
                    v = "=";
                    id = equ;
                    break;
                case '?':
                    v = "?";
                    id = ask;
                    break;
                case ';':
                    v = ";";
                    id = semicolon;
                    break;
                case '!':
                    v = "!";
                    id = not;
                    break;
                case ':':
                    v = ":";
                    id = colon;
                    break;
                default:
                    throw err("无效字符 '" + offset(-1) + '\'');
            }

            return formClip(id, v);
        }

        public boolean checkCDATATag(Word w) {
            return TextUtil.regionMatches(input, index, "![CDATA[", 0);
        }
    }
}
