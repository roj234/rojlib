package roj.lavac.expr;

import roj.asm.tree.anno.AnnValArray;
import roj.asm.tree.anno.AnnValInt;
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
	ASTNode array, index;

	public ArrayGet(ASTNode array, ASTNode index) {
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
	public ASTNode compress() {
		array = array.compress();
		index = index.compress();
		if (array.isConstant() && index.isConstant()) {
			return new LDC(((AnnValArray) array.asCst().val()).value.get(((AnnValInt) index.asCst().val()).value));
		}
		return this;
	}

	@Override
	public Type type() {
		return componentType(array.type());
	}

	private static Type componentType(Type t) {
		return t.owner != null ? new Type(t.owner, t.array()-1) : t.array() == 1 ? Type.std(t.type) : new Type(t.type, t.array()-1);
	}

	@Override
	public String toString() {
		return array.toString() + '[' + index.toString() + ']';
	}

	@Override
	public boolean isEqual(ASTNode left) {
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
