package roj.mildwind.type;

import roj.mildwind.JsContext;
import roj.mildwind.util.ScriptException;

/**
 * @author Roj234
 * @since 2023/6/12 0012 14:48
 */
public final class JsSymbol implements JsObject {
	public final String name;
	public JsSymbol() { name = null; }
	public JsSymbol(String v) { name = v; }

	public Type type() { return Type.SYMBOL; }

	public int asInt() { throw new ScriptException("Cannot convert a Symbol value to a number"); }
	public double asDouble() { throw new ScriptException("Cannot convert a Symbol value to a number"); }
	public String toString() { return "Symbol("+(name==null?"":name)+")"; }

	public JsObject get(String name) {
		switch (name) {
			case "description": return JsContext.getStr(this.name);
			case "constructor": return JsContext.context().root.get("Symbol");
		}
		return JsContext.context().OBJECT_PROTOTYPE.get(name);
	}
}
