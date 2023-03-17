package roj.config.exch;

import roj.config.NBTParser;
import roj.config.data.CEntry;
import roj.config.data.Type;
import roj.config.serial.CConsumer;
import roj.config.serial.Structs;
import roj.util.DynByteBuf;

import javax.annotation.Nonnull;

/**
 * !这个类不会被主动生成
 *
 * @author Roj234
 * @since 2022/3/21 14:59
 */
public final class TShort extends CEntry {
	public short value;

	public TShort(short number) {
		this.value = number;
	}

	public static TShort valueOf(short number) {
		return new TShort(number);
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
		return Type.Int2;
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
		TShort that = (TShort) o;
		return that.value == value;
	}

	@Override
	public boolean isSimilar(CEntry o) {
		return o.getType() == Type.Int2 || (o.getType().isSimilar(Type.Int2) && o.asInteger() == value);
	}

	@Override
	public int hashCode() {
		return value;
	}

	@Override
	public StringBuilder toJSON(StringBuilder sb, int depth) {
		return sb.append(value);
	}

	@Override
	public byte getNBTType() {
		return NBTParser.SHORT;
	}

	@Override
	public void toNBT(DynByteBuf w) {
		w.writeShort(value);
	}

	@Override
	public Object unwrap() {
		return value;
	}

	@Override
	public void toBinary(DynByteBuf w, Structs struct) {
		w.put((byte) Type.Int2.ordinal()).putInt(value);
	}

	@Override
	public void toB_encode(DynByteBuf w) {
		w.put((byte) 'i').putAscii(Integer.toString(value)).put((byte) 'e');
	}

	@Override
	public void forEachChild(CConsumer ser) {
		ser.value(value);
	}
}
