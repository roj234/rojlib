package roj.plugins.kscript.func;

import roj.config.data.CEntry;
import roj.config.data.Type;
import roj.config.serial.CVisitor;
import roj.text.CharList;

/**
 * @author Roj234
 * @since 2021/5/31 21:17
 */
public final class KSNull extends CEntry {
	public static final KSNull NULL = new KSNull();
	private KSNull() {}

	public Type getType() {return Type.NULL;}

	public boolean asBool() {return false;}
	public String asString() {return "null";}

	public void accept(CVisitor ser) {ser.valueNull();}
	public Object raw() {return null;}

	public CharList toJSON(CharList sb, int depth) {return sb.append("null");}
}