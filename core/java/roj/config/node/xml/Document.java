package roj.config.node.xml;

import roj.config.node.ConfigValue;
import roj.text.CharList;

import java.util.Collections;
import java.util.Map;

/**
 * @author Roj234
 * @since 2021/5/31 21:17
 */
public final class Document extends Element {
	public Document() { super("?xml"); }

	public Text createTextNode(String text) {return new Text(text, TEXT);}
	public Element createElement(String tag) {return new Element(tag);}

	@Override
	protected Map<String, ConfigValue> attributesForWrite() {
		if (attributes == null) throw new UnsupportedOperationException("Headless document");
		return super.attributesForWrite();
	}

	@Override
	public Map<String, ConfigValue> attributes() {return attributes == null ? Collections.emptyMap() : attributes;}

	protected void toXML(CharList sb, int depth) {
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