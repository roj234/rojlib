package roj.asm.type;

import roj.text.CharList;

/**
 * @author Roj234
 * @since 2022/11/1 0001 10:47
 */
final class EmptyClass implements IType {
	static final EmptyClass I = new EmptyClass();

	private EmptyClass() {}

	@Override
	public byte genericType() { return EMPTY_TYPE; }
	@Override
	public void toDesc(CharList sb) {}
	@Override
	public void toString(CharList sb) {}
	@Override
	public String owner() { return "java/lang/Object"; }

	@Override
	public IType clone() { return I; }

	@Override
	public void checkPosition(int env, int pos) {
		if (env != TYPE_PARAMETER_ENV || pos != 0) throw new IllegalStateException("'EmptyClass' can only be used in TypeParameter[0]");
	}

	@Override
	public int hashCode() { return 114514191; }
	@Override
	public String toString() { return "<generic empty class placeholder>"; }
}
