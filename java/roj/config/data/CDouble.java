package roj.config.data;

import roj.config.serial.CVisitor;
import roj.text.CharList;

/**
 * @author Roj234
 * @since 2021/5/31 21:17
 */
public final class CDouble extends CEntry {
	public double value;

	public CDouble(double number) { this.value = number; }
	public static CDouble valueOf(double number) { return new CDouble(number); }
	public static CDouble valueOf(String number) { return valueOf(Double.parseDouble(number)); }

	public Type getType() { return Type.DOUBLE; }
	public boolean eqVal(CEntry o) { return Double.compare(value, o.asDouble()) == 0; }
	public boolean mayCastTo(Type o) {
		//                          TDFLISBZNsML
		if (((1 << o.ordinal()) & 0b011000000100) != 0) return true;
		return switch (o) {
			case Int1 -> value >= -128 && value <= 127;
			case Int2 -> value >= -32768 && value <= 32767;
			case INTEGER -> value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE;
			case LONG -> value >= Long.MIN_VALUE && value <= Long.MAX_VALUE;
			default -> false;
		};
	}

	public int asInt() { return value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE ? (int) value : super.asInt(); }
	public long asLong() { return value >= Long.MIN_VALUE && value <= Long.MAX_VALUE ? (long) value : super.asLong(); }
	public float asFloat() { return (float) value; }
	public double asDouble() { return value; }
	public String asString() { return String.valueOf(value); }

	public void accept(CVisitor ser) { ser.value(value); }
	public Object raw() { return value; }

	public CharList toJSON(CharList sb, int depth) { return sb.append(value); }

	public int hashCode() {
		long l = Double.doubleToRawLongBits(value);
		return (int) (l ^ (l >>> 32));
	}
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		CDouble that = (CDouble) o;
		return Double.compare(that.value, value) == 0;
	}
}