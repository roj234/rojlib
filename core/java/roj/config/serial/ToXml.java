package roj.config.serial;

import roj.collect.ArrayList;
import roj.config.data.*;
import roj.util.TypedKey;

import java.util.List;

/**
 * @author Roj234
 * @since 2022/11/15 2:07
 */
public class ToXml implements CVisitor {
	public static final TypedKey<Boolean> HEADLESS = new TypedKey<>("xml:headless");
	public static final TypedKey<Boolean> SHORT_TAG = new TypedKey<>("xml:void");
	public static final TypedKey<Boolean> CDATA = new TypedKey<>("xml:CData");

	public static final byte
		XML_BEGIN    = 0,
		XML_NAME     = 1,
		XML_ATTR     = 2,
		XML_CHILDREN = 3,
		XML_CHILD    = 4,
		XML_ATTR_KEY = 5,
		XML_ATTR_VAL = 6;

	private final List<Element> stack = new ArrayList<>();
	private Element stackBottom = new Document();

	private String key;

	private byte state;

	private int maxDepth = 100;

	public final void value(String s) {
		switch (state) {
			case XML_CHILD: stackBottom.add(new Text(s)); break;
			case XML_NAME:
				state = XML_ATTR;
				if (stack.size() >= maxDepth) throw new IllegalStateException("栈溢出: 最大深度是 "+maxDepth);
				stack.add(stackBottom);
				stackBottom.add(stackBottom = createElement(s));
				break;
			default: add(CEntry.valueOf(s)); break;
		}
	}
	public final void value(int i) { add(CEntry.valueOf(i)); }
	public final void value(long i) { add(CEntry.valueOf(i)); }
	public final void value(double i) { add(CEntry.valueOf(i));}
	public final void value(boolean b) { add(CEntry.valueOf(b)); }
	public final void valueNull() { add(CNull.NULL); }
	private void add(CEntry v) {
		if (state == XML_CHILD) {
			stackBottom.add(new Text(v.asString()));
			return;
		}

		_state(XML_ATTR_VAL, XML_ATTR_KEY);
		stackBottom.attr(key, v);
		key = null;
	}

	public final void key(String k) { _state(XML_ATTR_KEY, XML_ATTR_VAL); key = k; }
	@SuppressWarnings("fallthrough")
	public final void valueList() {
		switch (state) {
			case XML_ATTR:
				if (stackBottom instanceof Document) {
					state = XML_NAME;
					break;
				}
			case XML_CHILDREN: state = XML_CHILD; break;
			case XML_CHILD: state = XML_NAME; break;
			case XML_BEGIN: state = XML_ATTR; break;
			default: throw new IllegalStateException(String.valueOf(state));
		}
	}
	public final void valueMap() { _state(XML_ATTR, XML_ATTR_KEY); }

	private void _state(byte key, byte val) {
		if (state != key) throw new IllegalStateException(String.valueOf(key));
		state = val;
	}

	@SuppressWarnings("fallthrough")
	public final void pop() {
		switch (state) {
			case XML_ATTR_KEY:
				if (stackBottom instanceof Document) {
					state = XML_CHILD;
					break;
				}
			case XML_CHILD: state = XML_CHILDREN; break;
			case XML_ATTR:
			case XML_CHILDREN:
				if (stack.isEmpty()) {
					state = -1; // END
					break;
				}
				Element child = stackBottom;
				stackBottom = stack.remove(stack.size()-1);
				beforePop(child, stackBottom);
				state = XML_CHILD;
				break;
			default: throw new IllegalStateException(String.valueOf(state));
		}
	}

	@Override
	public void comment(String comment) {
		if (state != XML_CHILD) throw new IllegalStateException(String.valueOf(state));
		stackBottom.add(new Text(comment, Node.COMMENT));
	}

	@Override
	public <T> void setProperty(TypedKey<T> k, T v) {
		switch (k.name) {
			case "generic:maxDepth": maxDepth = (int) v; break;
			case "xml:headless": ((Document)stackBottom).headless(); break;
			case "xml:void": stackBottom.isVoid = (boolean) v; break;
			case "xml:CData":
				List<Node> children = stackBottom.children();
				((Text)children.get(children.size()-1)).nodeType = (boolean) v ? Node.CDATA : Node.TEXT;
			break;
		}
	}

	public final ToXml reset() {
		stack.clear();
		stackBottom = new Document();
		state = XML_BEGIN;
		key = null;
		return this;
	}

	protected Element createElement(String str) { return new Element(str); }
	protected void beforePop(Element child, Element parent) {}

	public final Element get() { return stackBottom; }
}