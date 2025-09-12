package roj.config.node.xml;

import roj.config.ValueEmitter;
import roj.text.CharList;
import roj.text.HtmlEntities;

/**
 * @author Roj234
 * @since 2020/11/22 15:53
 */
public final class Text extends Node {
	public String value;
	public byte nodeType;

	public Text(String text) { value = text; nodeType = TEXT; }
	public Text(String text, byte type) { value = text; nodeType = type; }

	public byte nodeType() { return nodeType; }

	public String textContent() { return nodeType == COMMENT ? null : value; }
	public void getTextContent(CharList sb) { if (nodeType != COMMENT) sb.append(value); }
	public void textContent(String str) { if (nodeType != COMMENT) value = str; }

	public void accept(ValueEmitter cc) { if (nodeType == COMMENT) cc.comment(value); else cc.emit(value); }

	public void toXML(CharList sb, int depth) {
		switch (nodeType) {
			case CDATA: sb.append("<![CDATA[").append(value).append("]]>"); break;
			case COMMENT: sb.append("<!--").append(value).append("-->"); break;
			default: HtmlEntities.escapeHtml(sb, value); break;
		}
	}
	public void toCompatXML(CharList sb) {
		switch (nodeType) {
			case CDATA: sb.append("<![CDATA[").append(value).append("]]>"); break;
			case COMMENT: break; // no comment in compat XMLs
			default: HtmlEntities.escapeHtml(sb, value); break;
		}
	}
}