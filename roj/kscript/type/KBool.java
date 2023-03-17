package roj.kscript.type;

import javax.annotation.Nonnull;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/28 22:11
 */
public final class KBool extends KBase {
	public static final KBool TRUE = new KBool(), FALSE = new KBool();

	@Override
	public Type getType() {
		return Type.BOOL;
	}

	public static KBool valueOf(boolean b) {
		return b ? TRUE : FALSE;
	}

	private KBool() {}

	@Override
	public int asInt() {
		return this == TRUE ? 1 : 0;
	}

	@Override
	public double asDouble() {
		return this == TRUE ? 1 : 0;
	}

	@Nonnull
	@Override
	public String asString() {
		return this == TRUE ? "true" : "false";
	}

	@Override
	public boolean asBool() {
		return this == TRUE;
	}

	@Override
	public StringBuilder toString0(StringBuilder sb, int depth) {
		return sb.append(this == TRUE);
	}

	@Override
	public boolean equalsTo(KType b) {
		return b.canCastTo(Type.BOOL) && b.asBool() == (this == TRUE);
	}

	@Override
	public boolean canCastTo(Type type) {
		switch (type) {
			case BOOL:
			case DOUBLE:
			case INT:
			case STRING:
				return true;
		}
		return false;
	}
}
