package roj.config.node;

import roj.config.ValueEmitter;
import roj.text.CharList;

import java.util.Collections;

/**
 * @author Roj234
 * @since 2021/5/31 21:17
 */
public final class NullValue extends ConfigValue {
	public static final NullValue NULL = new NullValue();
	private NullValue() {}

	public Type getType() {return Type.NULL;}

	private static final MapValue NULL_MAP = new MapValue(Collections.emptyMap());
	private static final ListValue NULL_LIST = new ListValue(Collections.emptyList());

	public boolean asBool() {return false;}
	public int asInt() {return 0;}
	public long asLong() {return 0;}
	public float asFloat() {return 0;}
	public double asDouble() {return 0;}
	public String asString() {return "";}
	public MapValue asMap() {return NULL_MAP;}
	public ListValue asList() {return NULL_LIST;}

	public void accept(ValueEmitter visitor) {visitor.emitNull();}
	public Object raw() {return null;}

	public CharList toJSON(CharList sb, int depth) {return sb.append("null");}
	public String toString() {return "null";}
}