package roj.lavac.expr;

import roj.asm.type.Type;
import roj.config.word.NotStatementException;
import roj.lavac.parser.MethodPoetL;

import javax.annotation.Nonnull;

/**
 * 操作符 - 获取数组某项
 *
 * @author Roj233
 * @since 2022/2/27 20:09
 */
public final class ArrayGet implements LoadExpression {
	Expression array, index;

	public ArrayGet(Expression array, Expression index) {
		this.array = array;
		this.index = index;
	}

	@Override
	public void write(MethodPoetL tree, boolean noRet) {
		if (noRet) throw new NotStatementException();

		array.write(tree, false);
		index.write(tree, false);
		tree.arrayLoad();
	}

	@Nonnull
	@Override
	public Expression compress() {
		array = array.compress();
		index = index.compress();
		if (array.isConstant() && index.isConstant()) {
			return new Constant(type(), ((Object[])array.constVal())[((Number)index.constVal()).intValue()]);
		}
		return this;
	}

	@Override
	public Type type() {
		return componentType(array.type());
	}

	private static Type componentType(Type t) {
		Type type = t.clone();
		type.setArrayDim(type.array()-1);
		return type;
	}

	@Override
	public String toString() {
		return array.toString() + '[' + index.toString() + ']';
	}

	@Override
	public boolean isEqual(Expression left) {
		if (this == left) return true;
		if (!(left instanceof ArrayGet)) return false;
		ArrayGet get = (ArrayGet) left;
		return get.array.isEqual(array) && get.index.isEqual(index);
	}

	@Override
	public void write2(MethodPoetL tree) {
		this.array.write(tree, false);
		this.index.write(tree, false);
	}

	@Override
	public byte loadType() {
		return ARRAY;
	}
}
