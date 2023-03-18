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
	private static final short lsb = 10, rsb = 11, dot = 12, lmb = 13, rmb = 14, any = 15, equ = 16, neq = 17, or = 18, comma = 19, sep = 20, sep2 = 21, attr = 22;

	private static class XPath extends Tokenizer {
		private static final MyBitSet TOKEN_CHAR = new MyBitSet();
		private static final TrieTree<Word> TOKEN_ID = new TrieTree<>();
		static { addSymbols(TOKEN_ID, TOKEN_CHAR, 10, TextUtil.split1("( ) / [ ] * = != | , ... // @", ' ')); addWhitespace(TOKEN_CHAR); }

		{ literalEnd = TOKEN_CHAR; tokens = TOKEN_ID; }

		private List<Object> in = new SimpleList<>(), out = new SimpleList<>();

		final <T> List<T> in() { return Helpers.cast(in); }
		final <T> List<T> out() { return Helpers.cast(out); }
		final boolean swap() { List<Object> t = in; in = out; out = t; t.clear(); return in.isEmpty(); }
	}

	/**
	 * /module/component[name="NewModuleRootManager"]/content
	 * /my//data(2,5,66...99)/com[type="xyz" | 456, ntr != 233]/*
	 */
	public List<Node> querySelectorAll(String key) throws ParseException {
		XPath wr = (XPath) new XPath().init(key);
		wr.in.clear();
		wr.in.add(this);
		_query(wr);
		return wr.out();
	}
	public Node querySelector(String key) throws ParseException {
		XPath wr = (XPath) new XPath().init(key);
		wr.in.clear();
		wr.in.add(this);
		_query(wr);
		List<Node> out = wr.out();
		return out.isEmpty() ? null : out.get(0);
	}

	private static void _query(XPath wr) throws ParseException {
		while (wr.hasNext()) {
			Word w = wr.readWord();
			switch (w.type()) {
				case dot: case sep2: wr.retractWord(); _tag(wr, wr.in(), wr.out()); break;
				case lsb: _child(wr, wr.in(), wr.out()); break;
				case lmb: _filter(wr, wr.in(), wr.out()); break;
				default: wr.unexpected(w.val(), ". | ... | ( | [");
			}
			if (wr.swap()) break;
		}
	}
	private static void _tag(XPath wr, List<Node> in, List<Node> out) throws ParseException {
		List<Node> tmp;
		while (true) {
			int sep = wr.next().type();
			switch (sep) {
				case dot:
					String val = wr.next().val();
					for (int i = 0; i < in.size(); i++) {
						in.get(i).elements(val, Helpers.cast(out));
					}
					break;
				case sep2:
					val = wr.next().val();
					for (int i = 0; i < in.size(); i++) {
						in.get(i).getElementsByTagName(val, Helpers.cast(out));
					}
					break;
				default:
					wr.retractWord();
					return;
			}
		}
	}
	private static void _child(XPath wr, List<Node> in, List<Node> out) throws ParseException {
		while (true) {
			int val = wr.except(Word.INTEGER, "index").asInt();
			if (val < 0) val = in.size()+val;

			Word w = wr.next();
			if (w.type() == sep) {
				int to = wr.except(Word.INTEGER, "to_index").asInt();
				if (to < 0) to = in.size()+to;
				out.addAll(in.subList(val, to));
				w = wr.next();
			} else {
				out.add(in.get(val));
			}

			if (w.type() == rsb) return;
			else if (w.type() != comma) wr.unexpected(w.val(), "index | comma | )");
		}
	}
	private static void _filter(XPath wr, List<Node> in, List<Node> out) throws ParseException {
		MyHashSet<Node> _out = new MyHashSet<>();

		while (true) {
			String key = wr.next().val();

			Word w = wr.next();
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
		}

		out.addAll(_out);
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
		return null;
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
