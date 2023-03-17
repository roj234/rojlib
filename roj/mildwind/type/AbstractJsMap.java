package roj.mildwind.type;

import roj.mildwind.util.ScriptException;

/**
 * @author Roj234
 * @since 2023/6/22 0022 1:52
 */
public abstract class AbstractJsMap implements JsObject {
	public Type type() { return Type.OBJECT; }

	public JsObject getByInt(int i) { return get(Integer.toString(i)); }
	public void putByInt(int i, JsObject value) { put(Integer.toString(i), value); }
	public boolean delByInt(int i) { return del(Integer.toString(i)); }

	public boolean op_instanceof(JsObject object) { throw new ScriptException("Right-hand side of 'instanceof' is not callable"); }
}
