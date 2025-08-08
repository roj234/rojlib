package roj.config.data;

import roj.config.serial.CVisitor;
import roj.text.CharList;

/**
 * @author Roj234
 * @since 2022/3/21 14:59
 */
public final class CFloat extends CEntry {
	public float value;
	public CFloat() {}
	public CFloat(float v) {this.value = v;}
	public static CEntry valueOf(String number) {return valueOf(Float.parseFloat(number));}

	public Type getType() { return Type.Float4; }
	public boolean eqVal(CEntry o) { return Float.compare(o.asFloat(), value) == 0; }
	public boolean mayCastTo(Type o) {
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
	public float asFloat() { return value; }
	public double asDouble() { return value; }
	public String asString() { return String.valueOf(value); }

	public void accept(CVisitor visitor) { visitor.value(value); }
	public Object raw() { return value; }

	public CharList toJSON(CharList sb, int depth) { return sb.append(value); }
	public String toString() { return String.valueOf(value).concat("F"); }

	public int hashCode() {return Float.floatToRawIntBits(value) * 114514;}
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		CFloat that = (CFloat) o;
		return Float.compare(that.value, value) == 0;
	}
}