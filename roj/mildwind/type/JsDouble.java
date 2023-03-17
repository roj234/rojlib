package roj.mildwind.type;

import roj.mildwind.JsContext;

/**
 * @author Roj234
 * @since 2023/6/12 0012 14:48
 */
public final class JsDouble extends JsReferenceCounted {
	public static final JsDouble NAN = new JsDouble(null, Double.NaN);
	public static final JsDouble INF = new JsDouble(null, Double.POSITIVE_INFINITY);

	private double value;

	public JsDouble(double v) { super(null); value = v; }
	public JsDouble(JsContext vm, double v) { super(vm); vm__setValue(v); }
	public void vm__setValue(double v) { value = v; refCount = 1; }

	public Type type() { _unref(); return value == value ? Type.DOUBLE : Type.NAN; }
	public Type klassType() { _unref(); return Type.DOUBLE; }

	public int asBool() { _unref(); return (int) value; }
	public int asInt() { _unref(); return (int) value; }
	public double asDouble() { _unref(); return value; }
	public String toString() { _unref(); return Double.toString(value); }

	public JsObject get(String name) { _unref(); return JsContext.context().NUMBER_PROTOTYPE.get(name); }

	public JsObject op_add(JsObject r) {
		if (r.klassType().numOrBool()) return mutate(value+r.asDouble());
		return super.op_add(r); // op_add will decr refcount
	}
	public JsObject op_sub(JsObject r) { return mutate(value-r.asDouble()); }
	public JsObject op_mul(JsObject r) { return mutate(value*r.asDouble()); }
	public JsObject op_div(JsObject r) { return mutate(value/r.asDouble()); }
	public JsObject op_neg(          ) { return mutate(-value); }
	public JsObject op_pow(JsObject r) { return mutate(Math.pow(value,r.asDouble())); }
	public JsObject op_mod(JsObject r) { return mutate(value%r.asDouble()); }

	public JsObject op_inc(int v) { return mutate(value+v); }
	public JsObject op_inc(double v) { return mutate(value+v); }

	public int op_geq(JsObject r) {
		_unref();
		if (r.type().numOrBool()) return Double.compare(value, r.asDouble());
		return -1;
	}
	public int op_leq(JsObject r) {
		_unref();
		if (r.type().numOrBool()) return Double.compare(value, r.asDouble());
		return 1;
	}

	public boolean op_equ(JsObject o) { _unref(); return o.asDouble() == value; }
	public boolean op_feq(JsObject o) { _unref(); return o.klassType().num() && o.asDouble() == value; }

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		return Double.compare(((JsDouble) o).value, value) == 0;
	}

	@Override
	public int hashCode() {
		long v = Double.doubleToLongBits(value);
		return (int) (v ^ (v >>> 32));
	}

	private JsDouble mutate(double v) {
		if (refCount > 1 || vm == null) {
			refCount--;
			return JsContext.getDouble(v);
		}

		value = v;
		return this;
	}
	void add(JsContext vm) { vm.add(this); }
}
