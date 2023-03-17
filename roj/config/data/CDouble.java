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
public final class CDouble extends CEntry {
	public double value;

	public CDouble(double number) {
		this.value = number;
	}

	public static CDouble valueOf(double number) {
		return new CDouble(number);
	}

	public static CDouble valueOf(String number) {
		return valueOf(Double.parseDouble(number));
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
		return Type.DOUBLE;
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
		CDouble that = (CDouble) o;
		return Double.compare(that.value, value) == 0;
	}

	@Override
	public boolean isSimilar(CEntry o) {
		return o.getType() == Type.DOUBLE || (o.getType().isSimilar(Type.DOUBLE) && o.asDouble() == value);
	}

	@Override
	public int hashCode() {
		return Float.floatToIntBits((float) value);
	}

	@Override
	public CharList toJSON(CharList sb, int depth) {
		return sb.append(value);
	}

	@Override
	public byte getNBTType() {
		return NBTParser.DOUBLE;
	}

	@Override
	public Object unwrap() {
		return value;
	}

	@Override
	protected void toBinary(DynByteBuf w, VinaryParser struct) {
		w.put((byte) Type.DOUBLE.ordinal()).putDouble(value);
	}

	@Override
	public void forEachChild(CVisitor ser) {
		ser.value(value);
	}
}
