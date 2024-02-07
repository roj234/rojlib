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
public final class TShort extends CEntry {
	public short value;

	public TShort(short s) { this.value = s; }
	public static TShort valueOf(short number) { return new TShort(number); }

	@NotNull
	@Override
	public Type getType() { return Type.Int2; }

	public boolean asBool() { return value != 0; }
	public double asDouble() { return value; }
	public int asInteger() { return value; }
	public long asLong() { return value; }
	public String asString() { return String.valueOf(value); }

	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		TShort that = (TShort) o;
		return that.value == value;
	}
	public int hashCode() { return value ^ 1919; }

	public CharList toJSON(CharList sb, int depth) { return sb.append(value); }

	public byte getNBTType() { return NBTParser.SHORT; }

	public Object unwrap() { return value; }
	protected void toBinary(DynByteBuf w, VinaryParser struct) { w.put((byte) Type.Int2.ordinal()).putShort(value); }
	public void toB_encode(DynByteBuf w) { w.put((byte) 'i').putAscii(Integer.toString(value)).put((byte) 'e'); }

	public void forEachChild(CVisitor ser) { ser.value(value); }
}