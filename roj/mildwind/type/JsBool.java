package roj.mildwind.type;

import roj.mildwind.JsContext;

/**
 * @author Roj234
 * @since 2023/6/12 0012 14:48
 */
public final class JsBool implements JsObject {
	public static final JsBool TRUE = new JsBool(), FALSE = new JsBool();

	public static JsObject valueOf(int v) { return v != 0 ? TRUE : FALSE; }

	public Type type() { return Type.BOOL; }

	public int asBool() { return this == TRUE ? 1 : 0; }
	public int asInt() { return this == TRUE ? 1 : 0; }
	public double asDouble() { return this == TRUE ? 1 : 0; }
	public String toString() { return this == TRUE ? "true" : "false"; }

	public JsObject get(String name) { return JsContext.context().OBJECT_PROTOTYPE.get(name); }

	public boolean op_equ(JsObject o) { return o.type().numOrBool() && ((this == TRUE) ? o.asBool() == 1 : o.asBool() == 0); }
	public boolean op_feq(JsObject o) { return o == this; }
}
