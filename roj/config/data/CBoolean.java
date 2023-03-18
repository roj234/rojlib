package roj.config.data;

import roj.config.NBTParser;
import roj.config.VinaryParser;
import roj.config.serial.CVisitor;
import roj.text.CharList;
import roj.util.DynByteBuf;

import javax.annotation.Nonnull;

/**
 * @author Roj234
 * @since 2021/5/31 21:17
 */
public final class CBoolean extends CEntry {
	public static final CBoolean TRUE = new CBoolean(), FALSE = new CBoolean();

	private CBoolean() {}

	@Nonnull
	@Override
	public Type getType() { return Type.BOOL; }

	public static CEntry valueOf(boolean b) { return b ? TRUE : FALSE; }
	public static CEntry valueOf(String val) { return valueOf(Boolean.parseBoolean(val)); }

	public int asInteger() { return this == TRUE ? 1 : 0; }
	public double asDouble() { return this == TRUE ? 1 : 0; }
	public String asString() { return this == TRUE ? "true" : "false"; }
	public boolean asBool() { return this == TRUE; }

	@Override
	public boolean isSimilar(CEntry o) {
		return o.getType() == Type.BOOL || (o.getType().isSimilar(Type.BOOL) && o.asBool() == (this == TRUE));
	}

	@Override
	public CharList toJSON(CharList sb, int depth) { return sb.append(asString()); }

	@Override
	public byte getNBTType() {
		return NBTParser.BYTE;
	}

	@Override
	public Object unwrap() {
		return this == TRUE;
	}

	@Override
	protected void toBinary(DynByteBuf w, VinaryParser struct) {
		w.put((byte) Type.BOOL.ordinal()).putBool(this == TRUE);
	}

	@Override
	public int hashCode() {
		return asInteger();
	}

	@Override
	public void forEachChild(CVisitor ser) {
		ser.value(this == TRUE);
	}
}
