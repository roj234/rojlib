package roj.config.node;

import org.jetbrains.annotations.NotNull;
import roj.config.ValueEmitter;
import roj.text.CharList;
import roj.text.TextUtil;

/**
 * @author Roj234
 * @since 2021/5/31 21:17
 */
public final class IntValue extends ConfigValue implements Comparable<ConfigValue> {
	public int value;
	public IntValue() {}
	public IntValue(int n) {value = n;}
	public static ConfigValue valueOf(String number) { return valueOf(TextUtil.parseInt(number)); }

	public Type getType() { return Type.INTEGER; }
	protected boolean eqVal(ConfigValue o) { return o.asInt() == value; }
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

	public void accept(ValueEmitter visitor) { visitor.emit(value); }
	public Object raw() { return value; }

	public CharList toJSON(CharList sb, int depth) {return sb.append(value);}
	public String toString() {return String.valueOf(value);}

	public int hashCode() {return value;}
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		IntValue that = (IntValue) o;
		return that.value == value;
	}

	public int compareTo(@NotNull ConfigValue o) {return o.mayCastTo(Type.INTEGER) ? Integer.compare(value, o.asInt()) : toString().compareTo(o.toString());}
}