package roj.config.data;

import org.jetbrains.annotations.NotNull;
import roj.config.VinaryParser;
import roj.config.serial.CVisitor;
import roj.text.CharList;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2021/5/31 21:17
 */
public final class CNull extends CEntry {
	public static final CNull NULL = new CNull();

	private CNull() {}

	@NotNull
	@Override
	public Type getType() {
		return Type.NULL;
	}

	@NotNull
	public String asString() {
		return "";
	}

	@Override
	public double asDouble() {
		return 0;
	}

	@Override
	public boolean asBool() {
		return false;
	}

	@Override
	public int asInteger() {
		return 0;
	}

	@Override
	public long asLong() {
		return 0;
	}

	@NotNull
	@Override
	public CMapping asMap() {
		return new CMapping();
	}

	@NotNull
	@Override
	public CList asList() {
		return new CList();
	}

	@Override
	public CharList toJSON(CharList sb, int depth) {
		return sb.append("null");
	}

	@Override
	public Object unwrap() {
		return null;
	}

	@Override
	protected void toBinary(DynByteBuf w, VinaryParser struct) {
		w.put((byte) Type.NULL.ordinal());
	}

	@Override
	public boolean equals(Object obj) {
		return obj == NULL;
	}

	@Override
	public boolean isSimilar(CEntry o) {
		return o == this;
	}

	@Override
	public int hashCode() {
		return 348764;
	}

	@Override
	public void forEachChild(CVisitor ser) {
		ser.valueNull();
	}
}