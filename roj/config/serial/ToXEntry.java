package roj.config.serial;

import roj.collect.SimpleList;
import roj.config.data.*;

import java.util.List;

/**
 * @author Roj234
 * @since 2022/11/15 0015 2:07
 */
public class ToXEntry implements CVisitor {
	private static final byte
		XML_BEGIN    = 0,
		XML_NAME     = 1,
		XML_ATTR     = 2,
		XML_CHILDREN = 3,
		XML_CHILD    = 4,
		XML_ATTR_KEY = 5,
		XML_ATTR_VAL = 6;

	private final List<XElement> stack = new SimpleList<>();
	private XElement stackBottom = new XHeader();

	private String key;

	private byte state;

	private int maxDepth = 100;
	public void setMaxDepth(int depth) { maxDepth = depth; }

	public final void value(String str) {
		switch (state) {
			case XML_CHILD: stackBottom.add(new XText(str)); break;
			case XML_NAME:
				state = XML_ATTR;
				if (stack.size() >= maxDepth) throw new IllegalStateException("exceeds max depth " + maxDepth);
				stack.add(stackBottom);
				stackBottom.add(stackBottom = createElement(str));
				break;
			default: add(CString.valueOf(str)); break;
		}
	}
	public final void value(int l) { add(CInteger.valueOf(l)); }
	public final void value(long l) { add(CLong.valueOf(l)); }
	public final void value(double l) { add(CDouble.valueOf(l));}
	public final void value(boolean l) { add(l ? CBoolean.TRUE : CBoolean.FALSE); }
	public final void valueNull() { add(CNull.NULL); }
	private void add(CEntry v) {
		if (state == XML_CHILD) {
			stackBottom.add(new XText(v.asString()));
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
				if (stackBottom instanceof XHeader) {
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
				if (stackBottom instanceof XHeader) {
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
				XElement child = stackBottom;
				stackBottom = stack.remove(stack.size()-1);
				beforePop(child, stackBottom);
				state = XML_CHILD;
				break;
			default: throw new IllegalStateException(String.valueOf(state));
		}
	}

	@Override
	public void vsopt(String k, Object v) {
		switch (k) {
			case "xml:headless": ((XHeader)stackBottom).headless(); break;
			case "xml:short_tag": stackBottom.shortTag = (boolean) v; break;
			case "xml:cdata":
				List<XEntry> children = stackBottom.childrenForRead();
				((XText)children.get(children.size()-1)).CDATA_flag = (boolean) v ? XText.ALWAYS_ENCODE : XText.NEVER_ENCODE;
			break;
		}
	}

	protected XElement createElement(String str) { return new XElement(str); }
	protected void beforePop(XElement child, XElement parent) {}

	public final XElement get() { return stackBottom; }

	public final ToXEntry reset() {
		stack.clear();
		stackBottom = new XHeader();
		state = XML_BEGIN;
		key = null;
		return this;
	}
}
