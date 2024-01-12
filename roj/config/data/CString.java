package roj.config.data;

import org.jetbrains.annotations.NotNull;
import roj.config.IniParser;
import roj.config.NBTParser;
import roj.config.VinaryParser;
import roj.config.serial.CVisitor;
import roj.config.word.ITokenizer;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.DynByteBuf;

import java.util.Objects;

/**
 * @author Roj234
 * @since 2021/5/31 21:17
 */
public final class CString extends CEntry {
	public String value;

	public static CString valueOf(String s) { return new CString(s); }
	public CString(String string) {
		// null check
		this.value = string.toString();
	}

	@NotNull
	@Override
	public Type getType() { return Type.STRING; }

	@Override
	protected boolean isNumber() { return TextUtil.isNumber(value) >= 0; }
	@NotNull
	@Override
	public String asString() { return value; }
	@Override
	public double asDouble() { return Double.parseDouble(value); }
	@Override
	public int asInteger() { return TextUtil.isNumber(value) == 0 ? TextUtil.parseInt(value) : (int) asDouble(); }
	@Override
	public long asLong() { return TextUtil.isNumber(value) == 0 ? Long.parseLong(value) : (long) asDouble(); }
	@Override
	public boolean asBool() { return Boolean.parseBoolean(value); }

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		CString that = (CString) o;
		return Objects.equals(value, that.value);
	}

	@Override
	public int hashCode() { return value == null ? 0 : value.hashCode(); }

	@Override
	public boolean isSimilar(CEntry o) { return o.getType() == Type.STRING || (o.getType().isSimilar(Type.STRING) && o.asString().equals(value)); }

	@Override
	public CharList toJSON(CharList sb, int depth) { return value == null ? sb.append("null") : ITokenizer.addSlashes(sb.append('"'), value).append('"'); }

	@Override
	protected CharList toINI(CharList sb, int depth) { return IniParser.literalSafe(value) ? sb.append(value) : ITokenizer.addSlashes(sb.append('"'), value).append('"'); }

	@Override
	public byte getNBTType() { return NBTParser.STRING; }

	@Override
	public Object unwrap() { return value; }

	@Override
	protected void toBinary(DynByteBuf w, VinaryParser struct) { w.put((byte) Type.STRING.ordinal()).putVUIGB(value); }

	@Override
	public void toB_encode(DynByteBuf w) { w.putAscii(Integer.toString(DynByteBuf.byteCountUTF8(value))).put((byte)':').putUTFData(value); }

	@Override
	public void forEachChild(CVisitor ser) { ser.value(value); }
}