package roj.config.node.xml;

import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnmodifiableView;
import roj.collect.ArrayList;
import roj.collect.BitSet;
import roj.collect.HashSet;
import roj.collect.TrieTree;
import roj.config.ValueEmitter;
import roj.config.XmlParser;
import roj.config.node.*;
import roj.io.IOUtil;
import roj.text.*;
import roj.util.Helpers;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * XML Value Base
 *
 * @author Roj234
 * @since 2020/10/31 15:28
 */
public abstract class Node extends ConfigValue {
	// region XPath的子集
	private static final short lsb = 10, rsb = 11, child = 12, any_child = 13, lmb = 14, rmb = 15, any = 16, equ = 17, neq = 18, or = 19, comma = 20, range = 21, attr = 22, xstartwith = 23;
	private static final BitSet XPATH_TOKEN_CHAR = new BitSet();
	private static final TrieTree<Token> XPATH_TOKEN_ID = new TrieTree<>();
	static { Tokenizer.addSymbols(XPATH_TOKEN_ID, XPATH_TOKEN_CHAR, 10, TextUtil.split1("( ) / // [ ] * = != | , ... @ ^=", ' ')); Tokenizer.addWhitespace(XPATH_TOKEN_CHAR); }
	private static List<Node> xpathQuery(Tokenizer wr, List<Node> in) throws ParseException {
		List<Node> out = new ArrayList<>();
		Token w = wr.next();
		while (true) {
			switch (w.type()) {
				case child: {
					String val = wr.next().text();
					for (int i = 0; i < in.size(); i++)
						in.get(i).elements(val, Helpers.cast(out));
				}
				break;
				case any_child: {
					String val = wr.next().text();
					for (int i = 0; i < in.size(); i++)
						in.get(i).getElementsByTagName(val, Helpers.cast(out));
				}
				break;
				case lsb:
					while (true) {
						int val = wr.except(Token.INTEGER, "index").asInt();
						if (val < 0) val = in.size()+val;

						w = wr.next();
						if (w.type() == range) {
							int to = wr.except(Token.INTEGER, "to_index").asInt();
							if (to < 0) to = in.size()+to;
							out.addAll(in.subList(val, to));
							w = wr.next();
						} else {
							out.add(in.get(val));
						}

						if (w.type() == rsb) break;
						if (w.type() != comma) wr.unexpected(w.text(), "index | comma | )");
					}
				break;
				case lmb:
					HashSet<Node> _out = new HashSet<>();

					while (true) {
						String key = wr.next().text();
						int operator = wr.next().type();

						do {
							ConfigValue of = XmlParser.attrVal(wr.next());
							for (int i = 0; i < in.size(); i++) {
								ConfigValue cmp = in.get(i).attr(key);
								boolean match = switch (operator) {
									case equ -> of.contentEquals(cmp);
									case neq -> !of.contentEquals(cmp);
									case xstartwith -> cmp.asString().startsWith(of.asString());
									default -> false;
								};
								if (match) _out.add(in.get(i));
							}

							w = wr.next();
						} while (w.type() == or);

						if (w.type() == rmb) break;
						if (w.type() != comma) wr.unexpected(w.text(), "comma | right_square_bracket");

						in.clear();
						in.addAll(_out);
						_out.clear();
					}

					out.addAll(_out);
				break;
				default: wr.unexpected(w.text(), ". | ... | ( | [");
			}

			if (out.isEmpty() || (w = wr.next()).type() == Token.EOF) break;

			List<Node> tmp = in;
			in = out;
			out = tmp;
			out.clear();
		}
		return out;
	}

	// endregion

	protected Node() {}

	@Override public final Type getType() {return Type.OTHER;}
	@Override public final char dataType() {return (char) nodeType();}
	@Override public Object raw() {return this;}
	@Override public boolean mayCastTo(Type o) {return o == Type.STRING || o == Type.MAP || o == Type.LIST;}

	@Override public final String asString() {return textContent();}
	@Override public final MapValue asMap() {return new MapValue(attributesWritable());}
	@Override public final ListValue asList() {return new ListValue(childNodes());}

	public static final byte ELEMENT = 0, TEXT = 's', CDATA = 2, COMMENT = 3, ATTRIBUTE = 4;
	public abstract byte nodeType();
	public Element asElement() { throw new IllegalArgumentException("类型不是元素"); }
	public String textContent() {
		CharList sb = new CharList();
		getTextContent(sb);
		return sb.toStringAndFree();
	}
	public abstract void getTextContent(CharList sb);
	public abstract void textContent(String str);

	// region attr
	public ConfigValue attr(String v) { return NullValue.NULL;  }
	public void attr(String k, ConfigValue v) { throw new UnsupportedOperationException(); }

