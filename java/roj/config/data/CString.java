package roj.config.data;

import roj.config.IniParser;
import roj.config.Tokenizer;
import roj.config.serial.CVisitor;
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

	public CString(String string) { value = string.toString(); }
	public static CString valueOf(String s) { return new CString(s); }

	public Type getType() { return Type.STRING; }
	protected boolean eqVal(CEntry o) { return o.asString().equals(value); }
	@Override
	public boolean mayCastTo(Type o) {
		//                          TDFLISBZNsML
		if (((1 << o.ordinal()) & 0b011111110100) == 0) return false;
		if (((1 << o.ordinal()) & 0b011111100000) != 0) {
			int num = TextUtil.isNumber(value);
			// !!not check range!! (since String is always cast-able)
			return num >= 0 && (num == 0 || o.ordinal() >= Type.Float4.ordinal());
		} else {
			// string or boolean
			return o == Type.STRING || value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false");
		}
	}

	public boolean asBool() { return value.equalsIgnoreCase("true") || !value.equalsIgnoreCase("false") && super.asBool(); }
	public int asInteger() { return TextUtil.isNumber(value) == 0 ? TextUtil.parseInt(value) : super.asInteger(); }
	public long asLong() { return TextUtil.isNumber(value) == 0 ? Long.parseLong(value) : super.asLong(); }
	public float asFloat() { return Float.parseFloat(value); }
	public double asDouble() { return Double.parseDouble(value); }
	public String asString() { return value; }

	@Override
	public void accept(CVisitor ser) { ser.value(value); }
	@Override
	public Object raw() { return value; }

	@Override
	protected CharList toJSON(CharList sb, int depth) { return Tokenizer.addSlashes(sb.append('"'), value).append('"'); }

	@Override
	public void toB_encode(DynByteBuf w) { w.putAscii(Integer.toString(DynByteBuf.byteCountUTF8(value))).put(':').putUTFData(value); }
	@Override
	protected CharList toINI(CharList sb, int depth) { return IniParser.literalSafe(value) ? sb.append(value) : Tokenizer.addSlashes(sb.append('"'), value).append('"'); }

	@Override
	public int hashCode() { return value.hashCode(); }
	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		CString that = (CString) o;
		return Objects.equals(value, that.value);
	}
}