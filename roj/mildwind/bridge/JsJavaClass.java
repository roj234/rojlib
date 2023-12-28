package roj.mildwind.bridge;

import roj.asm.Opcodes;
import roj.collect.IntMap;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.mildwind.JsContext;
import roj.mildwind.api.Arguments;
import roj.mildwind.type.JsConstructor;
import roj.mildwind.type.JsObject;
import roj.mildwind.type.Type;
import roj.mildwind.util.ScriptException;
import roj.util.Helpers;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static roj.reflect.ReflectionUtils.u;

/**
 * @author Roj234
 * @since 2023/6/22 0022 1:05
 */
public final class JsJavaClass extends JsConstructor {
	public final Class<?> type;

	final Map<String, FieldInfo> fields;
	private final Map<String, FieldInfo> staticFields;
	@Deprecated
	final Map<String, JsJavaMethod> methods;
	private final Map<String, JsJavaMethod> staticMethods;

	private final IntMap<Object> constructors;

	public JsJavaClass(Class<?> type) {
		super(type.getSuperclass() == null ? JsContext.context().FUNCTION_PROTOTYPE : JsContext.getClassInfo(type.getSuperclass()));
		this.type = type;

		Map<String, FieldInfo> _f = new MyHashMap<>(), _sf = new MyHashMap<>();
		for (Field f : type.getDeclaredFields()) {
			((f.getModifiers()&Opcodes.ACC_STATIC) == 0 ? _f : _sf).put(f.getName(), new FieldInfo(f));
		}
		fields = _f.isEmpty() ? Collections.emptyMap() : _f;
		staticFields = _sf.isEmpty() ? Collections.emptyMap() : _sf;

		Map<String, JsJavaMethod> _m = new MyHashMap<>(), _sm = new MyHashMap<>();
		for (Method m : type.getDeclaredMethods()) {
			Map<String, JsJavaMethod> map = (m.getModifiers() & Opcodes.ACC_STATIC) == 0 ? _m : _sm;

			JsJavaMethod o = map.get(m.getName());
			if (o == null) map.put(m.getName(), new JsJavaMethod(this, m));
			else o.addMethod(m);
		}
		for (Map.Entry<String, JsJavaMethod> entry : _m.entrySet()) {
			prototype().put(entry.getKey(), entry.getValue());
		}
		methods = _m.isEmpty() ? Collections.emptyMap() : _m;
		staticMethods = _sm.isEmpty() ? Collections.emptyMap() : _sm;

		IntMap<Object> _c = new IntMap<>();
		for (Constructor<?> c : type.getDeclaredConstructors()) {
			int count = c.getParameterCount();
			Object prev = _c.putIfAbsent(count, c);
			if (prev != null) {
				if (prev.getClass() == SimpleList.class) ((SimpleList<?>) prev).add(Helpers.cast(c));
				else _c.putInt(count, SimpleList.asModifiableList(c, prev));
			}
		}
		constructors = _c;
	}

	public String toString() { return "function "+type.getName()+"() { [native code] }"; }

	@Override
	public JsObject get(String name) {
		FieldInfo f = staticFields.get(name);
		if (f != null) return f.get(type);

		JsJavaMethod m = staticMethods.get(name);
		if (m != null) return m;

		return super.get(name);
	}

	@Override
	public void put(String name, JsObject value) {
		FieldInfo f = staticFields.get(name);
		if (f != null) {
			f.set(type, value);
			return;
		}

		JsJavaMethod m = staticMethods.get(name);
		if (m != null) return;

		super.put(name, value);
	}

	@Override
	public JsObject _new(Arguments arguments) {
		try {
			return _invoke(new JsJavaObject(u.allocateInstance(type), this), arguments);
		} catch (InstantiationException e) {
			throw new ScriptException("java invocation failed", e);
		}
	}

	@Override
	public JsObject _invoke(JsObject self, Arguments arguments) {
		if (self instanceof JsJavaObject && ((JsJavaObject) self).obj.getClass() == type) {
			invokeMethod(((JsJavaObject) self).obj, arguments, constructors, "construct");
			return self;
		} else {
			throw new ScriptException("Illegal invocation");
		}
	}

	@SuppressWarnings("unchecked")
	final JsObject invokeMethod(Object inst, Arguments arguments, IntMap<Object> methods, String stage) {
		Method node = null;
		int argc = arguments.length().asInt();

		Object mmmm = methods.get(argc);
		if (mmmm == null) throw new ScriptException("Failed to "+stage+" '"+ type.getName()+"': "+methods.keySet()+" arguments required, but only "+argc+" present.");

		for (int i = 0; i < arguments.java_length(); i++) {
			// STRING, INT, DOUBLE, NAN, BOOL, NULL, UNDEFINED, OBJECT, ARRAY, FUNCTION, SYMBOL;
			Type type = arguments.getByInt(i).type();

		}
		/*if (mmmm.getClass() == InvokerReflect) {
			CodeWriter c;
			c.unpackArray();
		}*/
		if (mmmm instanceof Method) {
			node = (Method) mmmm;
			node.getParameterTypes();
		} else {
			node = null;
			int max = 999;
			List<AsmMethodInvoker> nodes = (List<AsmMethodInvoker>) mmmm;
			for (int i = 0; i < nodes.size(); i++) {
				AsmMethodInvoker mn = nodes.get(i);
				int cast = mn.canInvoke(arguments);
				if (cast >= 0 && cast < max) {
					max = cast;
					//node = mn;
				}
			}
		}
		if (node == null) throw new ScriptException("Failed to "+stage+" '"+ type.getName()+"': no method suits arguments provided");

		try {
			return (JsObject) node.invoke(inst, arguments);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		} catch (InvocationTargetException e) {
			throw new RuntimeException(e);
		}
	}
}