	@UnmodifiableView
	public Map<String, ConfigValue> attributes() {return Collections.emptyMap();}
	public Map<String, ConfigValue> attributesWritable() {return Collections.emptyMap();}
	// endregion
	// region children
	public int size() { return 0; }
	public void add(@NotNull Node node) { throw new UnsupportedOperationException(); }
	@NotNull
	public Node child(int index) { throw new ArrayIndexOutOfBoundsException(index); }

	@UnmodifiableView
	public List<Node> children() { return Collections.emptyList(); }
	public List<Node> childNodes() { return Collections.emptyList(); }

	public void clear() {}
	// endregion
	//region 当前级别子节点操作
	public final Node firstChild() { List<Node> l = children(); return l.isEmpty() ? null : l.get(0); }
	public final Node lastChild() { List<Node> l = children(); return l.isEmpty() ? null : l.get(l.size()-1); }
	public final void insertBefore(Node node, Node ref) {
		List<Node> nodes = childNodes();
		int i = nodes.indexOf(ref);
		nodes.add(i, node);
	}
	public final void replaceChild(Node from, Node to) {
		List<Node> nodes = childNodes();
		nodes.set(nodes.indexOf(from), to);
	}
	public final boolean remove(Node node) {
		List<Node> children = children();
		int i = children.indexOf(node);
		if (i < 0) return false;
		childNodes().remove(i);
		return true;
	}

	public final Element element(String tag) {
		List<Node> children = children();
		for (int i = 0; i < children.size(); i++) {
			Node xml = children.get(i);
			if (tag.equals("*") || (xml.nodeType() == ELEMENT && xml.asElement().tag.equals(tag))) {
				return xml.asElement();
			}
		}
		return Helpers.maybeNull();
	}
	public final List<Element> elements(String tag, List<Element> result) {
		List<Node> children = children();
		for (int i = 0; i < children.size(); i++) {
			Node xml = children.get(i);
			if (tag.equals("*") || (xml.nodeType() == ELEMENT && xml.asElement().tag.equals(tag))) {
				result.add(xml.asElement());
			}
		}

		return result;
	}
	//endregion
	//region 递归操作
	public final Element getElementByTagName(String tag) {
		List<Node> children = children();
		for (int i = 0; i < children.size(); i++) {
			Node xml = children.get(i);
			if (xml.nodeType() == ELEMENT) {
				Element elem = xml.asElement();
				if (elem.tag.equals(tag)) return elem;
				elem = elem.getElementByTagName(tag);
				if (elem != null) return elem;
			}
		}
		return null;
	}
	public final List<Element> getElementsByTagName(String tag) {
		List<Element> result = new ArrayList<>();
		getElementsByTagName(tag, result);
		return result;
	}
	public final void getElementsByTagName(String tag, List<Element> collector) {
		List<Node> children = children();
		for (int i = 0; i < children.size(); i++) {
			Node xml = children.get(i);
			if (xml.nodeType() == ELEMENT) {
				Element elem = xml.asElement();
				if (elem.tag.equals(tag)) collector.add(elem);
				elem.getElementsByTagName(tag, collector);
			}
		}
	}

	/**
	 * /module/component[name="NewModuleRootManager"]/content
	 * /my//data(2,5,66...99)/com[type="xyz" | 456, ntr != 233]/*
	 */
	public List<Node> querySelectorAll(@Language("XPath") String path) {
		Tokenizer x = new Tokenizer().tokenIds(XPATH_TOKEN_ID).literalEnd(XPATH_TOKEN_CHAR).init(path);
		try {
			return xpathQuery(x, ArrayList.asModifiableList(this));
		} catch (ParseException e) {
			throw new IllegalArgumentException(path, e);
		}
	}
	public Node querySelector(@Language("XPath") String path) {
		List<Node> out = querySelectorAll(path);
		return out.isEmpty() ? null : out.get(0);
	}

	public void forEach(Consumer<Node> consumer) {
		consumer.accept(this);
		var nodes = childNodes();
		for (int i = 0; i < nodes.size(); i++) {
			nodes.get(i).forEach(consumer);
		}
	}
	//endregion

	public abstract void accept(ValueEmitter cc);

	@Override
	public final String toString() {
		CharList sb = IOUtil.getSharedCharBuf();
		toXML(sb, 0);
		return sb.toString();
	}
	public final CharList toCompatXML() {
		CharList sb = new CharList(128);
		toCompatXML(sb);
		return sb;
	}
	public final void toXML(CharList sb) {toXML(sb, 0);}

	protected abstract void toXML(CharList sb, int depth);
	public abstract void toCompatXML(CharList sb);
}