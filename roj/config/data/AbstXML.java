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
package roj.config.data;

import roj.collect.LongBitSet;
import roj.collect.MyHashMap;
import roj.config.ParseException;
import roj.config.XMLParser;
import roj.config.word.AbstLexer;
import roj.config.word.Word;
import roj.config.word.WordPresets;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.Consumer;

/**
 * XML Parser Value Base
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/10/31 15:28
 */
public abstract class AbstXML implements Iterable<AbstXML> {
    static final short lsb = 10, rsb = 11,
            dot = 12,
            lmb = 13, rmb = 14,
            any = 15,
            equ = 16, neq = 17, gtr = 18, lss = 19, geq = 20, leq = 21,
            or = 22,
            comma = 23;

    private static class XSLexer extends AbstLexer {
        static final LongBitSet SPECIAL = LongBitSet.from("+-()!=<>.[]*,");

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
        protected Word readSymbol() throws ParseException {
            char c = next();
            String s;

            short id;
            switch (c) {
                case ')':
                    s = ")";
                    id = rsb;
                    break;
                case '(':
                    s = "(";
                    id = lsb;
                    break;
                case '.':
                    s = ".";
                    id = dot;
                    break;
                case '[':
                    s = "[";
                    id = lmb;
                    break;
                case ']':
                    s = "]";
                    id = rmb;
                    break;
                case '*':
                    s = "*";
                    id = any;
                    break;
                case ',':
                    s = ",";
                    id = comma;
                    break;
                case '=':
                    s = "=";
                    id = equ;
                    break;
                case '!':
                    if(offset(0) != '=')
                        throw err("无效字符 '!'");
                    index++;
                    s = "!=";
                    id = neq;
                    break;
                case '>':
                    if(offset(0) == '=') {
                        index++;
                        s = ">=";
                        id = geq;
                    } else {
                        s = ">";
                        id = gtr;
                    }
                    break;
                case '<':
                    if(offset(0) == '=') {
                        index++;
                        s = "<=";
                        id = leq;
                    } else {
                        s = ">";
                        id = lss;
                    }
                    break;
                case '|':
                    s = "!";
                    id = or;
                    break;
                default:
                    throw err("无效字符 '" + c + '\'');
            }

            return formClip(id, s);
        }
    }

    @SuppressWarnings("fallthrough")
    /**
     * module.component[name="NewModuleRootManager"].content
     * my.data(2).com[type="xyz" | 456, ntr != 233].*
     */
    public List<AbstXML> getXS(String key) throws ParseException {
        List<AbstXML> result = new ArrayList<>();
        result.add(this);
        List<AbstXML> result2 = new ArrayList<>();

        AbstLexer wr = new XSLexer().init(key);

        int dotFlag = 0;
        while (wr.hasNext() && !result.isEmpty()) {
            Word w = wr.readWord();
            switch (w.type()) {
                case WordPresets.LITERAL:
                case WordPresets.STRING: {
                    if ((dotFlag & 1) != 0) {
                        throw wr.err("缺失 '.'");
                    }

                    String x = w.val();
                    for (int i = 0; i < result.size(); i++) {
                        List<AbstXML> xmls = result.get(i).children;
                        for (int j = 0; j < xmls.size(); j++) {
                            AbstXML xml = xmls.get(j);
                            if (!xml.isString() && xml.asElement().tag.equals(x)) {
                                result2.add(xml);
                            }
                        }
                    }

                    List<AbstXML> tmp = result;
                    result = result2;
                    result2 = tmp;
                    result2.clear();

                    dotFlag |= 1;
                }
                break;
                case any: {
                    if ((dotFlag & 1) != 0) {
                        throw wr.err("缺失 '.'");
                    }

                    for (int i = 0; i < result.size(); i++) {
                        result2.addAll(result.get(i).children);
                    }

                    List<AbstXML> tmp = result;
                    result = result2;
                    result2 = tmp;
                    result2.clear();

                    dotFlag |= 1;
                }
                break;
                case dot:
                    if((dotFlag & 1) == 0) {
                        throw wr.err("未预料到的 '.'");
                    }
                    dotFlag &= ~1;
                break;
                case lsb: {
                    int prevI = -1;
                    x:
                    while (wr.hasNext()) {
                        w = wr.nextWord();
                        switch (w.type()) {
                            default:
                                throw wr.err("未预料到的 '" + w.val() + "'");
                            case WordPresets.INTEGER:
                                int i = w.number().asInt();
                                if ((dotFlag & 2) != 0) {
                                    if (i < 0) { // 5-10
                                        i = -i;
                                        for (int j = prevI; j < i; j++) {
                                            result2.add(result.get(i));
                                        }
                                        continue;
                                    } else {
                                        throw wr.err("缺失 ','");
                                    }
                                }
                                if (i < 0 || i > result.size())
                                    return Collections.emptyList();
                                result2.add(result.get(i));
                                dotFlag |= 2;
                                prevI = i;
                                break;
                            case comma:
                                if ((dotFlag & 2) == 0) {
                                    throw wr.err("未预料到的 ','");
                                }
                                dotFlag &= ~2;
                            break;
                            case rsb:
                                break x;
                        }
                    }

                    dotFlag &= ~2;
                    List<AbstXML> tmp = result;
                    result = result2;
                    result2 = tmp;
                    result2.clear();
                }
                break;
                case lmb: {
                    String name = null;
                    x:
                    while (wr.hasNext()) {
                        w = wr.nextWord();
                        switch (w.type()) {
                            default:
                                throw wr.err("未预料到的 '" + w.val() + "'");
                            case WordPresets.LITERAL:
                            case WordPresets.STRING:
                                if(name != null)
                                    throw wr.err("重复 key");
                                name = w.val();
                            break;
                            case equ:
                            case geq:
                            case gtr:
                            case lss:
                            case leq:
                            case neq:
                                if(name == null)
                                    throw wr.err("缺少 key");
                                if((dotFlag & 2) != 0)
                                    throw wr.err("缺失 ','");
                                filter(result, name, (byte) w.type(), wr);
                                dotFlag |= 2;
                            break;
                            case comma:
                                if (name == null || (dotFlag & 2) == 0) {
                                    throw wr.err("未预料到的 ','");
                                }
                                dotFlag &= ~2;
                                name = null;
                            break;
                            case rmb:
                                break x;
                        }
                    }

                    dotFlag &= ~2;
                }
                break;
                default:
                    throw wr.err("未预料到的 '" + w.val() + "'");

            }
        }
        return result;
    }

