package roj.asm.type;

import roj.text.CharList;

/**
 * @author Roj234
 * @since 2022/11/1 10:47
 */
final class Placeholder implements IType {
	static final Placeholder I = new Placeholder();

	private Placeholder() {}

	@Override public byte genericType() { return PLACEHOLDER_TYPE; }
	@Override public void toDesc(CharList sb) {}
	@Override public void toString(CharList sb) {}
	@Override public String owner() { return "java/lang/Object"; }
	@Override public IType clone() { return I; }

	@Override
	public void validate(int position, int index) {
		if (position != TYPE_PARAMETER_ENV || index != 0) throw new IllegalStateException(this+"类型只能位于类型参数的第一项");
	}

	@Override public int hashCode() { return 114514191; }
	@Override public String toString() {return "<接口占位符>";}
}