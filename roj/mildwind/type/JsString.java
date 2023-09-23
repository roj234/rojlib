package roj.mildwind.type;

import roj.mildwind.JsContext;
import roj.text.TextUtil;

/**
 * @author Roj234
 * @since 2023/6/12 0012 14:48
 */
public class JsString extends JsReferenceCounted {
	String value;
	byte st;

	public JsString(String v) { super(null); vm__setValue(v); }
	public JsString(JsContext vm, String v) { super(vm); vm__setValue(v); }
	public void vm__setValue(String v) { value = v; refCount = 1; checkNum(); }

	final void checkNum() { st = value.isEmpty() ? 7 : (byte) ((TextUtil.isNumber(value)+1) | 4); }
	private int _type() { _unref(); return st&3; }

    public final Type type() { return Type.VALUES[_type()%3]; }
	public final Type klassType() { _unref(); return Type.STRING; }

	public final int asBool() {
		switch (_type()) {
			case 0: default: return 1;
			case 1: return Integer.parseInt(value);
			case 2: return (int) asDouble();
			case 3: return 0;
		}
	}
	public final int asInt() {
		switch (_type()) {
			case 0: default: return 0;
			case 1: return Integer.parseInt(value);
			case 2: return (int) asDouble();
		}
	}
	public final double asDouble() {
		_unref();
		if ((st&3)%3 == 0) return Double.NaN;
		return Double.parseDouble(value);
	}
	public final String toString() { _unref(); return value; }

	public final JsObject get(String name) { _unref(); return JsContext.context().STRING_PROTOTYPE.get(name); }

	public JsObject op_add(JsObject r) { _unref(); return super.op_add(r); }
	@SuppressWarnings("fallthrough")
	public final JsObject op_sub(JsObject r) {
		switch (_type()) {
			case 0: default: break;
			case 1: if (r.type() == Type.INT) return JsContext.getInt(Integer.parseInt(value) - r.asInt());
			case 2: return JsContext.getDouble(Double.parseDouble(value) - r.asDouble());
			case 3: return r.op_neg();
		}
		return JsContext.getDouble(Double.NaN);
	}
	@SuppressWarnings("fallthrough")
	public final JsObject op_mul(JsObject r) {
		switch (_type()) {
			case 0: default: break;
			case 1: if (r.type() == Type.INT) return JsContext.getInt(Integer.parseInt(value) * r.asInt());
			case 2: return JsContext.getDouble(Double.parseDouble(value) * r.asDouble());
			case 3: if (r.type().numOrBool()) return JsContext.getInt(0); break;
		}
		return JsContext.getDouble(Double.NaN);
	}
	@SuppressWarnings("fallthrough")
	public final JsObject op_div(JsObject r) {
		switch (_type()) {
			case 0: default: break;
			case 1: if (r.type() == Type.INT) return JsContext.getInt(Integer.parseInt(value) / r.asInt());
			case 2: return JsContext.getDouble(Double.parseDouble(value) / r.asDouble());
			case 3: if (r.type().numOrBool()) return JsContext.getInt(0); break;
		}
		return JsContext.getDouble(Double.NaN);
	}
	public final JsObject op_neg() {
		switch (_type()) {
			case 0: default: return JsContext.getDouble(Double.NaN);
			case 1: return JsContext.getInt(-Integer.parseInt(value));
			case 2: return JsContext.getDouble(-Double.parseDouble(value));
			case 3: return JsContext.getInt(0);
		}
	}
	public final JsObject op_pow(JsObject r) {
		switch (_type()) {
			case 0: default: break;
			case 1: case 2: return JsContext.getDouble(Math.pow(Double.parseDouble(value), r.asDouble()));
			case 3: if (r.type().numOrBool()) return JsContext.getInt(0); break;
		}
		return JsContext.getDouble(Double.NaN);
	}
	@SuppressWarnings("fallthrough")
	public final JsObject op_mod(JsObject r) {
		switch (_type()) {
			case 0: default: break;
			case 1: if (r.type() == Type.INT) return JsContext.getInt(Integer.parseInt(value) % r.asInt());
			case 2: return JsContext.getDouble(Double.parseDouble(value) % r.asDouble());
			case 3: if (r.type().numOrBool()) return JsContext.getInt(0); break;
		}
		return JsContext.getDouble(Double.NaN);
	}

	public final JsObject op_inc(int v) {
		switch (_type()) {
			case 0: default: return JsContext.getDouble(Double.NaN);
			case 1: return JsContext.getInt(Integer.parseInt(value)+v);
			case 2: return JsContext.getDouble(Double.parseDouble(value)+v);
			case 3: return JsContext.getInt(v);
		}
	}
	public final JsObject op_inc(double v) {
		switch (_type()) {
			case 0: default: return JsContext.getDouble(Double.NaN);
			case 1: case 2: return JsContext.getDouble(Double.parseDouble(value)+v);
			case 3: return JsContext.getDouble(v);
		}
	}

	public boolean op_equ(JsObject o) { _unref(); return o.toString().equals(value); }

	public final int op_geq(JsObject r) { return op_leq(r); }
	public int op_leq(JsObject r) { _unref(); return value.compareTo(r.toString()); }

	final void add(JsContext vm) { vm.add(this); }
}
