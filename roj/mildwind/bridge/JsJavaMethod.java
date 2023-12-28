package roj.mildwind.bridge;

import roj.collect.IntMap;
import roj.collect.SimpleList;
import roj.mildwind.api.Arguments;
import roj.mildwind.type.JsFunction;
import roj.mildwind.type.JsObject;
import roj.mildwind.util.ScriptException;
import roj.util.Helpers;

import java.lang.reflect.Method;

/**
 * @author Roj234
 * @since 2023/6/22 0022 1:41
 */
final class JsJavaMethod extends JsFunction {
	JsJavaMethod(JsJavaClass owner, Method method) {
		super(owner);
		defineVal("__proto__", owner, 0);
		put("__proto__", owner);

		methods = new IntMap<>();
		methods.putInt(method.getParameterCount(), method);
	}

	private IntMap<Object> methods;
	final void addMethod(Method m) {
		int count = m.getParameterCount();
		Object prev = methods.putIfAbsent(count, m);
		if (prev != null) {
			if (prev.getClass() == SimpleList.class) ((SimpleList<?>) prev).add(Helpers.cast(m));
			else methods.putInt(count, SimpleList.asModifiableList(m, prev));
		}
	}

	@Override
	public JsObject _invoke(JsObject self, Arguments arguments) {
		JsJavaClass fn = (JsJavaClass) __proto__();
		if (self instanceof JsJavaObject && fn.type.isInstance(((JsJavaObject) self).obj)) {
			// if (methods.getClass() == SimpleList.class) methods = ((SimpleList<?>) methods).toArray();
			return fn.invokeMethod(((JsJavaObject) self).obj, arguments, methods, "invoke");
		}

		throw new ScriptException("Illegal invocation");
	}

	@Override
	public String toString() {
		Object o = methods.values().iterator().next();
		if (o.getClass() == SimpleList.class) o = ((SimpleList<?>) o).get(0);
		Method m = (Method) o;
		return "function "+m.getName()+"() { [native code (declared in "+m.getDeclaringClass()+")] }";
	}
}