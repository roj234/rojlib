package roj.config.data;

import java.util.Collections;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/11/22 15:53
 */
public final class XText extends AbstXML {
    public String value;

    public XText(String value) {
        super(Collections.emptyMap(), Collections.emptyList());
        this.value = value;
    }

    @Override
    public String asText() {
        return value;
    }

    public CMapping toJSON() {
        CMapping map = new CMapping();
        map.put("_type_", "XML.Text");
        map.put("text", value);

        return map;
    }

    @Override
    protected StringBuilder toXML(StringBuilder sb, int depth) {
        return toCompatXML(sb);
    }

    @Override
    protected StringBuilder toCompatXML(StringBuilder sb) {
        return !value.startsWith("<![CDATA[") && value.indexOf('<') >= 0 ? sb.append("<![CDATA[").append(value).append("]]>") : sb.append(value);
    }

    public static XText fromJSON(CMapping mapping) {
        return new XText(mapping.getString("text"));
    }
}
