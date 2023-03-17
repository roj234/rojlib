package roj.config.data;

import roj.config.NBTParser;
import roj.config.VinaryParser;
import roj.config.serial.CVisitor;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.DynByteBuf;

import javax.annotation.Nonnull;

/**
 * @author Roj234
 * @since 2021/5/31 21:17
 */
public final class CInteger extends CEntry {
	public int value;

	public CInteger(int number) {
		this.value = number;
	}

	public static CInteger valueOf(int number) {
		return new CInteger(number);
	}

	public static CInteger valueOf(String number) {
		return valueOf(TextUtil.parseInt(number));
	}

	@Override
	public boolean asBool() {
		return value != 0;
	}

	@Override
	public double asDouble() {
		return value;
	}

	@Override
	public int asInteger() {
		return value;
	}

	@Override
	public long asLong() {
		return value;
	}

	@Nonnull
	@Override
	public Type getType() {
		return Type.INTEGER;
	}

	@Nonnull
	@Override
	public String asString() {
		return String.valueOf(value);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		CInteger that = (CInteger) o;
		return that.value == value;
	}

	@Override
	public boolean isSimilar(CEntry o) {
		return o.getType() == Type.INTEGER || (o.getType().isSimilar(Type.INTEGER) && o.asInteger() == value);
	}

	@Override
	public int hashCode() {
		return value;
	}

	@Override
	public CharList toJSON(CharList sb, int depth) {
		return sb.append(value);
	}

	@Override
	public byte getNBTType() {
		return NBTParser.INT;
	}

	@Override
	public Object unwrap() {
		return value;
	}

	@Override
	protected void toBinary(DynByteBuf w, VinaryParser struct) {
		w.put((byte) Type.INTEGER.ordinal()).putInt(value);
	}

	@Override
	public void toB_encode(DynByteBuf w) {
		w.put((byte) 'i').putAscii(Integer.toString(value)).put((byte) 'e');
	}

	@Override
	public void forEachChild(CVisitor ser) {
		ser.value(value);
	}
}
