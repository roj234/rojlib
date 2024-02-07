package roj.config.data;

import roj.config.serial.CVisitor;
import roj.text.CharList;

import java.util.Map;

/**
 * WIP
 * @author Roj234
 * @since 2023/5/23 0023 16:10
 */
class Attr extends Node implements Map.Entry<String, CEntry> {
	private CEntry value;

	public byte nodeType() { return ATTRIBUTE; }

	public void appendTextContent(CharList sb) { value.toJSON(sb, 0); }
	public void textContent(String str) { value = CString.valueOf(str); }

	@Override
	public void toJSON(CVisitor cc) {}
	@Override
	protected void toXML(CharList sb, int depth) {}
	@Override
	public void toCompatXML(CharList sb) {}

	@Override
	public String getKey() { return null; }
	@Override
	public CEntry getValue() { return null; }

	@Override
	public CEntry setValue(CEntry value) { return null; }
}
