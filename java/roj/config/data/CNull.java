package roj.config.data;

import roj.config.serial.CVisitor;
import roj.text.CharList;

import java.util.Collections;

/**
 * @author Roj234
 * @since 2021/5/31 21:17
 */
public final class CNull extends CEntry {
	public static final CNull NULL = new CNull();
	private CNull() {}

	public Type getType() {return Type.NULL;}

	private static final CMap NULL_MAP = new CMap(Collections.emptyMap());
	private static final CList NULL_LIST = new CList(Collections.emptyList());

	public boolean asBool() {return false;}
	public int asInt() {return 0;}
	public long asLong() {return 0;}
	public float asFloat() {return 0;}
	public double asDouble() {return 0;}
	public String asString() {return "";}
	public CMap asMap() {return NULL_MAP;}
	public CList asList() {return NULL_LIST;}

	public void accept(CVisitor visitor) {
		visitor.valueNull();}
	public Object raw() {return null;}

	public CharList toJSON(CharList sb, int depth) {return sb.append("null");}
	public String toString() {return "null";}
}