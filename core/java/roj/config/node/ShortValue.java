package roj.config.node;

import roj.config.ValueEmitter;
import roj.text.CharList;

/**
 * @author Roj234
 * @since 2022/3/21 14:59
 */
public final class ShortValue extends ConfigValue {
	public short value;
	public ShortValue() {}
	public ShortValue(short s) { this.value = s; }

	public Type getType() { return Type.Int2; }
	protected boolean eqVal(ConfigValue o) { return o.asInt() == value; }
	public boolean mayCastTo(Type o) {
		if (((1 << o.ordinal()) & 0b01111101100) != 0) return true;
		if (o == Type.Int1) return value >= -128 && value <= 127;
		return false;
	}

	public boolean asBool() { return value != 0; }
	public int asInt() { return value; }
	public long asLong() { return value; }
	public float asFloat() { return value; }
	public double asDouble() { return value; }
	public String asString() { return String.valueOf(value); }

	public void accept(ValueEmitter visitor) { visitor.emit(value); }
	public Object raw() { return value; }

	public CharList toJSON(CharList sb, int depth) { return sb.append(value); }
	public String toString() {return String.valueOf(value).concat("S");}

	public int hashCode() { return value * 1919; }
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		ShortValue that = (ShortValue) o;
		return that.value == value;
	}
}