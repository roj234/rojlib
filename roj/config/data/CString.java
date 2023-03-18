package roj.config.data;

import roj.config.IniParser;
import roj.config.NBTParser;
import roj.config.VinaryParser;
import roj.config.serial.CVisitor;
import roj.config.word.ITokenizer;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.DynByteBuf;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * @author Roj234
 * @since 2021/5/31 21:17
 */
public final class CString extends CEntry {
	public String value;

	public CString(String string) {
		// null check
		this.value = string.toString();
	}

	@Nonnull
	@Override
	public Type getType() {
		return Type.STRING;
	}

	public static CString valueOf(String s) {
		return new CString(s);
	}

	@Nonnull
	@Override
	public String asString() {
		return value;
	}

	@Override
	public double asDouble() {
		return Double.parseDouble(value);
	}

	@Override
	public int asInteger() {
		return TextUtil.parseInt(value);
	}

	@Override
	public long asLong() {
		return Long.parseLong(value);
	}

	@Override
	public boolean asBool() {
		return Boolean.parseBoolean(value);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		CString that = (CString) o;
		return Objects.equals(value, that.value);
	}

	@Override
	public int hashCode() {
		return value == null ? 0 : value.hashCode();
	}

	@Override
	public boolean isSimilar(CEntry o) {
		return o.getType() == Type.STRING || (o.getType().isSimilar(Type.STRING) && o.asString().equals(value));
	}

	//@Override
	protected CharList toXML(CharList sb, int depth) {
		return value == null ? sb.append("null") : !value.startsWith("<![CDATA[") && value.indexOf('<') >= 0 ? sb.append("<![CDATA[").append(value).append("]]>") : sb.append(value);
	}

	@Override
	public CharList toJSON(CharList sb, int depth) {
		return value == null ? sb.append("null") : ITokenizer.addSlashes(value, sb.append('"')).append('"');
	}

	@Override
	protected CharList toINI(CharList sb, int depth) {
		return IniParser.literalSafe(value) ? sb.append(value) : ITokenizer.addSlashes(value, sb.append('"')).append('"');
	}

	@Override
	public byte getNBTType() {
		return NBTParser.STRING;
	}

	@Override
	public Object unwrap() {
		return value;
	}

	@Override
	protected void toBinary(DynByteBuf w, VinaryParser struct) {
		w.put((byte) Type.STRING.ordinal()).putVStr(value);
	}

	@Override
	public void toB_encode(DynByteBuf w) {
		w.putAscii(Integer.toString(DynByteBuf.byteCountUTF8(value))).put((byte)':').putUTFData(value);
	}

	@Override
	public void forEachChild(CVisitor ser) {
		ser.value(value);
	}
}
