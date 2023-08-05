package roj.mildwind.bridge;

import roj.asm.tree.MethodNode;
import roj.collect.IntMap;
import roj.collect.MyHashMap;
import roj.mildwind.JsContext;
import roj.mildwind.api.Arguments;
import roj.mildwind.type.JsConstructor;
import roj.mildwind.type.JsObject;
import roj.mildwind.util.ScriptException;

import java.util.List;

import static roj.reflect.ReflectionUtils.u;

/**
 * @author Roj234
 * @since 2023/6/22 0022 1:05
 */
public final class JsJavaClass extends JsConstructor {
	public Class<?> classType;

	public MyHashMap<String, FieldInfo> fields, staticFields;
	public MyHashMap<String, JsJavaMethod> methods, staticMethods;
	public IntMap<List<MethodNode>> constructors;

	public JsJavaClass(Class<?> type) {
		super(type.getSuperclass() == null ? JsContext.context().FUNCTION_PROTOTYPE : JsContext.getClassInfo(type.getSuperclass()));
		this.classType = type;
		// todo create methods
	}

	public String toString() { return "function "+classType.getName()+"() { [native code] }"; }

	@Override
	public JsObject _new(Arguments arguments) {
		try {
			return _invoke(new JsJavaObject(u.allocateInstance(classType), this), arguments);
		} catch (InstantiationException e) {
			throw new ScriptException("java invocation failed", e);
		}
	}

	@Override
	public JsObject _invoke(JsObject self, Arguments arguments) {
		if (self instanceof JsJavaObject && ((JsJavaObject) self).obj.getClass() == classType) {
			invokeMethod((JsJavaObject) self, arguments, constructors, "construct");
			return self;
		} else {
			throw new ScriptException("Illegal invocation");
		}
	}

	@SuppressWarnings("unchecked")
	final JsObject invokeMethod(JsJavaObject ref, Arguments arguments, Object methods, String stage) {
		AsmMethodInvoker node;
		int argc = arguments.length().asInt();

		if (methods instanceof Object[]) {
			Object[] obj = (Object[]) methods;
			node = (AsmMethodInvoker) obj[0];

			if (((Number) obj[1]).intValue() != argc)
				throw new ScriptException("Failed to "+stage+" '"+classType.getName()+"': "+obj[1]+" arguments required, but only "+argc+" present.");
			if (node.canInvoke(arguments) < 0) node = null;
		} else {
			IntMap<Object> map = (IntMap<Object>) methods;
			methods = map.get(argc);
			if (methods == null) throw new ScriptException("Failed to "+stage+" '"+classType.getName()+"': "+map.keySet()+" arguments required, but only "+argc+" present.");
			if (methods instanceof AsmMethodInvoker) {
				node = (AsmMethodInvoker) methods;
				if (node.canInvoke(arguments) < 0) node = null;
			}
			else {
				node = null;
				int max = 999;
				List<AsmMethodInvoker> nodes = (List<AsmMethodInvoker>) methods;
				for (int i = 0; i < nodes.size(); i++) {
					AsmMethodInvoker mn = nodes.get(i);
					int cast = mn.canInvoke(arguments);
					if (cast >= 0 && cast < max) {
						max = cast;
						node = mn;
					}
				}
			}
		}
		if (node == null) throw new ScriptException("Failed to "+stage+" '"+classType.getName()+"': no method suits arguments provided");

		return node.invoke(ref.obj, arguments);
	}
}
