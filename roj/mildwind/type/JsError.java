package roj.mildwind.type;

import roj.mildwind.JsContext;

/**
 * @author Roj234
 * @since 2023/6/12 0012 16:47
 */
public class JsError implements JsObject {
	public Throwable ex;
	public Type type() { return Type.OBJECT; }
	public JsObject get(String name) { return JsContext.context().ERROR_PROTOTYPE.get(name); }
}
