package roj.config.data;

import roj.config.serial.CVisitor;
import roj.text.CharList;
import roj.text.TextUtil;

/**
 * @author Roj234
 * @since 2021/5/31 21:17
 */
public final class CInt extends CEntry {
	public int value;
	public CInt() {}
	public CInt(int n) {value = n;}
	public static CEntry valueOf(String number) { return valueOf(TextUtil.parseInt(number)); }

	public Type getType() { return Type.INTEGER; }
	protected boolean eqVal(CEntry o) { return o.asInt() == value; }
	public boolean mayCastTo(Type o) {
		if (((1 << o.ordinal()) & 0b011110000100) != 0) return true;
		return switch (o) {
			case Int1 -> value >= -128 && value <= 127;
			case Int2 -> value >= -32768 && value <= 32767;
			default -> false;
		};
	}

	public boolean asBool() { return value != 0; }
	public int asInt() { return value; }
	public long asLong() { return value; }
	public float asFloat() { return value; }
	public double asDouble() { return value; }
	public String asString() { return String.valueOf(value); }

	public void accept(CVisitor ser) { ser.value(value); }
	public Object raw() { return value; }

	public CharList toJSON(CharList sb, int depth) {return sb.append(value);}
	public String toString() {return String.valueOf(value);}

	public int hashCode() {return value;}
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		CInt that = (CInt) o;
		return that.value == value;
	}
}