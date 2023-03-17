package roj.mildwind.type;

import roj.mildwind.JsContext;
import roj.mildwind.api.Arguments;
import roj.mildwind.util.ScriptException;

import java.util.Collections;
import java.util.Iterator;
import java.util.function.Consumer;


/**
 * @author Roj234
 * @since 2023/6/12 0012 14:44
 */
public interface JsObject {
	Type type();
	default Type klassType() { return type(); }

	default int asBool() { return 1; }
	default int asInt() { return 0; }
	default double asDouble() { return Double.NaN; }
	default <T> T asObject(Class<T> klass) { throw new ScriptException(this + " is not a JavaObject"); }

	default JsObject length() { return get("length"); }
	default JsObject prototype() { return get("prototype"); }
	default JsObject __proto__() { return get("__proto__"); }

	default JsObject get(String name) { return JsNull.UNDEFINED; }
	default void put(String name, JsObject value) {}
	default boolean del(String name) { return true; }

	default JsObject getByInt(int i) { return JsNull.UNDEFINED; }
	default void putByInt(int i, JsObject value) {}
	default boolean delByInt(int i) { return true; }

	default JsObject _new(Arguments arguments) { throw new ScriptException(this+" is not a constructor"); }
	default JsObject _invoke(JsObject self, Arguments arguments) { throw new ScriptException(this+" is not a function"); }
	default JsFunction _prepareClosure(JsObject[][] prevClosure, JsObject[] currClosure) { return (JsFunction) this; }

	default void _ref() {}
	default void _unref() {}

	default JsObject op_add(JsObject r) { return new JsDynString().op_add(this).op_add(r); }
	default JsObject op_sub(JsObject r) { return JsContext.getDouble(Double.NaN); }
	default JsObject op_mul(JsObject r) { return JsContext.getDouble(Double.NaN); }
	default JsObject op_div(JsObject r) { return JsContext.getDouble(Double.NaN); }
	default JsObject op_neg(          ) { return JsContext.getDouble(Double.NaN); }
	default JsObject op_pow(JsObject r) { return JsContext.getDouble(Double.NaN); }
	default JsObject op_mod(JsObject r) { return JsContext.getDouble(Double.NaN); }

	default JsObject op_inc(int cv    ) { return JsContext.getDouble(Double.NaN); }
	default JsObject op_inc(double cv ) { return JsContext.getDouble(Double.NaN); }

	// >=
	default int op_geq(JsObject r) { return -1; }
	// <=
	default int op_leq(JsObject r) { return -1; }
	// ==
	default boolean op_equ(JsObject o) { return o == this; }
	// ===
	default boolean op_feq(JsObject o) { return o.type() == type() && op_equ(o); }
	int hashCode();

	// already swap
	default boolean op_in (JsObject toFind) { throw new ScriptException("Cannot use 'in' operator to search for '"+toFind+"' in "+this); }
	default boolean op_instanceof(JsObject object) { throw new ScriptException("Right-hand side of 'instanceof' is not an object"); }

	// for k in ...
	default Iterator<JsObject> _keyItr() { return Collections.emptyIterator(); }
	// for v of ...
	default Iterator<JsObject> _valItr() { return Collections.emptyIterator(); }

	static JsObject op_bitand(JsObject l, JsObject r) { l._unref(); r._unref(); return JsContext.getInt(l.asInt()  &  r.asInt()); }
	static JsObject op_bitor (JsObject l, JsObject r) { l._unref(); r._unref(); return JsContext.getInt(l.asInt()  |  r.asInt()); }
	static JsObject op_bitxor(JsObject l, JsObject r) { l._unref(); r._unref(); return JsContext.getInt(l.asInt()  ^  r.asInt()); }
	static JsObject op_ishl  (JsObject l, JsObject r) { l._unref(); r._unref(); return JsContext.getInt(l.asInt() <<  r.asInt()); }
	static JsObject op_ishr  (JsObject l, JsObject r) { l._unref(); r._unref(); return JsContext.getInt(l.asInt() >>  r.asInt()); }
	static JsObject op_iushr (JsObject l, JsObject r) { l._unref(); r._unref(); return JsContext.getInt(l.asInt() >>> r.asInt()); }
	static JsObject op_rev   (JsObject l            ) { l._unref();             return JsContext.getInt(~l.asInt()); }

	default JsObject shallowCOWInstance() { return this; }
	default void addChangeListener(Consumer<JsObject> listener) {}
	default void removeChangeListener(Consumer<JsObject> listener) {}
}
