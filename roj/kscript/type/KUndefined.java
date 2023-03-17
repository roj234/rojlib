package roj.kscript.type;

import javax.annotation.Nonnull;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/28 22:12
 */
public final class KUndefined extends KBase {
	public static final KUndefined UNDEFINED = new KUndefined();

	private KUndefined() {}

	@Override
	public Type getType() {
		return Type.UNDEFINED;
	}

	@Override
	public StringBuilder toString0(StringBuilder sb, int depth) {
		return sb.append("undefined");
	}

	@Override
	public boolean equalsTo(KType b) {
		return b == UNDEFINED || b == KNull.NULL;
	}

	@Override
	public boolean equals(Object obj) {
		return obj == UNDEFINED;
	}

	@Override
	public boolean asBool() {
		return false;
	}

	@Nonnull
	@Override
	public String asString() {
		return "undefined";
	}

	@Override
	public int hashCode() {
		return 235789;
	}

	@Override
	public boolean canCastTo(Type type) {
		return false;
	}
}
