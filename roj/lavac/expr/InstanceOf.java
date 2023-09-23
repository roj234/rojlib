package roj.lavac.expr;

import roj.asm.Opcodes;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.config.word.NotStatementException;
import roj.lavac.parser.MethodWriterL;

/**
 * @author Roj234
 * @since 2022/2/24 19:55
 */
class InstanceOf implements Expression {
	private final Type type;
	private final Expression left;

	public InstanceOf(Type type, Expression left) {
		this.type = type;
		this.left = left;
	}

	@Override
	public IType type() { return Type.std(Type.BOOLEAN); }

	@Override
	public Expression resolve() {
		if (!left.isConstant()) return this;
		try {
			// todo MapUtil -> better solution
			return Constant.valueOf(type.toJavaClass().isInstance(left.constVal()));
		} catch (Throwable e) {
			return this;
		}
	}

	@Override
	public void write(MethodWriterL cw, boolean noRet) throws NotStatementException {
		left.write(cw, false);
		cw.clazz(Opcodes.INSTANCEOF, type);
		// absolute is
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		InstanceOf of = (InstanceOf) o;

		if (!type.equals(of.type)) return false;
		return left.equals(of.left);
	}

	@Override
	public int hashCode() {
		int result = type.hashCode();
		result = 31 * result + left.hashCode();
		return result;
	}
}
