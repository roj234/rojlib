package roj.config.node.xml;

import org.jetbrains.annotations.NotNull;
import roj.collect.ArrayList;
import roj.collect.HashMap;
import roj.config.ValueEmitter;
import roj.config.node.ConfigValue;
import roj.config.node.NullValue;
import roj.config.node.Type;
import roj.text.CharList;
import roj.text.Tokenizer;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2021/5/31 21:17
 */
public class Element extends Node {
	public String tag;
	public boolean isVoid;

	public Element(String tag) { this.tag = tag; }

	public byte nodeType() { return ELEMENT; }
	public Element asElement() { return this; }
	public String textContent() {
		if (children.size() > 1 || isBreak()) return super.textContent();
		return children.isEmpty()?"":children.get(0).textContent();
	}
	public void getTextContent(CharList sb) {
		if (isBreak()) sb.append('\n');
		for (int i = 0; i < children.size(); i++)
			sb.append(children.get(i).textContent());
	}
	private boolean isBreak() {return isVoid && tag.equals("br");}

	public void textContent(String str) {
		childNodes().clear();
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

	public Node voidElement() {
		isVoid = true;
		return this;
	}

	// region 属性
	Map<String, ConfigValue> attributes = Collections.emptyMap();

	public Element attr(String name, String value) { attributesWritable().put(name, valueOf(value)); return this; }
	public Element attr(String name, int value) { attributesWritable().put(name, valueOf(value)); return this; }
	public Element attr(String name, double value) { attributesWritable().put(name, valueOf(value)); return this; }
	public Element attr(String name, boolean value) { attributesWritable().put(name, valueOf(value)); return this; }
	public ConfigValue attr(String name) { return attributes.getOrDefault(name, NullValue.NULL); }
	public void attr(String k, ConfigValue v) { attributesWritable().put(k, v); }

	@Override
	public Map<String, ConfigValue> attributesWritable() {
		if (!(attributes instanceof HashMap))
			attributes = new HashMap<>(attributes);
		return attributes;
	}
	@Override
	public Map<String, ConfigValue> attributes() { return attributes; }
	// endregion
	// region 子节点
	List<Node> children = Collections.emptyList();

	@Override
	public int size() { return children.size(); }
	@Override
	public void add(@NotNull Node node) {
		if (children == Collections.EMPTY_LIST) children = Collections.singletonList(node);
		else childNodes().add(node);
	}
	@Override
	@NotNull
	public Node child(int index) { return children.get(index); }
	@Override
	public List<Node> childNodes() {
		if (!(children instanceof ArrayList)) {
			ArrayList<Node> s = ArrayList.hugeCapacity(children.size());
			s.addAll(children);
			children = s;
		}
		return children;
	}
	@Override
	public List<Node> children() { return children; }
	@Override
	public void clear() {
		if (children instanceof ArrayList) children.clear();
		else children = Collections.emptyList();
	}
	// endregion

	protected void toXML(CharList sb, int depth) {
		writeTag(sb);

		if (isVoid && children.isEmpty()) {
			sb.append("!DOCTYPE".equals(tag) ? ">" : " />");
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

		if (isVoid && children.isEmpty()) {
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
			for (Map.Entry<String, ConfigValue> entry : attributes.entrySet()) {
				sb.append(' ');
				if (entry.getValue().getType() != Type.NULL) {
					sb.append(entry.getKey()).append('=');
					entry.getValue().toJSON(sb, 0);
				} else {
					String key = entry.getKey();
					if (key.contains(" ") || key.contains("\"") || key.contains("'") || key.contains("<") || key.contains("&")) {
						Tokenizer.escape(sb.append('"'), key).append('"');
					} else {
						sb.append(key);
					}
				}
			}
		}
	}

	public void accept(ValueEmitter cc) {
		int size = 0;
		if (!tag.equals("?xml")) size++;
		if (!attributes.isEmpty()) size++;
		if (!children.isEmpty()) size++;
		cc.emitList(size);

		if (!tag.equals("?xml")) cc.emit(tag);
		if (!attributes.isEmpty()) {
			cc.emitMap(attributes.size());
			for (Map.Entry<String, ConfigValue> entry : attributes.entrySet()) {
				cc.key(entry.getKey());
				entry.getValue().accept(cc);
			}
			cc.pop();
		}
		if (!children.isEmpty()) {
			if (children.size() == 1 && children.get(0).nodeType() == TEXT) {
				children.get(0).accept(cc);
			} else {
				cc.emitList(children.size());
				for (int i = 0; i < children.size(); i++) {
					children.get(i).accept(cc);
				}
				cc.pop();
			}
		}

		cc.pop();
	}
}