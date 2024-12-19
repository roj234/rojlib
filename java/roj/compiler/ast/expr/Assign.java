package roj.compiler.ast.expr;

import roj.asm.Opcodes;
import roj.asm.tree.anno.AnnValInt;
import roj.asm.type.IType;
import roj.compiler.JavaLexer;
import roj.compiler.asm.MethodWriter;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.TypeCast;

import static roj.compiler.JavaLexer.byId;

/**
 * @author Roj234
 * @since 2023/9/18 0018 9:07
 */
class Assign extends ExprNode {
	VarNode left;
	ExprNode right;
	TypeCast.Cast cast;

	Assign(VarNode left, ExprNode right) {
		this.left = left;
		this.right = right;
	}

	@Override
	public String toString() { return left+" = "+right; }

	@Override
	public IType type() { return left.type(); }

	@Override
	public ExprNode resolve(LocalContext ctx) {
		ExprNode node = left.resolve(ctx);
		if (node instanceof VarNode vn && !vn.isFinal()) left = vn;
		else {
			ctx.report(Kind.ERROR, "assign.error.final", left);
			return NaE.RESOLVE_FAILED;
		}

		ExprNode prev = right;
		if (prev instanceof Cast c) {
			int castType = c.cast.type;
			if ((castType == -1 || castType == -2) && !(left instanceof LocalVariable))
				ctx.report(Kind.INCOMPATIBLE, "assign.incompatible.redundantCast");
		}

		if (prev instanceof Binary op) {
			right = op.resolveEx(ctx, true);
			if (right == NaE.RESOLVE_FAILED) return right;

			if (node.equals(op.left)) {
				// a = a + b
				// check binary_assign override
				var override = ctx.getOperatorOverride(node, op.right, op.operator - JavaLexer.binary_assign_delta);
				if (override != null) return override;
			}

			if (right == null) {
				ctx.report(Kind.ERROR, "binary.error.notApplicable", op.left.type(), op.right.type(), byId(op.operator));
				return NaE.RESOLVE_FAILED;
			}
		} else {
			right = prev.resolve(ctx);
		}

		// 常量传播
		if (right.isConstant() && left instanceof LocalVariable lv) {
			ctx.assignVar(lv.v, right.constVal());
		}

		IType lType = left.type();
		cast = ctx.castTo(right.type(), lType, TypeCast.getDataCap(lType.getActualType()) < 4 ? TypeCast.E_NUMBER_DOWNCAST : 0);

		return cast.canCast() ? this : NaE.RESOLVE_FAILED;
	}

	static void incOrDec(VarNode expr, MethodWriter cw, boolean noRet, boolean returnBefore, int amount) {
		boolean isLv = false;
		int dtype = TypeCast.getDataCap(expr.type().getActualType());
		// == 4 (int) 防止byte自增导致溢出什么的...
		if (expr instanceof LocalVariable lv) {
			isLv = true;
			lv.v.endPos = expr.wordEnd;

			var ctx = LocalContext.get();
			ctx.loadVar(lv.v);
			ctx.storeVar(lv.v);

			if (dtype == 4 && (short)amount == amount) {
				if (!noRet & returnBefore) cw.load(lv.v);
				cw.iinc(lv.v, amount);
				if (!noRet & !returnBefore) cw.load(lv.v);
				return;
			}
		}

		expr.preLoadStore(cw);

		int op;
		if (amount < 0 && amount != Integer.MIN_VALUE) {
			amount = -amount;
			op = Opcodes.ISUB-4;
		} else {
			op = Opcodes.IADD-4;
		}

		int type2 = Math.max(4, dtype);
		switch (type2) {
			case 4: cw.ldc(amount); break;
			case 5: cw.ldc((long)amount); break;
			case 6: cw.ldc((float)amount); break;
			case 7: cw.ldc((double)amount); break;
		}
		cw.one((byte) (op + type2));
		if (dtype < 4 && isLv) cw.one((byte) (Opcodes.I2B-1 + dtype));

		expr.postStore(cw, 0);
	}

	@Override
	@SuppressWarnings("fallthrough")
	public void write(MethodWriter cw, boolean noRet) {
		// a = a + 1
		if (right instanceof Binary br) {
			if (br.left.equals(left)) {
				if (sameVarShrink(cw, br, noRet, br.right)) return;
			} else if (br.left.isConstant() && br.right.equals(left)) {
				if (sameVarShrink(cw, br, noRet, br.left)) return;
			}
		} else {
			left.preStore(cw);
			right.write(cw);
		}

		if (isCastNeeded()) cast.write(cw);

		int state = noRet ? 0 : left.copyValue(cw, left.type().rawType().length() - 1 != 0);
		left.postStore(cw, state);
	}

	private boolean isCastNeeded() { return (cast.type != -1 && cast.type != -2) || left.getClass() == LocalVariable.class; }

	private boolean sameVarShrink(MethodWriter cw, Binary br, boolean noRet, ExprNode operand) {
		// to IINC if applicable
		block:
		if (left instanceof LocalVariable lv && TypeCast.getDataCap(br.type().getActualType()) == 4 && operand.isConstant()) {
			int value = ((AnnValInt) operand.constVal()).value;

			switch (br.operator) {
				default: break block;
				case JavaLexer.add: break;
				case JavaLexer.sub: value = -value; break;
			}
			if ((short)value != value) break block;

			var ctx = LocalContext.get();
			ctx.loadVar(lv.v);
			ctx.storeVar(lv.v);

			cw.iinc(lv.v, value);
			if (!noRet) left.write(cw);
			return true;
		}

		left.preLoadStore(cw);
		if (operand == br.left) br.writeLeft(cw);
		else br.writeRight(cw);
		br.writeOperator(cw);

		return false;
	}

	@Override
	public final boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Assign assign) || o.getClass() != getClass()) return false;
		return assign.left.equals(left) && assign.right.equals(right);
	}

	@Override
	public final int hashCode() {
		int result = left.hashCode();
		result = 31 * result + right.hashCode();
		result = 31 * result + getClass().hashCode();
		return result;
	}
}