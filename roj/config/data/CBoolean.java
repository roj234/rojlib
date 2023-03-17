package roj.config.data;

import roj.config.NBTParser;
import roj.config.serial.CConsumer;
import roj.config.serial.Structs;
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
	public Type getType() {
		return Type.BOOL;
	}

	public static CEntry valueOf(boolean b) {
		return b ? TRUE : FALSE;
	}

	public static CEntry valueOf(String val) {
		return valueOf(Boolean.parseBoolean(val));
	}

	@Override
	public int asInteger() {
		return this == TRUE ? 1 : 0;
	}

	@Override
	public double asDouble() {
		return this == TRUE ? 1 : 0;
	}

	@Nonnull
	@Override
	public String asString() {
		return this == TRUE ? "true" : "false";
	}

	@Override
	public boolean asBool() {
		return this == TRUE;
	}

	@Override
	public boolean isSimilar(CEntry o) {
		return o.getType() == Type.BOOL || (o.getType().isSimilar(Type.BOOL) && o.asBool() == (this == TRUE));
	}

	@Override
	public StringBuilder toJSON(StringBuilder sb, int depth) {
		return sb.append(this == TRUE);
	}

	@Override
	public byte getNBTType() {
		return NBTParser.BYTE;
	}

	@Override
	public void toNBT(DynByteBuf w) {
		w.write(this == TRUE ? 1 : 0);
	}

	@Override
	public Object unwrap() {
		return this == TRUE;
	}

	@Override
	public void toBinary(DynByteBuf w, Structs struct) {
		w.put((byte) Type.BOOL.ordinal()).putBool(this == TRUE);
	}

	@Override
	public int hashCode() {
		return this == TRUE ? 432895 : 1278;
	}

	@Override
	public void forEachChild(CConsumer ser) {
		ser.value(this == TRUE);
	}
}
