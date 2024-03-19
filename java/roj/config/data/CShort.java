package roj.config.data;

import roj.config.serial.CVisitor;
import roj.text.CharList;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2022/3/21 14:59
 */
public final class CShort extends CEntry {
	public short value;

	public CShort(short s) { this.value = s; }
	public static CShort valueOf(short number) { return new CShort(number); }

	public Type getType() { return Type.Int2; }
	protected boolean eqVal(CEntry o) { return o.asInteger() == value; }
	public boolean mayCastTo(Type o) {
		if (((1 << o.ordinal()) & 0b01111101100) != 0) return true;
		if (o == Type.Int1) return value >= -128 && value <= 127;
		return false;
	}

	public boolean asBool() { return value != 0; }
	public double asDouble() { return value; }
	public int asInteger() { return value; }
	public float asFloat() { return value; }
	public long asLong() { return value; }
	public String asString() { return String.valueOf(value); }

	public void accept(CVisitor ser) { ser.value(value); }
	public Object raw() { return value; }

	public CharList toJSON(CharList sb, int depth) { return sb.append(value); }

	public void toB_encode(DynByteBuf w) { w.put('i').putAscii(Integer.toString(value)).put('e'); }

	public int hashCode() { return value ^ 1919; }
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		CShort that = (CShort) o;
		return that.value == value;
	}
}