package roj.mildwind.bridge;

import roj.mildwind.api.Arguments;
import roj.mildwind.type.JsFunction;
import roj.mildwind.type.JsObject;
import roj.mildwind.util.ScriptException;

/**
 * @author Roj234
 * @since 2023/6/22 0022 1:41
 */
final class JsJavaMethod extends JsFunction {
	JsJavaMethod(JsJavaClass owner, Object methods) {
		super(owner);
		defineVal("__proto__", owner, 0);
		this.methods = methods;
	}

	private final Object methods;

	@Override
	public JsObject _invoke(JsObject self, Arguments arguments) {
		JsJavaClass fn = (JsJavaClass) __proto__();
		if (self instanceof JsJavaObject && fn.classType.isInstance(((JsJavaObject) self).obj)) {
			return fn.invokeMethod((JsJavaObject) self, arguments, methods, "invoke");
		} else {
			throw new ScriptException("Illegal invocation");
		}
	}
}
