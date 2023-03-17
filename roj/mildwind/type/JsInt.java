package roj.mildwind.type;

import roj.mildwind.JsContext;

/**
 * @author Roj234
 * @since 2023/6/12 0012 14:48
 */
public final class JsInt extends JsReferenceCounted {
	public static final JsInt ZERO = new JsInt(null, 0);

	private int value;

	public JsInt(int v) { super(null); value = v; }
	public JsInt(JsContext vm, int v) { super(vm); vm__setValue(v); }
	public void vm__setValue(int v) { value = v; refCount = 1; }

	public Type type() { _unref(); return Type.INT; }

	public int asInt() { _unref(); return value; }
	public int asBool() { _unref(); return value; }
	public double asDouble() { _unref(); return value; }
	public String toString() { _unref(); return Integer.toString(value); }

	public JsObject get(String name) { _unref(); return JsContext.context().NUMBER_PROTOTYPE.get(name); }

	public JsObject op_add(JsObject r) {
		switch (r.klassType()) {
			case DOUBLE: _unref(); return JsContext.getDouble(value+r.asDouble());
			case INT: case BOOL: return mutate(value+r.asInt());
			default: return super.op_add(r);
		}
	}
	public JsObject op_sub(JsObject r) {
		if (r.type() == Type.INT || r.type() == Type.BOOL) return mutate(value-r.asInt());
		_unref(); return JsContext.getDouble(value-r.asDouble());
	}
	public JsObject op_mul(JsObject r) {
		if (r.type() == Type.INT || r.type() == Type.BOOL) return mutate(value*r.asInt());
		_unref(); return JsContext.getDouble(value*r.asDouble());
	}
	public JsObject op_div(JsObject r) { _unref(); return JsContext.getDouble(value/r.asDouble()); }
	public JsObject op_neg(          ) { return mutate(-value); }
	public JsObject op_pow(JsObject r) { _unref(); return JsContext.getDouble(Math.pow(value,r.asDouble())); }
	public JsObject op_mod(JsObject r) {
		if (r.type() == Type.INT || r.type() == Type.BOOL) return mutate(value%r.asInt());
		_unref();return JsContext.getDouble(value%r.asDouble());
	}

	public JsObject op_inc(int v) { return mutate(value+v); }
	public JsObject op_inc(double cv) { _unref(); return JsContext.getDouble(value+cv); }

	public int op_geq(JsObject r) {
		_unref();
		switch (r.type()) {
			// include NAN
			default: return -1;
			case DOUBLE: return Double.compare(value, r.asDouble());
			case INT: case BOOL: return Integer.compare(value, r.asInt());
		}
	}
	public int op_leq(JsObject r) {
		_unref();
		switch (r.type()) {
			// include NAN
			default: return 1;
			case DOUBLE: return Double.compare(value, r.asDouble());
			case INT: case BOOL: return Integer.compare(value, r.asInt());
		}
	}

	public boolean op_equ(JsObject o) {
		switch (o.type()) {
			case INT: case BOOL: return o.asBool() == value;
			default: return o.asDouble() == value;
		}
	}
	public boolean op_feq(JsObject o) {
		Type type = o.klassType();
		if (type == Type.INT) return o.asBool() == value;
		else if (type == Type.DOUBLE) return o.asDouble() == value;
		return false;
	}

	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		JsInt jsInt = (JsInt) o;
		return value == jsInt.value;
	}
	public int hashCode() { return value; }

	private JsInt mutate(int v) {
		if (refCount > 1 || vm == null) {
			refCount--;
			return JsContext.getInt(v);
		}

		value = v;
		return this;
	}
	void add(JsContext vm) { vm.add(this); }
}
