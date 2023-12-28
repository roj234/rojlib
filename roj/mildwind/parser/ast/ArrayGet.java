package roj.mildwind.parser.ast;

import roj.asm.Opcodes;
import roj.config.word.NotStatementException;
import roj.mildwind.JsContext;
import roj.mildwind.asm.JsMethodWriter;
import roj.mildwind.type.JsObject;
import roj.mildwind.type.Type;

import javax.annotation.Nonnull;

/**
 * 操作符 - 获取对象可变名称属性
 *
 * @author Roj233
 * @since 2020/10/13 22:17
 */
final class ArrayGet implements LoadExpression {
	Expression array, index;
	boolean delete;

	public ArrayGet(Expression array, Expression index) {
		this.array = array;
		this.index = index;
	}

	public void writeLoad(JsMethodWriter tree) {
		array.write(tree, false);
		index.write(tree, false);
	}
	public void writeExecute(JsMethodWriter tree, boolean noRet) {
		if (index.type() == Type.INT) {
			tree.invokeV("roj/mildwind/type/JsObject", "asInt", "()I");
			if (delete) {
				tree.invokeV("roj/mildwind/type/JsObject", "delByInt", "(I)Z");
				if (noRet) tree.one(Opcodes.POP);
			} else {
				if (noRet) throw new NotStatementException();
				tree.invokeV("roj/mildwind/type/JsObject", "getByInt", "(I)Lroj/mildwind/type/JsObject;");
			}
		} else {
			tree.invokeV("java/lang/Object", "toString", "()Ljava/lang/String;");
			if (delete) {
				tree.invokeV("roj/mildwind/type/JsObject", "del", "(Ljava/lang/String;)Z");
				if (noRet) tree.one(Opcodes.POP);
			} else {
				if (noRet) throw new NotStatementException();
				tree.invokeV("roj/mildwind/type/JsObject", "get", "(Ljava/lang/String;)Lroj/mildwind/type/JsObject;");
			}
		}
	}

	@Nonnull
	@Override
	public Expression compress() {
		array = array.compress();
		index = index.compress();
		/*if (array.isConstant()&&index.isConstant()) {
			return Constant.valueOf(array.constVal().get(index.constVal().toString()));
		}*/
		return this;
	}

	public boolean setDeletion() { return delete = true; }

	@Override
	public Type type() { return array.isConstant()&&index.isConstant() ? array.constVal().get(index.toString()).type() : Type.OBJECT; }

	@Override
	public String toString() { return array.toString()+'['+index+']'; }

	@Override
	public boolean isEqual(Expression left) {
		if (this == left) return true;
		if (!(left instanceof ArrayGet)) return false;
		ArrayGet get = (ArrayGet) left;
		return get.array.isEqual(array) && get.index.isEqual(index);
	}

	@Override
	public JsObject compute(JsContext ctx) {
		JsObject arr = array.compute(ctx);
		JsObject idx = index.compute(ctx);
		return idx.type() == Type.INT ? arr.getByInt(idx.asInt()) : arr.get(idx.toString());
	}

	@Override
	public void computeAssign(JsContext ctx, JsObject val) {
		JsObject arr = array.compute(ctx);
		JsObject idx = index.compute(ctx);

		if (idx.type() == Type.INT) arr.putByInt(idx.asInt(), val);
		else arr.put(idx.toString(), val);
	}
}