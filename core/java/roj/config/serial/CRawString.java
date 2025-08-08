package roj.config.serial;

import org.jetbrains.annotations.NotNull;
import roj.config.data.CEntry;
import roj.config.data.Type;
import roj.text.CharList;

/**
 * @author Roj234
 * @since 2022/3/11 20:18
 */
@Deprecated
public final class CRawString extends CEntry {
	private final CharSequence v;

	public CRawString(CharSequence s) { this.v = s; }
	public static CRawString valueOf(CharSequence s) { return new CRawString(s); }

	@NotNull
	@Override
	public Type getType() { throw new UnsupportedOperationException("TRawString是适用于特定配置格式的已序列化字符串"); }

	public void accept(CVisitor visitor) {
		if ((visitor instanceof ToSomeString x)) {
			x.preValue(false);
			x.sb.append(v);
		} else getType();
	}
	public Object raw() { return getType(); }

	public CharList toJSON(CharList sb, int depth) { return sb.append(v); }
}