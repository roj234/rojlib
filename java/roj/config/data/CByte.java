package roj.config.data;

import roj.config.serial.CVisitor;
import roj.text.CharList;

/**
 * @author Roj234
 * @since 2022/3/21 14:59
 */
public final class CByte extends CEntry {
	public byte value;

	public CByte(byte v) { this.value = v; }
	public static CByte valueOf(byte v) { return new CByte(v); }

	public Type getType() { return Type.Int1; }
	protected boolean eqVal(CEntry o) { return o.asInt() == value; }
	public boolean mayCastTo(Type o) { return ((1 << o.ordinal()) & 0b011111110100) != 0; }

	public boolean asBool() { return value != 0; }
	public double asDouble() { return value; }
	public int asInt() { return value; }
	public long asLong() { return value; }
	public String asString() { return String.valueOf(value); }

	public void accept(CVisitor ser) { ser.value(value); }
	public Object raw() { return value; }

	public CharList toJSON(CharList sb, int depth) { return sb.append(value); }

	public int hashCode() { return value ^ 114; }
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		CByte that = (CByte) o;
		return that.value == value;
	}
}