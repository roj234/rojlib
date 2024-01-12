package roj.config.exch;

import org.jetbrains.annotations.NotNull;
import roj.config.VinaryParser;
import roj.config.data.CEntry;
import roj.config.data.Type;
import roj.config.serial.CVisitor;
import roj.text.CharList;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2022/3/11 20:18
 */
@Deprecated
public final class TRawString extends CEntry {
	private final CharSequence v;

	public TRawString(CharSequence s) { this.v = s; }
	public static TRawString valueOf(CharSequence s) { return new TRawString(s); }

	@NotNull
	@Override
	public Type getType() { throw new UnsupportedOperationException("TRawString是适用于特定配置格式的已序列化字符串"); }

	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		TRawString that = (TRawString) o;
		return v.equals(that.v);
	}
	public int hashCode() { return v.hashCode()+1; }

	public CharList toJSON(CharList sb, int depth) { return sb.append(v); }

	public Object unwrap() { return getType(); }
	protected void toBinary(DynByteBuf w, VinaryParser struct) { getType(); }

	public void forEachChild(CVisitor ser) { getType(); }
}