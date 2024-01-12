package roj.config.exch;

import org.jetbrains.annotations.NotNull;
import roj.config.NBTParser;
import roj.config.VinaryParser;
import roj.config.data.CEntry;
import roj.config.data.Type;
import roj.config.serial.CVisitor;
import roj.text.CharList;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2022/3/21 14:59
 */
public final class TFloat extends CEntry {
	public float value;

	public TFloat(float v) {
		this.value = v;
	}
	public static TFloat valueOf(float v) {
		return new TFloat(v);
	}

	@NotNull
	@Override
	public Type getType() { return Type.Float4; }

	public boolean asBool() { return value != 0; }
	public double asDouble() { return value; }
	public int asInteger() { return (int) value; }
	public long asLong() { return (long) value; }
	public String asString() { return String.valueOf(value); }

	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		TFloat that = (TFloat) o;
		return that.value == value;
	}
	public int hashCode() { return Float.floatToRawIntBits(value) ^ 1919810; }

	public CharList toJSON(CharList sb, int depth) { return sb.append(value); }

	public byte getNBTType() { return NBTParser.FLOAT; }

	public Object unwrap() { return value; }
	protected void toBinary(DynByteBuf w, VinaryParser struct) { w.put((byte) Type.Float4.ordinal()).putFloat(value); }

	public void forEachChild(CVisitor ser) { ser.value(value); }
}