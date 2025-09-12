package roj.config.node;

import org.jetbrains.annotations.NotNull;
import roj.config.TextEmitter;
import roj.config.ValueEmitter;
import roj.text.CharList;

/**
 * @author Roj234
 * @since 2022/3/11 20:18
 */
@Deprecated
public final class RawValue extends ConfigValue {
	private final CharSequence v;

	public RawValue(CharSequence s) { this.v = s; }
	public static RawValue valueOf(CharSequence s) { return new RawValue(s); }

	@NotNull
	@Override
	public Type getType() { throw new UnsupportedOperationException("RawValue是适用于特定配置格式的已序列化字符串"); }

	public void accept(ValueEmitter visitor) {
		if (!(visitor instanceof TextEmitter x)) getType();
		else x.mapValue().append(v);
	}
	public Object raw() { return getType(); }

	public CharList toJSON(CharList sb, int depth) { return sb.append(v); }
}