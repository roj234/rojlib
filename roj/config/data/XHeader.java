package roj.config.data;

import roj.text.CharList;

/**
 * @author Roj234
 * @since 2021/5/31 21:17
 */
public final class XHeader extends XElement {
	public XHeader() { super("?xml"); }

	public void toXML(CharList sb, int depth) {
		if (depth != 0) throw new UnsupportedOperationException("Xml header must on top");

		if (attributes != null) {
			writeTag(sb);
			sb.append("?>");
		}

		if (!children.isEmpty()) {
			sb.append('\n');
			for (int i = 0; i < children.size(); i++) {
				children.get(i).toXML(sb, 0);
			}
		}
	}

	public void toCompatXML(CharList sb) {
		if (attributes != null) {
			writeTag(sb);
			sb.append("?>");
		}

		for (int i = 0; i < children.size(); i++) {
			children.get(i).toCompatXML(sb);
		}
	}

	public void headless() { attributes = null; }
}
