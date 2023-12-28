package roj.config.data;

import roj.collect.MyBitSet;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.collect.TrieTree;
import roj.config.ParseException;
import roj.config.XMLParser;
import roj.config.serial.CVisitor;
import roj.config.word.Tokenizer;
import roj.config.word.Word;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * XML Value Base
 *
 * @author Roj234
 * @since 2020/10/31 15:28
 */
public abstract class Node {
	// region simple xpath-like selector language
	private static final short lsb = 10, rsb = 11, child = 12, any_child = 13, lmb = 14, rmb = 15, any = 16, equ = 17, neq = 18, or = 19, comma = 20, range = 21, attr = 22;
	private static final MyBitSet XPATH_TOKEN_CHAR = new MyBitSet();
	private static final TrieTree<Word> XPATH_TOKEN_ID = new TrieTree<>();
	static { Tokenizer.addSymbols(XPATH_TOKEN_ID, XPATH_TOKEN_CHAR, 10, TextUtil.split1("( ) / // [ ] * = != | , ... @", ' ')); Tokenizer.addWhitespace(XPATH_TOKEN_CHAR); }

	/**
	 * /module/component[name="NewModuleRootManager"]/content
	 * /my//data(2,5,66...99)/com[type="xyz" | 456, ntr != 233]/*
	 */
	public List<Node> querySelectorAll(String key) {
		Tokenizer x = new Tokenizer().defaultC2C(3).tokenIds(XPATH_TOKEN_ID).literalEnd(XPATH_TOKEN_CHAR).init(key);
		try {
			return _query(x, SimpleList.asModifiableList(this));
		} catch (ParseException e) {
			throw new IllegalArgumentException(key, e);
		}
	}
	public Node querySelector(String key) {
		List<Node> out = querySelectorAll(key);
		return out.isEmpty() ? null : out.get(0);
	}

	private static List<Node> _query(Tokenizer wr, List<Node> in) throws ParseException {
		List<Node> out = new SimpleList<>();
		Word w = wr.next();
		while (true) {
			switch (w.type()) {
				case child: {
					String val = wr.next().val();
					for (int i = 0; i < in.size(); i++)
						in.get(i).elements(val, Helpers.cast(out));
				}
				break;
				case any_child: {
					String val = wr.next().val();
					for (int i = 0; i < in.size(); i++)
						in.get(i).getElementsByTagName(val, Helpers.cast(out));
				}
				break;
				case lsb:
					while (true) {
						int val = wr.except(Word.INTEGER, "index").asInt();
						if (val < 0) val = in.size()+val;

						w = wr.next();
						if (w.type() == range) {
							int to = wr.except(Word.INTEGER, "to_index").asInt();
							if (to < 0) to = in.size()+to;
							out.addAll(in.subList(val, to));
							w = wr.next();
						} else {
							out.add(in.get(val));
						}

						if (w.type() == rsb) break;
						if (w.type() != comma) wr.unexpected(w.val(), "index | comma | )");
					}
				break;
				case lmb:
					MyHashSet<Node> _out = new MyHashSet<>();

					while (true) {
						String key = wr.next().val();

						w = wr.next();
						int operator = w.type();
						if (operator != equ && operator != neq) wr.unexpected(w.val(), "== | !=");

						boolean bopr = operator == equ;

						do {
							CEntry of = XMLParser.of(wr.next());
							for (int i = 0; i < in.size(); i++) {
								if (of.isSimilar(in.get(i).attr(key)) == bopr) {
									_out.add(in.get(i));
								}
							}

							w = wr.next();
						} while (w.type() == or);

						if (w.type() == rmb) break;
						if (w.type() != comma) wr.unexpected(w.val(), "comma | right_square_bracket");

						in.clear();
						in.addAll(_out);
						_out.clear();
					}

					out.addAll(_out);
				break;
				default: wr.unexpected(w.val(), ". | ... | ( | [");
			}

			if (out.isEmpty() || (w = wr.next()).type() == Word.EOF) break;

			List<Node> tmp = in;
			in = out;
			out = tmp;
			out.clear();
		}
		return out;
	}

	// endregion

	protected Node() {}

	public static final byte ELEMENT = 0, TEXT = 1, CDATA = 2, COMMENT = 3, ATTRIBUTE = 4;
	public abstract byte nodeType();
	public Element asElement() { throw new IllegalArgumentException("类型不是元素"); }
	public String textContent() {
		CharList sb = IOUtil.ddLayeredCharBuf();
		appendTextContent(sb);
		return sb.toStringAndFree();
	}
	public abstract void appendTextContent(CharList sb);
	public abstract void textContent(String str);

	// region attr
	public CEntry attr(String v) { return CNull.NULL;  }
	public void attr(String k, CEntry v) { throw new UnsupportedOperationException(); }

	public Map<String, CEntry> attributes() { return Collections.emptyMap(); }
	public Map<String, CEntry> attributesForRead() { return Collections.emptyMap(); }
	// endregion
	// region children
	public int size() { return 0; }
	public void add(@Nonnull Node node) { throw new UnsupportedOperationException(); }
	@Nonnull
	public Node child(int index) { throw new ArrayIndexOutOfBoundsException(index); }
	public List<Node> _childNodes() { return Collections.emptyList(); }
	public List<Node> children() { return Collections.emptyList(); }
	public void clear() {}
	// endregion
	// region children-EX
	public final Node firstChild() { List<Node> l = children(); return l.isEmpty() ? null : l.get(0); }
	public final Node lastChild() { List<Node> l = children(); return l.isEmpty() ? null : l.get(l.size()-1); }
	public final void insertBefore(Node node, Node ref) {
		List<Node> nodes = children();
		int i = nodes.indexOf(ref);
		nodes.add(i, node);
	}
	public final void replaceChild(Node from, Node to) {
		List<Node> nodes = children();
		nodes.set(nodes.indexOf(from), to);
	}
	public final boolean remove(Node node) { return children().remove(node); }

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
	// endregion
	// region children-EX-EX (field)
	public Node parentNode() { throw new UnsupportedOperationException("Not implemented"); }
	public Node previousSibling() { throw new UnsupportedOperationException("Not implemented"); }
	public Node nextSibling() { throw new UnsupportedOperationException("Not implemented"); }
	public Document owner() { throw new UnsupportedOperationException("Not implemented"); }
	// endregion

	public abstract void toJSON(CVisitor cc);

	@Override
	public final String toString() {
		CharList sb = IOUtil.getSharedCharBuf();
		toXML(sb, 0);
		return sb.toString();
	}
	public CharList toCompatXML() {
		CharList sb = new CharList(128);
		toCompatXML(sb);
		return sb;
	}
	protected abstract void toXML(CharList sb, int depth);
	public abstract void toCompatXML(CharList sb);
}