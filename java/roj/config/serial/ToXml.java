package roj.config.serial;

import roj.collect.SimpleList;
import roj.config.data.*;

import java.util.List;

/**
 * @author Roj234
 * @since 2022/11/15 0015 2:07
 */
public class ToXml implements CVisitor {
	public static final byte
		XML_BEGIN    = 0,
		XML_NAME     = 1,
		XML_ATTR     = 2,
		XML_CHILDREN = 3,
		XML_CHILD    = 4,
		XML_ATTR_KEY = 5,
		XML_ATTR_VAL = 6;

	private final List<Element> stack = new SimpleList<>();
	private Element stackBottom = new Document();

	private String key;

	private byte state;

	private int maxDepth = 100;

	public final void value(String str) {
		switch (state) {
			case XML_CHILD: stackBottom.add(new Text(str)); break;
			case XML_NAME:
				state = XML_ATTR;
				if (stack.size() >= maxDepth) throw new IllegalStateException("栈溢出: 最大深度是 "+maxDepth);
				stack.add(stackBottom);
				stackBottom.add(stackBottom = createElement(str));
				break;
			default: add(CEntry.valueOf(str)); break;
		}
	}
	public final void value(int l) { add(CEntry.valueOf(l)); }
	public final void value(long l) { add(CEntry.valueOf(l)); }
	public final void value(double l) { add(CEntry.valueOf(l));}
	public final void value(boolean l) { add(CEntry.valueOf(l)); }
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
	public void setProperty(String k, Object v) {
		switch (k) {
			case "entry:max_depth": maxDepth = (int) v; break;
			case "xml:headless": ((Document)stackBottom).headless(); break;
			case "xml:short_tag": stackBottom.shortTag = (boolean) v; break;
			case "xml:cdata":
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