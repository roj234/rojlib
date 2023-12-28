package roj.mildwind.parser.ast;

import roj.asm.Opcodes;
import roj.mildwind.JsContext;
import roj.mildwind.asm.JsMethodWriter;
import roj.mildwind.parser.JSLexer;
import roj.mildwind.type.JsObject;
import roj.mildwind.type.Type;

/**
 * 操作符-赋值
 *
 * @author Roj233
 * @since 2020/10/15 13:01
 */
final class Assign implements Expression {
	LoadExpression left;
	Expression right;

	public Assign(LoadExpression left, Expression right) {
		this.left = left;
		this.right = right;
	}

	public Type type() { return right.type(); }

	@Override
	@SuppressWarnings("fallthrough")
	public void write(JsMethodWriter tree, boolean noRet) {
		boolean var = left instanceof Variable;

		check:
		if (right instanceof Binary) {
			Binary bin = (Binary) right;

			Expression rv;
			// a.b.c = a.b.c <operator> nn
			if (bin.left.isEqual(left)) rv = bin.right;
			// a.b.c = nn <operator> a.b.c 需要left=const避免和执行顺序有关的side-effect
			else if (bin.right.isEqual(left) && bin.left.isConstant()) rv = bin.left;
			// nothing same..
			else break check;

			switch (bin.op) {
				case JSLexer.logic_or: case JSLexer.logic_and: case JSLexer.nullish_coalescing: break;
				case JSLexer.add: case JSLexer.dec:
					// 'i = i+1' (same as 'i += 1', but not i++)
					if (rv.isConstant() && rv.constVal().type().numOrBool()) {
						writeIncrement(left, tree, noRet, false, rv.constVal().asDouble(), rv.constVal().type() == Type.DOUBLE);
						return;
					}
				default:
					// etc. k = k * 3;
					if (!var) {
						boolean intKey = checkGetKey(left, tree);

						rv.write(tree, false);
						bin.writeOperator(tree);

						checkSetKey(tree, intKey);
						return;
					}
			}
		}

		if (var) {
			if (right.isConstant()) {
				int fid = tree.variables.set(((Variable) left).name, right.constVal());
				if (fid >= 0) {
					tree.load(fid);
					return;
				}
			}

			right.write(tree, false);
			if (!noRet) tree.one(Opcodes.DUP);

			tree.variables.set(((Variable) left).name);
			return;
		}

		left.writeLoad(tree);

		boolean intKey = left instanceof ArrayGet && ((ArrayGet) left).index.type() == Type.INT;
		if (intKey) tree.invokeV("roj/mildwind/type/JsObject", "asInt", "()I");
		else tree.invokeV("java/lang/Object", "toString", "()Ljava/lang/String;");

		right.write(tree, false);

		if (!noRet) tree.one(Opcodes.DUP_X2);
		checkSetKey(tree, intKey);
	}

	static void writeIncrement(LoadExpression expr, JsMethodWriter tree, boolean noRet, boolean returnBefore, double dt, boolean isDouble) {
		if (expr instanceof Variable) {
			Variable v = (Variable) expr;
			v.write(tree, false);

			if (!noRet&returnBefore) tree.one(Opcodes.DUP);

			if (isDouble) tree.ldc(dt);
			else tree.ldc((int) dt);
			tree.invokeV("roj/mildwind/type/JsObject", "op_inc", "(I)Lroj/mildwind/type/JsObject;");

			if (!noRet&!returnBefore) tree.one(Opcodes.DUP);

			tree.variables.set(v.name);
			return;
		}

		boolean intKey = checkGetKey(expr, tree);

		// [obj] arr idx obj
		if (!noRet&returnBefore) tree.one(Opcodes.DUP_X2);

		if (isDouble) {
			tree.ldc(dt);
			tree.invokeV("roj/mildwind/type/JsObject", "op_inc", "(D)Lroj/mildwind/type/JsObject;");
		} else {
			tree.ldc((int) dt);
			tree.invokeV("roj/mildwind/type/JsObject", "op_inc", "(I)Lroj/mildwind/type/JsObject;");
		}

		if (!noRet&!returnBefore) tree.one(Opcodes.DUP_X2);

		checkSetKey(tree, intKey);
	}

	private static boolean checkGetKey(LoadExpression expr, JsMethodWriter tree) {
		expr.writeLoad(tree);
		if (expr instanceof ArrayGet && ((ArrayGet) expr).index.type() == Type.INT) {
			tree.invokeV("roj/mildwind/type/JsObject", "asInt", "()I");
			tree.one(Opcodes.DUP2);
			tree.invokeV("roj/mildwind/type/JsObject", "getByInt", "(I)Lroj/mildwind/type/JsObject;");
			return true;
		} else {
			tree.invokeV("java/lang/Object", "toString", "()Ljava/lang/String;");
			tree.one(Opcodes.DUP2);
			tree.invokeV("roj/mildwind/type/JsObject", "get", "(Ljava/lang/String;)Lroj/mildwind/type/JsObject;");
			return false;
		}
	}
	private static void checkSetKey(JsMethodWriter tree, boolean intKey) {
		if (intKey) tree.invokeV("roj/mildwind/type/JsObject", "putByInt", "(ILroj/mildwind/type/JsObject;)V");
		else tree.invokeV("roj/mildwind/type/JsObject", "put", "(Ljava/lang/String;Lroj/mildwind/type/JsObject;)V");
	}

	public Expression compress() {
		left = (LoadExpression) left.compress();
		right = right.compress();
		return this;
	}

	@Override
	public JsObject compute(JsContext ctx) {
		JsObject v = right.compute(ctx);
		left.computeAssign(ctx, v);
		return v;
	}

	@Override
	public boolean isEqual(Expression left) {
		if (this == left) return true;
		if (!(left instanceof Assign)) return false;
		Assign assign = (Assign) left;
		return assign.left.isEqual(left) && assign.right.isEqual(right);
	}

	@Override
	public String toString() { return left+" = "+right; }
}