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
public class CLong extends CEntry {
	public long value;

	public CLong(long number) {
		this.value = number;
	}

	public static CLong valueOf(long number) {
		return new CLong(number);
	}

	public static CLong valueOf(String number) {
		return valueOf(Long.parseLong(number));
	}

	@Override
	public final double asDouble() {
		return value;
	}

	@Override
	public final int asInteger() {
		return (int) value;
	}

	@Override
	public final long asLong() {
		return value;
	}

	@Nonnull
	@Override
	public Type getType() {
		return Type.LONG;
	}

	@Nonnull
	@Override
	public final String asString() {
		return String.valueOf(value);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		CLong that = (CLong) o;
		return that.value == value;
	}

	@Override
	public boolean isSimilar(CEntry o) {
		return o.getType() == Type.LONG || (o.getType().isSimilar(Type.LONG) && o.asLong() == value);
	}

	@Override
	public int hashCode() {
		return (int) value;
	}

	@Override
	public CharList toJSON(CharList sb, int depth) {
		return sb.append(value);
	}

	@Override
	public byte getNBTType() {
		return NBTParser.LONG;
	}

	@Override
	public Object unwrap() {
		return value;
	}

	@Override
	protected void toBinary(DynByteBuf w, VinaryParser struct) {
		w.put((byte) Type.LONG.ordinal()).putLong(value);
	}

	@Override
	public void toB_encode(DynByteBuf w) {
		w.put((byte) 'i').putAscii(Long.toString(value)).put((byte) 'e');
	}

	@Override
	public void forEachChild(CVisitor ser) {
		ser.value(value);
	}
}
