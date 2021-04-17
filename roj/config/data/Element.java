package roj.config.data;

import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.config.serial.CVisitor;
import roj.config.word.ITokenizer;
import roj.text.CharList;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2021/5/31 21:17
 */
public class Element extends Node {
	public String tag;
	public boolean shortTag;

	public Element(String tag) { this.tag = tag; }

	public byte nodeType() { return ELEMENT; }
	public Element asElement() { return this; }
	public String textContent() {
		if (children.size() > 1) return super.textContent();
		return children.isEmpty()?"":children.get(0).textContent();
	}
	public void appendTextContent(CharList sb) {
		for (int i = 0; i < children.size(); i++)
			sb.append(children.get(i).textContent());
	}
	public void textContent(String str) {
		_childNodes().clear();
		if (str != null) children.add(new Text(str));
	}

	public String namespace() {
		int i = tag.indexOf(':');
		return i < 0 ? "" : tag.substring(0, i);
	}
	public String qualifiedName() {
		int i = tag.indexOf(':');
		return i < 0 ? tag : tag.substring(i+1);
	}

	// region 属性
	Map<String, CEntry> attributes = Collections.emptyMap();

	public Element attr(String name, String value) { attributes().put(name, CString.valueOf(value)); return this; }
	public Element attr(String name, int value) { attributes().put(name, CInteger.valueOf(value)); return this; }
	public Element attr(String name, double value) { attributes().put(name, CDouble.valueOf(value)); return this; }
	public Element attr(String name, boolean value) { attributes().put(name, CBoolean.valueOf(value)); return this; }
	public CEntry attr(String name) { return attributes.getOrDefault(name, CNull.NULL); }
	public void attr(String k, CEntry v) { attributes().put(k, v); }

	@Override
	public Map<String, CEntry> attributes() {
		if (!(attributes instanceof MyHashMap))
			attributes = new MyHashMap<>(attributes);
		return attributes;
	}
	@Override
	public Map<String, CEntry> attributesForRead() { return attributes; }
	// endregion
	// region 子节点
	List<Node> children = Collections.emptyList();

	@Override
	public int size() { return children.size(); }
	@Override
	public void add(@Nonnull Node node) {
		if (children == Collections.EMPTY_LIST) children = Collections.singletonList(node);
		else _childNodes().add(node);
	}
	@Override
	@Nonnull
	public Node child(int index) { return children.get(index); }
	@Override
	public List<Node> _childNodes() {
		if (!(children instanceof SimpleList)) {
			SimpleList<Node> s = new SimpleList<>(children);
			children = s;
			s.capacityType = 2;
		}
		return children;
	}
	@Override
	public List<Node> children() { return children; }
	@Override
	public void clear() {
		if (children instanceof SimpleList) children.clear();
		else children = Collections.emptyList();
	}
	// endregion

	public void toXML(CharList sb, int depth) {
		writeTag(sb);

		if (shortTag && children.isEmpty()) {
			sb.append(" />");
			return;
		}

		sb.append('>');

		int depth1 = depth + 4;
		if (!children.isEmpty()) {
			for (int i = 0; i < children.size(); i++) {
				Node entry = children.get(i);
				if (entry.nodeType() != TEXT && (i == 0 || children.get(i-1).nodeType() != TEXT)) {
					sb.append('\n');
					for (int j = 0; j < depth1; j++) sb.append(' ');
				}
				entry.toXML(sb, depth1);
			}
			if (children.get(children.size() - 1).nodeType() != TEXT) {
				sb.append('\n');
				for (int j = 0; j < depth; j++)sb.append(' ');
			}
		}

		sb.append("</").append(tag).append('>');
	}

	public void toCompatXML(CharList sb) {
		writeTag(sb);

		if (shortTag && children.isEmpty()) {
			sb.append("/>");
			return;
		}

		sb.append('>');

		for (int i = 0; i < children.size(); i++) {
			children.get(i).toCompatXML(sb);
		}
		sb.append("</").append(tag).append('>');
	}

	final void writeTag(CharList sb) {
		sb.append('<').append(tag);
		if (!attributes.isEmpty()) {
			for (Map.Entry<String, CEntry> entry : attributes.entrySet()) {
				sb.append(' ');
				if (entry.getValue().getType() != Type.NULL) {
					sb.append(entry.getKey()).append('=');
					entry.getValue().toJSON(sb, 0);
				} else {
					String key = entry.getKey();
					if (key.contains(" ") || key.contains("\"") || key.contains("'") || key.contains("<") || key.contains("&")) {
						ITokenizer.addSlashes(sb.append('"'), key).append('"');
					} else {
						sb.append(key);
					}
				}
			}
		}
	}

	public void toJSON(CVisitor cc) {
		int size = 0;
		if (!tag.equals("?xml")) size++;
		if (!attributes.isEmpty()) size++;
		if (!children.isEmpty()) size++;
		cc.valueList(size);

		if (!tag.equals("?xml")) cc.value(tag);
		if (!attributes.isEmpty()) {
			cc.valueMap(attributes.size());
			for (Map.Entry<String, CEntry> entry : attributes.entrySet()) {
				cc.key(entry.getKey());
				entry.getValue().forEachChild(cc);
			}
			cc.pop();
		}
		if (!children.isEmpty()) {
			if (children.size() == 1 && children.get(0).nodeType() == TEXT) {
				children.get(0).toJSON(cc);
			} else {
				cc.valueList(children.size());
				for (int i = 0; i < children.size(); i++) {
					children.get(i).toJSON(cc);
				}
				cc.pop();
			}
		}

		cc.pop();
	}
}
