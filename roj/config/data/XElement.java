package roj.config.data;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: XElement.java
 */
public final class XElement extends AbstXML {
    public String namespace, tag;

    public XElement(String tag) {
        super(Collections.emptyMap(), Collections.emptyList());
        this.tag = tag;
    }

    public XElement(String tag, Map<String, ConfEntry> attributes, List<AbstXML> children) {
        super(attributes, children);
        this.tag = tag;
    }

    @Override
    public XElement asElement() {
        return this;
    }

    public String namespace() {
        if(namespace == null) {
            int i = tag.indexOf(':');
            if (i == -1) {
                namespace = "";
            } else {
                namespace = tag.substring(0, i);
            }
        }
        return namespace;
    }

    @Override
    public String toString() {
        return toXML(new StringBuilder(128), 0).toString();
    }

    public StringBuilder toXML(StringBuilder sb, int depth) {
        sb.append('<').append(tag);
        if (!attributes.isEmpty()) {
            for (Map.Entry<String, ConfEntry> entry : attributes.entrySet()) {
                sb.append(' ').append(entry.getKey()).append('=');
                entry.getValue().toJSON(sb, 0);
            }
        }
        sb.append('>');

        int depth1 = depth + 4;
        if (!children.isEmpty()) {
            int i = 0, s = children.size();
            for (AbstXML entry : children) {
                if (!entry.isString() && (i == 0 || !children.get(i - 1).isString())) {
                    System.out.println("Adding " + entry.toCompatXML(new StringBuilder()));
                    sb.append('\n');
                    for (int j = 0; j < depth1; j++) {
                        sb.append(' ');
                    }
                }
                entry.toXML(sb, depth1);
                i++;
            }
            if (!children.get(children.size() - 1).isString()) {
                sb.append('\n');
                for (int j = 0; j < depth; j++) {
                    sb.append(' ');
                }
            }
        }

        return sb.append('<').append('/').append(tag).append('>');
    }

    public StringBuilder toCompatXML(StringBuilder sb) {
        sb.append('<').append(tag);
        if (!attributes.isEmpty()) {
            for (Map.Entry<String, ConfEntry> entry : attributes.entrySet()) {
                sb.append(' ').append(entry.getKey()).append('=');
                entry.getValue().toJSON(sb, 0);
            }
        }
        if (children.isEmpty()/* && value == null*/)
            sb.append('/');
        sb.append('>');

        if (!children.isEmpty()) {
            for (AbstXML entry : children) {
                entry.toCompatXML(sb);
            }
            sb.delete(sb.length() - 1, sb.length()).append('\n');
        } else {
            return sb;
        }
        return sb.append('<').append('/').append(tag).append('>');
    }

    public void addAll(XElement list) {
        initList();
        this.children.addAll(list.children);
        initMap();
        this.attributes.putAll(list.attributes);
        //this.value = list.value;
    }

    public CMapping toJSON() {
        CMapping map = super.toJSON();
        map.put("_type_", "XML.Elem");
        map.put("tag", tag);

        return map;
    }

    public static XElement fromJSON(CMapping map) {
        XElement element = new XElement(map.getString("tag"));
        element.read(map);
        return element;
    }
}
