package roj.mildwind.bridge;

import roj.mildwind.JsContext;
import roj.mildwind.type.AbstractJsMap;
import roj.mildwind.type.JsObject;
import roj.mildwind.util.ScriptException;

import java.util.Iterator;
/**
 * @author Roj234
 * @since 2023/6/22 0022 1:01
 */
public final class JsJavaObject extends AbstractJsMap {
	final Object obj;
	final JsJavaClass type;

	public JsJavaObject(Object obj) {
		this.obj = obj;
		this.type = JsContext.getClassInfo(obj.getClass());
	}

	public JsJavaObject(Object obj, JsJavaClass type) {
		this.obj = obj;
		this.type = type;
	}

	public String toString() { return obj.toString(); }
	@SuppressWarnings("unchecked")
	public <T> T asObject(Class<T> klass) {
		if (!type.classType.isAssignableFrom(klass)) {
			throw new ScriptException(type.classType.getName() + " cannot convert to " + klass.getName());
		}
		return (T) obj;
	}

	public JsObject __proto__() { return type.prototype(); }

	public JsObject get(String name) {
		if (name.equals("__proto__")) return type.prototype();

		FieldInfo info = type.fields.get(name);
		if (info == null) return type.prototype().get(name);
		return info.get(obj);
	}
	public void put(String name, JsObject value) {
		if (name.equals("__proto__")) return;

		FieldInfo info = type.fields.get(name);
		if (info == null) return;
		info.set(obj, value);
	}
	public boolean del(String name) { return !name.equals("__proto__") && !type.fields.containsKey(name) && !type.methods.containsKey(name); }

	public boolean op_in(JsObject toFind) {
		String name = toFind.toString();
		return type.fields.containsKey(name) || type.methods.containsKey(name) || "__proto__".equals(name);
	}

	public Iterator<JsObject> _keyItr() {
		return new Iterator<JsObject>() {
			final Iterator<String> itr = type.fields.keySet().iterator();
			public boolean hasNext() { return itr.hasNext(); }
			public JsObject next() { return JsContext.getStr(itr.next()); }
		};
	}
	public Iterator<JsObject> _valItr() {
		return new Iterator<JsObject>() {
			final Iterator<String> itr = type.fields.keySet().iterator();
			public boolean hasNext() { return itr.hasNext(); }
			public JsObject next() { return get(itr.next()); }
		};
	}
}
