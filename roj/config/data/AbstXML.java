package roj.config.data;

import roj.collect.MyHashMap;
import roj.config.ParseException;
import roj.config.word.Lexer;
import roj.config.word.Word;
import roj.config.word.WordPresets;
import roj.math.MathUtils;
import roj.text.CharList;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/10/31 15:28
 */
public abstract class AbstXML implements Iterable<AbstXML> {
    private static class XSLexer extends Lexer {
        @Override
        protected Word formAlphabetClip(CharList temp) {
            String s = temp.toString();

            short id = WordPresets.LITERAL;
            if ("child".equals(s)) {
                id = 598;
            }

            return formClip(id, s);
        }

        @Override
        protected Word readSpecial() throws ParseException {
            char c = next();

            short id;
            switch (c) {
                case ')':
                    id = 601;
                    break;
                case '(':
                    id = 600;
                    break;
                case '.':
                    id = 599;
                    break;
                default:
                    throw err("无效字符 '" + c + '\'');
            }

            return formClip(id, String.valueOf(c));
        }
    }

    // child(5).child(2).child(3)
    public static AbstXML getXS(AbstXML xml, String key) throws ParseException {
        Lexer l = new XSLexer().init(key);

        int except = 0;
        while (l.hasNext()) {
            Word w = l.readWord();
            switch (w.type()) {
                case 598: {
                    if(except == 1)
                        throw l.err("exception .");
                    w = l.readWord();
                    if(w.type() != 600)
                        throw l.err("excepting (");
                    w = l.readWord();
                    if(w.type() != WordPresets.INTEGER)
                        throw l.err("excepting integer");
                    int id = MathUtils.parseInt(w.number().val());
                    final List<AbstXML> children = xml.children;
                    if(children.size() <= id)
                        throw l.err("not enough element(" + children.size() + ")");
                    xml = children.get(id);
                    w = l.readWord();
                    if(w.type() != 601)
                        throw l.err("excepting )");
                    except = 1;
                }
                break;
                case 599:
                    if(except == 0)
                        throw l.err("exception child");
                    except = 0;
                default:
                    throw l.err("exception " + (except == 0 ? "child" : "."));

            }
        }
        return xml;
    }

    protected Map<String, ConfEntry> attributes;
    protected List<AbstXML> children;

    public AbstXML(Map<String, ConfEntry> attributes, List<AbstXML> children) {
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

    public final ConfEntry getAttribute(String name) {
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

    public final Map<String, ConfEntry> getAttributes() {
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

    public CMapping toJSON() {
        CMapping map = new CMapping();
        if (!attributes.isEmpty())
            map.put("attributes", new CMapping(attributes));
        if (!children.isEmpty())
            map.put("children", writeChildren());

        return map;
    }

    protected final CList writeChildren() {
        CList children = new CList(this.children.size());
        for (AbstXML element : this.children) {
            children.add(element.toJSON());
        }

        return children;
    }

    protected void read(CMapping map) {
        if (map.containsKey("attributes", Type.MAP)) {
            this.attributes = map.get("attributes").asMap().map;
        }
        if (map.containsKey("children", Type.LIST)) {
            initList();
            for (ConfEntry entry : map.get("children").asList()) {
                this.children.add(addChildren(entry.asMap()));
            }
        }
    }

    protected AbstXML addChildren(CMapping map) {
        switch (map.getString("_type_")) {
            case "XML.Text":
                return XText.fromJSON(map);
            case "XML.Elem":
                return XElement.fromJSON(map);
            default:
                throw new IllegalStateException("Unexpected value: " + map.getString("_type_"));
        }
    }

    protected abstract StringBuilder toXML(StringBuilder sb, int depth);

    protected abstract StringBuilder toCompatXML(StringBuilder sb);
}
