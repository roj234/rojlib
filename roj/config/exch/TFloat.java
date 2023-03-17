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
public final class TFloat extends CEntry {
	public float value;

	public TFloat(float number) {
		this.value = number;
	}

	public static TFloat valueOf(float number) {
		return new TFloat(number);
	}

	@Override
	public double asDouble() {
		return value;
	}

	@Override
	public int asInteger() {
		return (int) value;
	}

	@Override
	public long asLong() {
		return (long) value;
	}

	@Nonnull
	@Override
	public Type getType() {
		return Type.Float4;
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
		TFloat that = (TFloat) o;
		return that.value == value;
	}

	@Override
	public boolean isSimilar(CEntry o) {
		return o.getType() == Type.Float4 || (o.getType().isSimilar(Type.Float4) && (float) o.asDouble() == value);
	}

	@Override
	public int hashCode() {
		return Float.floatToRawIntBits(value);
	}

	@Override
	public StringBuilder toJSON(StringBuilder sb, int depth) {
		return sb.append(value);
	}

	@Override
	public byte getNBTType() {
		return NBTParser.FLOAT;
	}

	@Override
	public void toNBT(DynByteBuf w) {
		w.writeFloat(value);
	}

	@Override
	public Object unwrap() {
		return value;
	}

	@Override
	public void toBinary(DynByteBuf w, Structs struct) {
		w.put((byte) Type.Float4.ordinal()).putFloat(value);
	}

	@Override
	public void forEachChild(CConsumer ser) {
		ser.value(value);
	}
}
