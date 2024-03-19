package roj.config.data;

import roj.config.serial.CVisitor;
import roj.text.CharList;

/**
 * @author Roj234
 * @since 2021/5/31 21:17
 */
public final class CNull extends CEntry {
	public static final CNull NULL = new CNull();
	private CNull() {}

	public Type getType() { return Type.NULL; }
	//public boolean mayCastTo(Type o) { return true; }

	public boolean asBool() { return false; }
	public int asInteger() { return 0; }
	public long asLong() { return 0; }
	public float asFloat() { return 0; }
	public double asDouble() { return 0; }
	public String asString() { return ""; }

	public CMap asMap() { return new CMap(); }
	public CList asList() { return new CList(); }

	public void accept(CVisitor ser) { ser.valueNull(); }
	public Object raw() { return null; }

	public CharList toJSON(CharList sb, int depth) { return sb.append("null"); }
}