package roj.config.node;

import roj.config.ValueEmitter;
import roj.text.CharList;

/**
 * @author Roj234
 * @since 2021/5/31 21:17
 */
public final class BoolValue extends ConfigValue {
	public static final BoolValue TRUE = new BoolValue(), FALSE = new BoolValue();
	private BoolValue() {}

	public Type getType() { return Type.BOOL; }
	protected boolean eqVal(ConfigValue o) { return o.asBool() == asBool(); }
	public boolean mayCastTo(Type o) { return ((1 << o.ordinal()) & 0b10100) != 0; }

	public boolean asBool() { return this == TRUE; }
	public String asString() { return this == TRUE ? "true" : "false"; }

	public void accept(ValueEmitter visitor) {visitor.emit(this == TRUE);}
	public Object raw() {return this == TRUE;}
	public String toString() {return asString();}

	public CharList toJSON(CharList sb, int depth) { return sb.append(asString()); }
}