package roj.mildwind.type;

import roj.mildwind.JsContext;
import roj.mildwind.api.Arguments;

/**
 * @author Roj234
 * @since 2023/6/20 0012 22:55
 */
public abstract class JsConstructor extends JsFunction {
	private final JsMap prototype;

	public JsConstructor() { this(JsContext.context().FUNCTION_PROTOTYPE); }
	public JsConstructor(JsMap proto) {
		super(proto);
		prototype = new JsMap();
		prototype.put("constructor", this);
	}

	// copy with closure instance
	public JsConstructor(JsConstructor fn) {
		super(fn.__proto__);
		prototype = fn.prototype;
	}

	public JsMap prototype() { return prototype; }

	@Override
	public JsObject get(String name) {
		if (name.equals("prototype")) return prototype;
		return super.get(name);
	}

	@Override
	public void put(String name, JsObject value) {
		if (name.equals("prototype")) return;
		super.put(name, value);
	}

	@Override
	public boolean del(String name) {
		if (name.equals("prototype")) return false;
		return super.del(name);
	}

	public JsObject _new(Arguments arguments) {
		JsMap inst = new JsMap(prototype);
		JsObject ref = _invoke(inst, arguments);
		return ref == JsNull.UNDEFINED ? inst : ref;
	}
}