package roj.config.data;

import roj.config.serial.CVisitor;
import roj.text.CharList;

/**
 * @author Roj234
 * @since 2025/3/13 6:14
 */
public final class CChar extends CEntry {
	public char value;
	public CChar() {}
	public CChar(char v) {this.value = v;}

	public Type getType() {return Type.CHAR;}
	protected boolean eqVal(CEntry o) {return o.asInt() == value;}
	public boolean mayCastTo(Type o) {
		if (((1 << o.ordinal()) & 0b01011110000100) != 0) return true;
		return switch (o) {
			case Int1 -> value <= 127;
			case Int2 -> value <= 32767;
			default -> false;
		};
	}

	public char asChar() {return value;}
	public int asInt() {return value;}
	public long asLong() {return value;}
	public float asFloat() {return value;}
	public double asDouble() {return value;}
	public String asString() {return String.valueOf(value);}

	public void accept(CVisitor visitor) {
		visitor.value(value);}
	public Object raw() {return value;}

	public CharList toJSON(CharList sb, int depth) {return sb.append('\'').append(value).append('\'');}
	public String toString() {return "'"+value+"'";}

	public int hashCode() {return value * 191981;}
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		CChar that = (CChar) o;
		return that.value == value;
	}
}