    public void iterate(Consumer<AbstXML> x) {
        x.accept(this);
        for (int i = 0; i < children.size(); i++) {
            children.get(i).iterate(x);
        }
    }

    // todo compare type
    private static void filter(List<AbstXML> dst, String name, byte type, AbstLexer wr) throws ParseException {
        List<CEntry> find = new ArrayList<>();

        boolean or = false;
        while (true) {
            Word w = wr.nextWord();
            if(!or) {
                find.add(XMLParser.of(w));
                or = true;
            } else {
                if(w.type() != AbstXML.or) {
                    wr.retractWord();
                    break;
                }
                or = false;
            }
        }

        o:
        for (int i = 0; i < dst.size(); i++) {
            XElement e = dst.get(i).asElement();
            CEntry attr = e.getAttribute(name);
            for (int j = 0; j < find.size(); j++) {
                if(/* cmp */attr.equalsTo(find.get(j))) {
                    continue o;
                }
            }
            dst.remove(i--);
        }
    }

    public XElement getByTagName(String tag) {
        for (int i = 0; i < children.size(); i++) {
            AbstXML xml = children.get(i);
            if (!xml.isString() && xml.asElement().tag.equals(tag)) {
                return xml.asElement();
            }
        }
        return null;
    }

    public List<XElement> getsByTagName(String tag) {
        List<XElement> result = new ArrayList<>();

        for (int i = 0; i < children.size(); i++) {
            AbstXML xml = children.get(i);
            if (!xml.isString() && xml.asElement().tag.equals(tag)) {
                result.add(xml.asElement());
            }
        }

        return result;
    }

    protected Map<String, CEntry> attributes;
    protected List<AbstXML> children;

    public AbstXML(Map<String, CEntry> attributes, List<AbstXML> children) {
        this.attributes = attributes;
        this.children = children;
    }

    public String asText() {
        throw new IllegalArgumentException("This is not text");
    }

    public XElement asElement() {
        throw new IllegalArgumentException("This is not an element");
    }

    public final int childElementCount() {
        return children.size();
    }

    public final CEntry getAttribute(String name) {
        return attributes.getOrDefault(name, CNull.NULL);
    }

    public final void setAttribute(String name, String value) {
        initMap();
        attributes.put(name, new CString(value));
    }

    public final void setAttribute(String name, int value) {
        initMap();
        attributes.put(name, new CInteger(value));
    }

    public final void setAttribute(String name, double value) {
        initMap();
        attributes.put(name, new CDouble(value));
    }

    public final void setAttribute(String name, boolean value) {
        initMap();
        attributes.put(name, CBoolean.valueOf(value));
    }

    @Nonnull
    public final Iterator<AbstXML> iterator() {
        return children.iterator();
    }

    public final void appendChild(@Nonnull XElement entry) {
        initList();
        children.add(entry);
    }

    @Nonnull
    public final AbstXML children(int index) {
        return children.get(index);
    }

    public final Map<String, CEntry> getAttributes() {
        initMap();
        return attributes;
    }

    protected final void initMap() {
        if (attributes == Collections.EMPTY_MAP && !isString())
            attributes = new MyHashMap<>();
    }

    public final List<AbstXML> childElements() {
        initList();
        return children;
    }

    protected final void initList() {
        if (children == Collections.EMPTY_LIST && !isString())
            children = new ArrayList<>();
    }

    public final boolean isString() {
        return getClass() == XText.class;
    }

    public final void clear() {
        children.clear();
    }

    public CEntry toJSON() {
        CMapping map = new CMapping();
        if (!attributes.isEmpty())
            map.put("A", new CMapping(attributes));
        if (!children.isEmpty()) {
            CList children = new CList(this.children.size());
            List<AbstXML> xmls = this.children;
            for (int i = 0; i < xmls.size(); i++) {
                children.add(xmls.get(i).toJSON());
            }
            map.put("C", children);
        }

        return map;
    }

    protected void read(CMapping map) {
        if (map.containsKey("A", Type.MAP)) {
            this.attributes = map.get("A").asMap().map;
        }
        if (map.containsKey("C", Type.LIST)) {
            initList();
            for (CEntry entry : map.get("C").asList()) {
                this.children.add(entry.getType() == Type.STRING ? new XText(entry.asString()) : XElement.fromJSON(entry.asMap()));
            }
        }
    }

    protected abstract StringBuilder toXML(StringBuilder sb, int depth);

    protected abstract StringBuilder toCompatXML(StringBuilder sb);
}
