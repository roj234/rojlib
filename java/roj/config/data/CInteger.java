package roj.config.data;

import roj.config.VinaryParser;
import roj.config.serial.CVisitor;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2021/5/31 21:17
 */
public final class CInteger extends CEntry {
	public int value;

	public CInteger(int number) { this.value = number; }
	public static CInteger valueOf(int number) { return new CInteger(number); }
	public static CInteger valueOf(String number) { return valueOf(TextUtil.parseInt(number)); }

	public Type getType() { return Type.INTEGER; }
	protected boolean eqVal(CEntry o) { return o.asInteger() == value; }
	public boolean mayCastTo(Type o) {
		if (((1 << o.ordinal()) & 0b011110000100) != 0) return true;
		return switch (o) {
			case Int1 -> value >= -128 && value <= 127;
			case Int2 -> value >= -32768 && value <= 32767;
			default -> false;
		};
	}

	public boolean asBool() { return value != 0; }
	public int asInteger() { return value; }
	public long asLong() { return value; }
	public float asFloat() { return value; }
	public double asDouble() { return value; }
	public String asString() { return String.valueOf(value); }

	public void accept(CVisitor ser) { ser.value(value); }
	public Object raw() { return value; }

	public CharList toJSON(CharList sb, int depth) { return sb.append(value); }
	protected void toBinary(DynByteBuf w, VinaryParser struct) { w.put(Type.INTEGER.ordinal()).putInt(value); }
	public void toB_encode(DynByteBuf w) { w.put('i').putAscii(Integer.toString(value)).put('e'); }

	public int hashCode() { return value; }
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		CInteger that = (CInteger) o;
		return that.value == value;
	}
}