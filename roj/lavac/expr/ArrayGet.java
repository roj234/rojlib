package roj.lavac.expr;

import roj.asm.OpcodeUtil;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.compiler.ast.expr.ExprNode;
import roj.config.word.NotStatementException;
import roj.lavac.parser.MethodWriterL;

import javax.annotation.Nonnull;

/**
 * 操作符 - 获取数组某项
 *
 * @author Roj233
 * @since 2022/2/27 20:09
 */
final class ArrayGet implements LoadNode {
	private ExprNode array, index;

	ArrayGet(ExprNode array, ExprNode index) {
		this.array = array;
		this.index = index;
	}

	@Override
	public IType type() { return componentType(array.type()); }

	@Nonnull
	@Override
	public ExprNode resolve() {
		array = array.resolve();
		index = index.resolve();
		if (array.isConstant() && index.isConstant()) {
			return new Constant(type(), ((Object[])array.constVal())[((Number)index.constVal()).intValue()]);
		}
		return this;
	}

	@Override
	public void write(MethodWriterL cw, boolean noRet) {
		if (noRet) throw new NotStatementException();

		array.write(cw, false);
		index.write(cw, false);
		cw.checkCast(index.type(), Type.std(Type.INT)).cast(cw);

		byte storeType = (byte) OpcodeUtil.getByName().getInt(type().rawType().nativeName()+"ALOAD");
		cw.one(storeType);
	}

	@Override
	public void writeLoad(MethodWriterL tree) {
		this.array.write(tree, false);
		this.index.write(tree, false);
	}

	private static IType componentType(IType t) {
		IType type = t.clone();
		type.setArrayDim(type.array()-1);
		return type;
	}

	@Override
	public String toString() { return array.toString()+'['+index+']'; }

	@Override
	public boolean equalTo(Object left) {
		if (this == left) return true;
		if (!(left instanceof ArrayGet)) return false;
		ArrayGet get = (ArrayGet) left;
		return get.array.equalTo(array) && get.index.equalTo(index);
	}

	@Override
	public int hashCode() {
		int result = array.hashCode();
		result = 31 * result + index.hashCode();
		return result;
	}
}
