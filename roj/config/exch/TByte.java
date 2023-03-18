package roj.config.exch;

import roj.config.NBTParser;
import roj.config.VinaryParser;
import roj.config.data.CEntry;
import roj.config.data.Type;
import roj.config.serial.CVisitor;
import roj.text.CharList;
import roj.util.DynByteBuf;

import javax.annotation.Nonnull;

/**
 * @author Roj234
 * @since 2022/3/21 14:59
 */
public final class TByte extends CEntry {
	public byte value;

	public TByte(byte v) { this.value = v; }
	public static TByte valueOf(byte v) { return new TByte(v); }

	@Nonnull
	@Override
	public Type getType() { return Type.Int1; }

	public boolean asBool() { return value != 0; }
	public double asDouble() { return value; }
	public int asInteger() { return value; }
	public long asLong() { return value; }
	public String asString() { return String.valueOf(value); }

	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		TByte that = (TByte) o;
		return that.value == value;
	}
	public int hashCode() { return value ^ 114; }

	public CharList toJSON(CharList sb, int depth) { return sb.append(value); }

	public byte getNBTType() { return NBTParser.BYTE; }

	public Object unwrap() { return value; }
	protected void toBinary(DynByteBuf w, VinaryParser struct) { w.put((byte) Type.Int1.ordinal()).put(value); }
	public void toB_encode(DynByteBuf w) { w.put((byte) 'i').putAscii(Integer.toString(value)).put((byte) 'e'); }

	public void forEachChild(CVisitor ser) { ser.value(value); }
}
