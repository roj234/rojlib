package roj.config.data;

import roj.text.CharList;

import java.util.Map;

/**
 * @author Roj234
 * @since 2021/5/31 21:17
 */
public final class XHeader extends XElement {
	public XHeader() {
		super("?xml");
	}

	public void toXML(CharList sb, int depth) {
		if (depth != 0) throw new UnsupportedOperationException("Xml header must on top");

		if (attributes != null) {
			sb.append("<?xml");
			if (!attributes.isEmpty()) {
				for (Map.Entry<String, CEntry> entry : attributes.entrySet()) {
					sb.append(' ').append(entry.getKey()).append('=');
					entry.getValue().toJSON(sb, 0);
				}
			}
			sb.append('?').append('>');
		}

		if (!children.isEmpty()) {
			sb.append('\n');
			for (int i = 0; i < children.size(); i++) {
				children.get(i).toXML(sb, 0);
			}
			sb.delete(sb.length() - 1, sb.length());
		}
	}

	public void toCompatXML(CharList sb) {
		if (attributes != null) {
			sb.append("<?xml");
			if (!attributes.isEmpty()) {
				for (Map.Entry<String, CEntry> entry : attributes.entrySet()) {
					sb.append(' ').append(entry.getKey()).append('=');
					entry.getValue().toJSON(sb, 0);
				}
			}
			sb.append('?').append('>');
		}

		if (!children.isEmpty()) {
			for (int i = 0; i < children.size(); i++) {
				children.get(i).toCompatXML(sb);
			}
		}
	}

	@Override
	public CMapping toJSON() {
		CMapping map = super.toJSON();
		map.remove("I");
		return map;
	}
}
