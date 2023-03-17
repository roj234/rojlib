package roj.config.data;

import roj.config.serial.CVisitor;
import roj.net.http.HttpUtil;
import roj.text.CharList;

/**
 * @author Roj234
 * @since 2020/11/22 15:53
 */
public final class Text extends Node {
	public String value;
	public byte nodeType;

	public Text(String text) { value = text; }
	public Text(String text, byte type) { value = text; nodeType = type; }

	public byte nodeType() { return nodeType; }

	public String textContent() { return nodeType == COMMENT ? null : value; }
	public void appendTextContent(CharList sb) { if (nodeType != COMMENT) sb.append(value); }
	public void textContent(String str) { if (nodeType != COMMENT) value = str; }

	public void toJSON(CVisitor cc) { if (nodeType == COMMENT) cc.comment(value); else cc.value(value); }

	public void toXML(CharList sb, int depth) { toCompatXML(sb); }
	public void toCompatXML(CharList sb) {
		switch (nodeType) {
			case CDATA: sb.append("<![CDATA[").append(value).append("]]>"); break;
			case COMMENT: sb.append("<!--").append(value).append("-->"); break;
			default: HttpUtil.htmlspecial(sb, value); break;
		}
	}
}
