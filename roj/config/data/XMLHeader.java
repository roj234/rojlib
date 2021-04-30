package roj.config.data;

import roj.collect.MyHashMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * This file is a part of MI <br>
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * @author Roj234
 * Filename: XMLHeader.java
 */
public final class XMLHeader extends AbstXML {
    public XMLHeader() {
        super(new MyHashMap<>(), new ArrayList<>());
    }

    public XMLHeader(Map<String, ConfEntry> attributes, List<AbstXML> children) {
        super(attributes, children);
    }

    @Override
    public String toString() {
        return toXML(new StringBuilder(128)).toString();
    }

    public StringBuilder toXML(StringBuilder sb) {
        sb.append("<?xml");
        if (!attributes.isEmpty()) {
            for (Map.Entry<String, ConfEntry> entry : attributes.entrySet()) {
                sb.append(' ').append(entry.getKey()).append('=');
                entry.getValue().toJSON(sb, 0);
            }
        }
        sb.append('>');
        if (!children.isEmpty()) {
            sb.append('\n');
            for (AbstXML entry : children) {
                entry.toXML(sb, 0).append('\n');
            }
            sb.delete(sb.length() - 1, sb.length());
        }
        return sb;
    }

    public StringBuilder toCompatXML(StringBuilder sb) {
        sb.append("<?xml");
        if (!attributes.isEmpty()) {
            for (Map.Entry<String, ConfEntry> entry : attributes.entrySet()) {
                sb.append(' ').append(entry.getKey()).append('=');
                entry.getValue().toJSON(sb, 0);
            }
        }
        sb.append('?').append('>');
        if (!children.isEmpty()) {
            for (AbstXML entry : children) {
                entry.toCompatXML(sb);
            }
            sb.delete(sb.length() - 1, sb.length());
        }
        return sb;
    }

    public CMapping toJSON() {
        CMapping map = super.toJSON();
        map.put("__type__", "XML.Header");

        return map;
    }

    @Override
    protected StringBuilder toXML(StringBuilder sb, int depth) {
        throw new UnsupportedOperationException();
    }

    public static XMLHeader fromJSON(CMapping map) {
        XMLHeader header = new XMLHeader();
        header.read(map);
        return header;
    }
}
