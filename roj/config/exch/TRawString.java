package roj.config.exch;

import roj.config.data.CEntry;
import roj.config.data.Type;
import roj.config.serial.CConsumer;
import roj.config.serial.Structs;
import roj.util.DynByteBuf;

import javax.annotation.Nonnull;

/**
 * 适用于特定配置格式的已序列化字符串
 *
 * @author Roj234
 * @since 2022/3/11 20:18
 */
public final class TRawString extends CEntry {
	private final CharSequence v;

	public TRawString(CharSequence string) {
		this.v = string;
	}

	@Nonnull
	@Override
	public Type getType() {
		throw new UnsupportedOperationException("TRawString是适用于特定配置格式的已序列化字符串");
	}

	public static TRawString valueOf(CharSequence s) {
		return new TRawString(s);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		TRawString that = (TRawString) o;
		return v.equals(that.v);
	}

	@Override
	public int hashCode() {
		return v.hashCode();
	}

	@Override
	public StringBuilder toJSON(StringBuilder sb, int depth) {
		return sb.append(v);
	}

	@Override
	public Object unwrap() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void toBinary(DynByteBuf w, Structs struct) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void forEachChild(CConsumer ser) {
		throw new UnsupportedOperationException();
	}
}
