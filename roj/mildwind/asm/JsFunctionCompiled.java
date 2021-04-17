package roj.mildwind.asm;

import roj.mildwind.JsContext;
import roj.mildwind.api.Arguments;
import roj.mildwind.type.JsFunction;
import roj.mildwind.type.JsObject;

/**
 * @author Roj234
 * @since 2023/6/12 0012 16:15
 */
public abstract class JsFunctionCompiled extends JsFunction {
	protected int methodId;
	protected JsObject[][] closures;

	JsFunctionCompiled() {}

	// tableswitch
	public abstract JsObject _invoke(JsObject self, Arguments arg);

	public abstract JsFunctionCompiled copy(int methodId);

	@Override
	public JsFunction _prepareClosure(JsObject[][] prevClosure, JsObject[] currClosure) {
		JsObject[][] closures = new JsObject[prevClosure.length+1][];
		System.arraycopy(prevClosure, 0, closures, 0, prevClosure.length);
		closures[prevClosure.length] = currClosure;

		JsFunctionCompiled fn = copy(methodId);
		fn.closures = closures;
		return fn;
	}

	protected static void sv(JsObject obj, JsFunctionCompiled fn, int closure, int slot) {
		JsObject[] arr = fn.closures[closure];

		JsObject prev = arr[slot];
		if (prev != null) prev._unref();

		arr[slot] = obj;
	}
	protected JsObject gv(int closure, int slot) { return closures[closure][slot]; }

	protected static void gsv(JsObject obj, String name) { JsContext.context().root.put(name, obj); }
	protected static JsObject ggv(String name) { return JsContext.context().root.get(name); }
}
