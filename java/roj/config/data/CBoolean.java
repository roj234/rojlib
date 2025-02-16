package roj.config.data;

import roj.config.serial.CVisitor;
import roj.text.CharList;

/**
 * @author Roj234
 * @since 2021/5/31 21:17
 */
public final class CBoolean extends CEntry {
	public static final CBoolean TRUE = new CBoolean(), FALSE = new CBoolean();
	private CBoolean() {}

	public Type getType() { return Type.BOOL; }
	protected boolean eqVal(CEntry o) { return o.asBool() == asBool(); }
	public boolean mayCastTo(Type o) { return ((1 << o.ordinal()) & 0b10100) != 0; }

	public boolean asBool() { return this == TRUE; }
	public String asString() { return this == TRUE ? "true" : "false"; }

	public void accept(CVisitor ser) {ser.value(this == TRUE);}
	public Object raw() {return this == TRUE;}
	public String toString() {return asString();}

	public CharList toJSON(CharList sb, int depth) { return sb.append(asString()); }
}