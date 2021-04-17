package roj.mildwind.type;

import roj.mildwind.util.ScriptException;

/**
 * @author Roj234
 * @since 2023/6/12 0012 14:48
 */
public final class JsNull implements JsObject {
	public static final JsObject NULL = new JsNull();
	public static final JsObject UNDEFINED = new JsNull();

	public Type type() { return this == UNDEFINED ? Type.UNDEFINED : Type.NULL; }
	public int asBool() { return 0; }
	public double asDouble() { return this == NULL ? 0 : Double.NaN; }
	public String toString() { return this == NULL ? "null" : "undefined"; }

	public JsObject get(String name) { throw new ScriptException("Cannot read properties of "+this+" (reading '"+name+"')"); }
	public void put(String name, JsObject value) { throw new ScriptException("Cannot set properties of "+this+" (reading '"+name+"')"); }
	public boolean del(String name) { return conv() != null; }

	public JsObject getByInt(int i) { return get(Integer.toString(i)); }
	public void putByInt(int i, JsObject value) { put(Integer.toString(i), value); }
	public boolean delByInt(int i) { return del(Integer.toString(i)); }

	private static JsObject conv() { throw new ScriptException("Cannot convert undefined or null to object"); }

	public boolean op_equ(JsObject o) { return o.type() == Type.NULL || o.type() == Type.UNDEFINED; }
	public boolean op_feq(JsObject o) { return o.type() == type(); }
}
