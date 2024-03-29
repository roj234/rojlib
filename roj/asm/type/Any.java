package roj.asm.type;

import roj.text.CharList;

/**
 * @author Roj234
 * @since 2022/11/1 0001 11:04
 */
final class Any implements IType {
	static final Any I = new Any();

	private Any() {}

	@Override
	public byte genericType() {
		return ANY_TYPE;
	}

	@Override
	public void toDesc(CharList sb) {
		sb.append("*");
	}

	@Override
	public void toString(CharList sb) {
		sb.append("?");
	}

	@Override
	public void checkPosition(int env, int pos) {
		if (env != GENERIC_ENV) throw new IllegalStateException("'Any' can only be used in Generic");
	}

	@Override
	public int hashCode() {
		return 1145141919;
	}

	@Override
	public String toString() {
		return "<generic 'any'>";
	}
}
