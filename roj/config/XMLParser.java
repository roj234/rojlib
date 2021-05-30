package roj.config;

import roj.collect.LongBitSet;
import roj.collect.MyHashMap;
import roj.concurrent.OperationDone;
import roj.config.data.*;
import roj.config.word.AbstLexer;
import roj.config.word.Lexer;
import roj.config.word.Word;
import roj.config.word.WordPresets;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

import static roj.config.JSONParser.unexpected;

/**
 * XML - 可扩展标记语言（EXtensible Markup Language）
 * <BR>
 * 元数据（有关数据的数据）应当存储为属性，而数据本身应当存储为元素。
 * <p>
 * @author Roj234
 * Filename: XMLParser.java
 */
public class XMLParser {
    public static final short
            left_curly_bracket = 0,
            right_curly_bracket = 1,
            slash = 2, equ = 3, ask = 4,
            semicolon = 5, not = 6, colon = 7,
            namespace = 8, unknown = 9;

    public static void main(String[] args) throws ParseException {
        String xml = TextUtil.concat(args, ' ');

        System.out.println("INPUT = " + xml);

        final XMLHeader xmls = parse(xml);
        System.out.print("XML = " + xmls.toString());
    }

    public static XMLHeader parse(String string) throws ParseException {
        return parse((XMLexer) new XMLexer().init(string));
    }

    public static XMLHeader parse(XMLexer wr) throws ParseException {
        XMLHeader ce = xmlHeader(wr);
        if (wr.hasNext()) {
            throw wr.err("期待 /EOF");
        }
        return ce;
    }

    public static XMLHeader xmlHeader(XMLexer wr) throws ParseException {
        Word w = wr.nextWord();
        if (w.type() == left_curly_bracket) {
            except(wr, ask, "?");
            w = wr.nextWord();
            if (w.type() != WordPresets.LITERAL || !w.val().equals("xml")) {
                unexpected(wr, w.val(), "xml");
            }

            MyHashMap<String, ConfEntry> attributes = new MyHashMap<>();
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
                        attributes.put(aName, ConfEntry.of(wr.nextWord()));
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
                        children.add(xmlElement(wr));
                        break;
                    case WordPresets.EOF:
                        break o;
                    default:
                        unexpected(wr, w.val(), "<");
                        break;
                }
            }

            return new XMLHeader(attributes.isEmpty() ? Collections.emptyMap() : attributes, children.isEmpty() ? Collections.emptyList() : children);

        } else {
            unexpected(wr, w.val());
            return Helpers.nonnull();
        }
    }

    @SuppressWarnings("fallthrough")
    public static XElement xmlElement(XMLexer wr) throws ParseException {
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
            MyHashMap<String, ConfEntry> attributes = new MyHashMap<>();
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
                        except(wr, equ, "=");
                        // todo 这里可以拿到xmlns:xxx if aName.startsWith("xmlns:")
                        attributes.put(aName, ConfEntry.of(wr.nextWord()));
                    }
                    break;
                    default:
                        unexpected(wr, w.val(), "属性 / '>'");
                }
            }

            if (needCloseTag && !wr.noCloseTags.contains(name)) {
                AbstLexer.Snapshot lcb = wr.snapshot();
                w = wr.nextWord(); // 这里有问题，特殊符号，已修复 unknown

                if (!wr.checkCDATATag(w)) {
                    o:
                    while (true) {
                        if (w.type() == left_curly_bracket) {
                            w = wr.nextWord();
                            if (w.type() == slash) {
                                w = wr.nextWord();

                                switch (w.type()) {
                                    case WordPresets.LITERAL:
                                    case namespace:
                                        if (!w.val().equals(name)) {
                                            throw wr.err("结束标签不匹配! 需要 " + name + " 找到 " + w.val());
                                        }

                                        break o;
                                    default:
                                        unexpected(wr, w.val(), "标签名");
                                        break;
                                }

                                break;
                            } else {
                                wr.restore(lcb);
                                children.add(xmlElement(wr));
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

                except(wr, right_curly_bracket, ">");
            }

            return new XElement(name, /*value, */attributes.isEmpty() ? Collections.emptyMap() : attributes, children.isEmpty() ? Collections.emptyList() : children);
        } else {
            unexpected(wr, w.val());
        }
        throw OperationDone.NEVER;
    }

    static void except(Lexer wr, short id, String s) throws ParseException {
        Word w = wr.nextWord();
        if (w.type() != id) {
            unexpected(wr, w.val(), s);
        }
    }

    public static final class XMLexer extends Lexer {
        static final LongBitSet SPECIAL = LongBitSet.preFilled("+-<>/=?;!:");

        Set<String> noCloseTags = Collections.emptySet();

        public XMLexer noCloseTags(@Nonnull Set<String> noCloseTags) {
            this.noCloseTags = noCloseTags;
            return this;
        }

        @Override
        protected Word readAlphabet() {
            CharSequence input = this.input;
            int index = this.index;

            CharList temp = this.found;
            temp.clear();

            boolean ns = false;

            while (index < input.length()) {
                char c = input.charAt(index++);
                if (c == ':') {
                    ns = true;
                    temp.append(c);
                } else {
                    if (!SPECIAL.contains(c) && !WHITESPACE.contains(c)) {
                        temp.append(c);
                    } else {
                        index--;
                        break;
                    }
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
                            if (SPECIAL.contains(c)) {
                                switch (c) { // todo +-
                                    case '+':
                                        next();
                                    case '-':
                                        return readDigit();
                                }
                                return readSpecial();
                            } else if (NUMBER.contains(c)) {
                                return readDigit();
                            } else {
                                return readAlphabet();
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
                            case '&':
                                temp.append(decodeEntity());
                                break;
                            case '<':
                                index--;
                                break o;
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

        private char decodeEntity() throws ParseException {
            String v = readAlphabet().val();

            char c;
            switch (v) {
                case "lt":
                    c = '<';
                    break;
                case "gt":
                    c = '>';
                    break;
                case "amp":
                    c = '&';
                    break;
                case "apos":
                    c = '\'';
                    break;
                case "quot":
                    c = '"';
                    break;
                default:
                    throw err("无效转义符");
            }

            if (!readSpecial().val().equals(";")) {
                throw err("转义需要以;结束");
            }

            return c;
        }

        @Override
        protected Word readSpecial() throws ParseException {
            char c = next();

            short id;
            switch (c) {
                case '<':
                    id = left_curly_bracket;
                    break;
                case '/':
                    id = slash;
                    break;
                case '>':
                    id = right_curly_bracket;
                    break;
                case '=':
                    id = equ;
                    break;
                case '?':
                    id = ask;
                    break;
                case ';':
                    id = semicolon;
                    break;
                case '!':
                    id = not;
                    break;
                case ':':
                    id = colon;
                    break;
                default:
                    throw err("无效字符 '" + c + '\'');
            }

            return formClip(id, String.valueOf(c));
        }

        public boolean checkCDATATag(Word w) {
            return ((CharList) input).regionMatches(index, "![CDATA[", 0);
        }
    }
}
