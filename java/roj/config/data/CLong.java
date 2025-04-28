package roj.config.data;

import roj.config.serial.CVisitor;
import roj.text.CharList;

/**
 * @author Roj234
 * @since 2021/5/31 21:17
 */
public class CLong extends CEntry {
	public long value;
	public CLong() {}
	public CLong(long number) { this.value = number; }
	public static CEntry valueOf(String number) { return valueOf(Long.parseLong(number)); }

	public Type getType() { return Type.LONG; }
	public boolean eqVal(CEntry o) { return o.asLong() == value; }
	public boolean mayCastTo(Type o) {
		if (((1 << o.ordinal()) & 0b011100000100) != 0) return true;
		return switch (o) {
			case Int1 -> value >= -128 && value <= 127;
			case Int2 -> value >= -32768 && value <= 32767;
			case INTEGER -> value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE;
			default -> false;
		};
	}

	public final int asInt() { return value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE ? (int) value : super.asInt(); }
	public final long asLong() { return value; }
	public final float asFloat() { return value; }
	public final double asDouble() { return value; }
	public final String asString() { return String.valueOf(value); }

	public void accept(CVisitor visitor) { visitor.value(value); }
	public Object raw() { return value; }

	public CharList toJSON(CharList sb, int depth) { return sb.append(value); }
	public String toString() {return String.valueOf(value).concat("L");}

	public int hashCode() { return (int) (value ^ (value >>> 32)); }
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		CLong that = (CLong) o;
		return that.value == value;
	}
}